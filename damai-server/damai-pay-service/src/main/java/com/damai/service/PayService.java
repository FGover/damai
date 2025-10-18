package com.damai.service;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.dto.NotifyDto;
import com.damai.dto.PayBillDto;
import com.damai.dto.PayDto;
import com.damai.dto.RefundDto;
import com.damai.dto.TradeCheckDto;
import com.damai.entity.PayBill;
import com.damai.entity.RefundBill;
import com.damai.enums.BaseCode;
import com.damai.enums.PayBillStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.PayBillMapper;
import com.damai.mapper.RefundBillMapper;
import com.damai.pay.PayResult;
import com.damai.pay.PayStrategyContext;
import com.damai.pay.PayStrategyHandler;
import com.damai.pay.RefundResult;
import com.damai.pay.TradeResult;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.DateUtils;
import com.damai.vo.NotifyVo;
import com.damai.vo.PayBillVo;
import com.damai.vo.TradeCheckVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import static com.damai.constant.Constant.ALIPAY_NOTIFY_FAILURE_RESULT;
import static com.damai.constant.Constant.ALIPAY_NOTIFY_SUCCESS_RESULT;
import static com.damai.core.DistributedLockConstants.COMMON_PAY;
import static com.damai.core.DistributedLockConstants.TRADE_CHECK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 支付 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class PayService {

    @Autowired
    private PayBillMapper payBillMapper;

    @Autowired
    private RefundBillMapper refundBillMapper;

    @Autowired
    private PayStrategyContext payStrategyContext;

    @Autowired
    private UidGenerator uidGenerator;

    /**
     * 通用支付处理方法
     * 同一处理不同支付渠道的支付请求，通过订单号加锁防止重复支付，不依赖第三方支付的幂等性保障
     *
     * @param payDto 支付参数对象
     * @return 支付结果
     */
    @ServiceLock(name = COMMON_PAY, keys = {"#payDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public String commonPay(PayDto payDto) {
        // 根据订单号查询支付账单是否已有支付记录
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, payDto.getOrderNumber());
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        // 如果支付账单存在，并且支付状态不为“未支付”，则抛出异常
        if (Objects.nonNull(payBill) && !Objects.equals(payBill.getPayBillStatus(), PayBillStatus.NO_PAY.getCode())) {
            throw new DaMaiFrameException(BaseCode.PAY_BILL_IS_NOT_NO_PAY);
        }
        // 根据支付渠道获取对应的支付策略处理器
        // 策略模式：通过payStrategyContext获取渠道对应的实现类，适配不同支付渠道的差异
        PayStrategyHandler payStrategyHandler = payStrategyContext.get(payDto.getChannel());
        // 调用具体支付渠道的支付方法，发起支付请求
        PayResult pay = payStrategyHandler.pay(
                String.valueOf(payDto.getOrderNumber()),   // 订单号
                payDto.getPrice(),      // 订单金额
                payDto.getSubject(),    // 订单标题
                payDto.getNotifyUrl(),  //支付结果异步通知地址
                payDto.getReturnUrl()   // 支付完成后跳转地址
        );
        // 如果支付请求成功，则将支付账单信息插入数据库
        if (pay.isSuccess()) {
            payBill = new PayBill();  // 新建支付账单对象
            payBill.setId(uidGenerator.getUid());   // 生成支付账单唯一ID
            payBill.setOutOrderNo(String.valueOf(payDto.getOrderNumber()));  // 设置订单号
            payBill.setPayChannel(payDto.getChannel());   // 设置支付渠道
            payBill.setPayScene("生产");    // 设置支付场景（此处为固定值“生产”）
            payBill.setSubject(payDto.getSubject());  // 设置订单标题
            payBill.setPayAmount(payDto.getPrice());   // 设置订单金额
            payBill.setPayBillType(payDto.getPayBillType());  // 设置账单类型
            payBill.setPayBillStatus(PayBillStatus.NO_PAY.getCode());  // 设置账单状态为“未支付”
            payBill.setPayTime(DateUtils.now());  // 设置支付时间
            payBillMapper.insert(payBill);  // 插入支付账单信息到数据库
        }
        // 返回支付结果
        return pay.getBody();
    }

    /**
     * 处理支付渠道回调通知的核心业务逻辑
     * 负责验证通知的合法性，并更新支付单状态（属于支付结果确认的关键步骤）
     *
     * @param notifyDto 包含支付渠道标识和回调参数的DTO对象
     * @return NotifyVo 封装处理结果（支付单编号和处理状态），用于告知支付渠道是否处理成功
     */
    @Transactional(rollbackFor = Exception.class)
    public NotifyVo notify(NotifyDto notifyDto) {
        NotifyVo notifyVo = new NotifyVo();
        log.info("回调通知参数 ===> {}", JSON.toJSONString(notifyDto));
        // 提取支付渠道传递的具体参数（如订单号、支付金额、签名等）
        Map<String, String> params = notifyDto.getParams();
        // 根据支付渠道获取对应的策略处理器
        PayStrategyHandler payStrategyHandler = payStrategyContext.get(notifyDto.getChannel());
        // 验证回调参数的签名（防止伪造请求，确保通知来自合法的支付渠道）
        boolean signVerifyResult = payStrategyHandler.signVerify(params);
        // 签名验证失败
        if (!signVerifyResult) {
            // 设置结果为失败（通常为"failure"）
            notifyVo.setPayResult(ALIPAY_NOTIFY_FAILURE_RESULT);
            // 直接返回失败结果，支付渠道会重试通知
            return notifyVo;
        }
        // 根据回调参数中的订单号（out_trade_no）查询系统中的支付账单记录
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, params.get("out_trade_no"));
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        // 账单是否存在，不存在则返回失败
        if (Objects.isNull(payBill)) {
            log.error("账单为空 notifyDto : {}", JSON.toJSONString(notifyDto));
            notifyVo.setPayResult(ALIPAY_NOTIFY_FAILURE_RESULT);
            return notifyVo;
        }
        // 处理支付账单已处于终态的场景（避免重复处理）
        // 如果支付账单已支付，直接返回成功（防止重复更新）
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.PAY.getCode())) {
            log.info("账单已支付 notifyDto : {}", JSON.toJSONString(notifyDto));
            notifyVo.setOutTradeNo(payBill.getOutOrderNo());
            notifyVo.setPayResult(ALIPAY_NOTIFY_SUCCESS_RESULT);
            return notifyVo;
        }
        // 如果支付账单已取消，直接返回成功
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.CANCEL.getCode())) {
            log.info("账单已取消 notifyDto : {}", JSON.toJSONString(notifyDto));
            notifyVo.setOutTradeNo(payBill.getOutOrderNo());
            notifyVo.setPayResult(ALIPAY_NOTIFY_SUCCESS_RESULT);
            return notifyVo;
        }
        // 如果支付账单已退款，直接返回成功
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.REFUND.getCode())) {
            log.info("账单已退款 notifyDto : {}", JSON.toJSONString(notifyDto));
            notifyVo.setOutTradeNo(payBill.getOutOrderNo());
            notifyVo.setPayResult(ALIPAY_NOTIFY_SUCCESS_RESULT);
            return notifyVo;
        }
        // 验证回调数据的业务合法性（如金额匹配、商户信息一致等）
        boolean dataVerify = payStrategyHandler.dataVerify(notifyDto.getParams(), payBill);
        // 数据验证失败
        if (!dataVerify) {
            // 设置结果为失败（通常为"failure"）
            notifyVo.setPayResult(ALIPAY_NOTIFY_FAILURE_RESULT);
            return notifyVo;
        }
        // 验证通过，更新支付账单状态为“已支付”
        PayBill updatePayBill = new PayBill();
        updatePayBill.setPayBillStatus(PayBillStatus.PAY.getCode());
        // 根据订单号更新数据库
        LambdaUpdateWrapper<PayBill> payBillLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(PayBill.class).eq(PayBill::getOutOrderNo, params.get("out_trade_no"));
        payBillMapper.update(updatePayBill, payBillLambdaUpdateWrapper);
        // 封装成功结果并返回
        notifyVo.setOutTradeNo(payBill.getOutOrderNo());  // 携带订单号返回
        notifyVo.setPayResult(ALIPAY_NOTIFY_SUCCESS_RESULT);   // 告知支付渠道处理成功
        return notifyVo;
    }

    /**
     * 校验订单在支付渠道中的实际交易状态，并同步更新系统内支付账单状态
     * 作为主动查询支付状态的核心方法，确保系统内支付账单状态与第三方支付平台一致
     *
     * @param tradeCheckDto 交易查询参数
     * @return TradeCheckVo 交易状态查询结果
     */
    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(name = TRADE_CHECK, keys = {"#tradeCheckDto.outTradeNo"})
    public TradeCheckVo tradeCheck(TradeCheckDto tradeCheckDto) {
        // 初始化交易状态查询结果对象
        TradeCheckVo tradeCheckVo = new TradeCheckVo();
        // 根据支付渠道获取对应的策略处理器
        PayStrategyHandler payStrategyHandler = payStrategyContext.get(tradeCheckDto.getChannel());
        // 调用支付渠道的订单查询接口，获取第三方平台的真实交易状态
        TradeResult tradeResult = payStrategyHandler.queryTrade(tradeCheckDto.getOutTradeNo());
        // 将第三方返回的交易结果复制到返回对象中（基础信息透传）
        BeanUtil.copyProperties(tradeResult, tradeCheckVo);
        // 若第三方查询失败（如接口异常、订单不存在），直接返回结果
        if (!tradeResult.isSuccess()) {
            return tradeCheckVo;
        }
        // 提取第三方返回的关键信息
        BigDecimal totalAmount = tradeResult.getTotalAmount();  // 第三方记录的支付金额
        String outTradeNo = tradeResult.getOutTradeNo();   // 订单号
        Integer payBillStatus = tradeResult.getPayBillStatus();   // 第三方记录的支付状态（如"已支付"）
        // 根据订单号查询系统内对应的支付单记录
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, outTradeNo);
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        // 账单是否存在，不存在则返回失败
        if (Objects.isNull(payBill)) {
            log.error("账单为空 tradeCheckDto : {}", JSON.toJSONString(tradeCheckDto));
            return tradeCheckVo;
        }
        // 校验第三方支付金额与系统内支付单金额是否一致（防篡改/数据异常）
        if (payBill.getPayAmount().compareTo(totalAmount) != 0) {
            log.error("支付渠道 和库中账单支付金额不一致 支付渠道支付金额 : {}, 库中账单支付金额 : {}, tradeCheckDto : {}",
                    totalAmount, payBill.getPayAmount(), JSON.toJSONString(tradeCheckDto));
            return tradeCheckVo;   // 金额不一致，不更新状态，直接返回
        }
        // 若第三方支付状态与系统内支付单状态不一致，同步更新系统状态
        if (!Objects.equals(payBill.getPayBillStatus(), payBillStatus)) {
            log.warn("支付渠道和库中账单交易状态不一致 支付渠道payBillStatus : {}, 库中payBillStatus : {}, tradeCheckDto : {}",
                    payBillStatus, payBill.getPayBillStatus(), JSON.toJSONString(tradeCheckDto));
            // 构建更新对象，设置目标状态
            PayBill updatePayBill = new PayBill();
            updatePayBill.setId(payBill.getId());  // 支付单ID（用于定位记录）
            updatePayBill.setPayBillStatus(payBillStatus);  // 同步为第三方返回的状态
            // 执行更新操作（按订单号条件）
            LambdaUpdateWrapper<PayBill> payBillLambdaUpdateWrapper =
                    Wrappers.lambdaUpdate(PayBill.class).eq(PayBill::getOutOrderNo, outTradeNo);
            payBillMapper.update(updatePayBill, payBillLambdaUpdateWrapper);
        }
        // 返回包含最新状态的查询结果
        return tradeCheckVo;
    }

    /**
     * 处理订单退款请求的核心方法
     * 负责校验退款条件、调用支付渠道的退款接口，并记录退款单信息
     *
     * @param refundDto 退款请求参数
     * @return String 退款单关联的订单号
     */
    public String refund(RefundDto refundDto) {
        // 根据订单号查询对应的支付账单记录
        PayBill payBill = payBillMapper.selectOne(Wrappers.lambdaQuery(PayBill.class)
                .eq(PayBill::getOutOrderNo, refundDto.getOrderNumber()));
        // 如果支付账单不存在，抛出异常
        if (Objects.isNull(payBill)) {
            throw new DaMaiFrameException(BaseCode.PAY_BILL_NOT_EXIST);
        }
        // 校验支付单状态是否为"已支付"（只有已支付的订单才能发起退款）
        if (!Objects.equals(payBill.getPayBillStatus(), PayBillStatus.PAY.getCode())) {
            throw new DaMaiFrameException(BaseCode.PAY_BILL_IS_NOT_PAY_STATUS);
        }
        // 校验退款金额是否超过支付金额（退款金额不能大于原支付金额）
        if (refundDto.getAmount().compareTo(payBill.getPayAmount()) > 0) {
            throw new DaMaiFrameException(BaseCode.REFUND_AMOUNT_GREATER_THAN_PAY_AMOUNT);
        }
        // 根据支付渠道（如"alipay"）获取对应的支付策略处理器
        PayStrategyHandler payStrategyHandler = payStrategyContext.get(refundDto.getChannel());
        // 调用支付渠道的退款接口（如支付宝的退款API）
        RefundResult refundResult = payStrategyHandler.refund(
                refundDto.getOrderNumber(),  // 订单号
                refundDto.getAmount(),   // 退款金额
                refundDto.getReason()   // 退款原因
        );
        // 处理退款结果
        if (refundResult.isSuccess()) {
            // 如果退款成功，创建退款单记录，保存退款信息
            RefundBill refundBill = new RefundBill();
            refundBill.setId(uidGenerator.getUid());  // 生成唯一ID
            refundBill.setOutOrderNo(payBill.getOutOrderNo());  // 关联的订单号
            refundBill.setPayBillId(payBill.getId());   // 关联的支付单ID
            refundBill.setRefundAmount(refundDto.getAmount());   // 退款金额
            refundBill.setRefundStatus(1);   // 退款状态
            refundBill.setRefundTime(DateUtils.now());   // 退款时间
            refundBill.setReason(refundDto.getReason());   // 退款原因
            // 将退款单插入数据库
            refundBillMapper.insert(refundBill);
            // 返回订单号，标识退款成功
            return refundBill.getOutOrderNo();
        } else {
            throw new DaMaiFrameException(refundResult.getMessage());
        }
    }

    /**
     * 根据订单号查询支付账单详情
     *
     * @param payBillDto 支付单查询DTO
     * @return 支付单视图对象
     */
    public PayBillVo detail(PayBillDto payBillDto) {
        PayBillVo payBillVo = new PayBillVo();
        // 根据订单号查询支付账单详情
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, payBillDto.getOrderNumber());
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.nonNull(payBill)) {
            BeanUtil.copyProperties(payBill, payBillVo);
        }
        return payBillVo;
    }
}
