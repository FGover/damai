package com.damai.pay.alipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConstants;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.internal.util.WebUtils;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.damai.entity.PayBill;
import com.damai.enums.AlipayTradeStatus;
import com.damai.enums.BaseCode;
import com.damai.enums.PayBillStatus;
import com.damai.enums.PayChannel;
import com.damai.exception.DaMaiFrameException;
import com.damai.pay.PayResult;
import com.damai.pay.PayStrategyHandler;
import com.damai.pay.RefundResult;
import com.damai.pay.TradeResult;
import com.damai.pay.alipay.config.AlipayProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 支付宝支付策略处理器，实现支付宝的支付、签名验证、订单查询、退款等功能
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
public class AlipayStrategyHandler implements PayStrategyHandler {

    /**
     * 支付宝的SDK客户端，用于调用支付宝开放平台接口
     */
    private final AlipayClient alipayClient;

    /**
     * 支付宝相关配置类，存储appId、私钥、公钥等配置信息
     */
    private final AlipayProperties aliPayProperties;

    /**
     * 发起支付宝电脑网站支付（生成支付链接/表单）
     *
     * @param outTradeNo 订单号
     * @param price      支付价格
     * @param subject    标题
     * @param notifyUrl  回调地址
     * @param returnUrl  支付后返回地址
     * @return 支付结果对象
     */
    @Override
    public PayResult pay(String outTradeNo, BigDecimal price, String subject, String notifyUrl, String returnUrl) {
        try {
            // 创建支付宝网页支付请求对象
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            // 设置异步通知地址，仅支持http/https，公网可访问（关键：支付成功后支付宝会回调此地址更新订单状态）
            request.setNotifyUrl(notifyUrl);
            // 设置同步跳转地址，仅支持http/https（用户支付成功后页面跳转的地址，仅用于前端展示）
            request.setReturnUrl(returnUrl);
            // 构建请求参数（支付宝接口要求的业务参数）
            JSONObject bizContent = new JSONObject();
            // 订单号
            bizContent.put("out_trade_no", outTradeNo);
            // 支付金额，最小值0.01元
            bizContent.put("total_amount", price);
            // 订单标题，不可使用特殊符号
            bizContent.put("subject", subject);
            // 电脑网站支付场景固定传值FAST_INSTANT_TRADE_PAY
            bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
            // 将业务参数设置到请求中
            request.setBizContent(bizContent.toString());
            // 调用支付宝SDK执行请求，获取支付表单（HTML格式，用于前端展示支付页面）
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request, "POST");
            // 返回支付结果对象，包含支付链接/表单
            return new PayResult(response.isSuccess(), response.getBody());
        } catch (Exception e) {
            log.error("alipay pay error", e);
            throw new DaMaiFrameException(BaseCode.PAY_ERROR);
        }
    }

    /**
     * 验证支付宝回调参数的签名（防止回调被篡改）
     *
     * @param params 支付宝回调的参数Map
     * @return boolean 签名是否验证通过
     */
    @Override
    public boolean signVerify(final Map<String, String> params) {
        try {
            // 调用支付宝SDK验证签名：使用支付宝公钥、UTF-8编码、RSA2签名算法
            return AlipaySignature.rsaCheckV1(
                    params,
                    aliPayProperties.getAlipayPublicKey(),  // 支付宝公钥（用于验证签名）
                    AlipayConstants.CHARSET_UTF8,     // 编码格式
                    //调用SDK验证签名
                    AlipayConstants.SIGN_TYPE_RSA2   // 签名算法（支付宝推荐RSA2）
            );
        } catch (Exception e) {
            log.error("alipay sign verify error", e);
            return false;
        }

    }

    /**
     * 验证支付宝回调数据的合法性（除签名外的业务校验）
     *
     * @param params  支付回调的参数Map
     * @param payBill 系统中支付账单记录
     * @return boolean 数据是否合法
     */
    @Override
    public boolean dataVerify(final Map<String, String> params, PayBill payBill) {
        // 校验回调金额与系统记录的支付金额是否一致（防止篡改）
        BigDecimal notifyPayAmount = new BigDecimal(params.get("total_amount"));
        BigDecimal payAmount = payBill.getPayAmount();
        if (notifyPayAmount.compareTo(payAmount) != 0) {
            log.error("回调金额和账单支付金额不一致 回调金额 : {}, 账单支付金额 : {}", notifyPayAmount, payAmount);
            return false;
        }
        // 校验回调的商户ID与系统配置的商户ID是否一致
        String notifySellerId = params.get("seller_id");
        String alipaySellerId = aliPayProperties.getSellerId();
        if (!notifySellerId.equals(alipaySellerId)) {
            log.error("回调商户pid和已配置商户pid不一致 回调商户pid : {}, 已配置商户pid : {}", notifySellerId, alipaySellerId);
            return false;
        }
        //　校验回调的appId与系统配置的appId是否一致
        String notifyAppId = params.get("app_id");
        String alipayAppId = aliPayProperties.getAppId();
        if (!notifyAppId.equals(alipayAppId)) {
            log.error("回调appId和已配置appId不一致 回调appId : {}, 已配置appId : {}", notifyAppId, alipayAppId);
            return false;
        }
        // 校验支付状态是否为“交易成功”
        String tradeStatus = params.get("trade_status");
        if (!AlipayTradeStatus.TRADE_SUCCESS.getValue().equals(tradeStatus)) {
            log.error("支付未成功 tradeStatus : {}", tradeStatus);
            return false;
        }
        // 所有校验通过
        return true;
    }

    /**
     * 查询支付宝订单状态（用于主动查询支付结果，补充异步通知的可靠性）
     *
     * @param outTradeNo 订单号
     * @return TradeResult 订单查询结果，包含支付状态、金额等信息
     */
    @Override
    public TradeResult queryTrade(String outTradeNo) {
        String successCode = "10000";  // 支付宝接口成功返回码
        String successMsg = "Success";  // 支付宝接口成功返回信息
        TradeResult tradeResult = new TradeResult();
        tradeResult.setSuccess(false);   // 初始设为失败
        try {
            // 创建支付宝订单查询请求对象
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            // 跳过SSL证书校验（生产环境需开启）
            WebUtils.setNeedCheckServerTrusted(false);
            // 构建查询参数
            JSONObject bizContent = new JSONObject();
            // 订单号
            bizContent.put("out_trade_no", outTradeNo);
            request.setBizContent(bizContent.toString());
            // 调用支付宝SDK执行请求，获取订单查询结果
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                // 解析查询结果
                JSONObject jsonResponse = JSON.parseObject(response.getBody());
                JSONObject alipayTradeQueryResponse = jsonResponse.getJSONObject("alipay_trade_query_response");
                String code = alipayTradeQueryResponse.getString("code");
                String msg = alipayTradeQueryResponse.getString("msg");
                // 若查询成功，设置订单信息
                if (successCode.equals(code) && successMsg.equals(msg)) {
                    tradeResult.setSuccess(true);
                    // 订单号
                    tradeResult.setOutTradeNo(alipayTradeQueryResponse.getString("out_trade_no"));
                    // 支付金额
                    tradeResult.setTotalAmount(new BigDecimal(alipayTradeQueryResponse.getString("total_amount")));
                    // 账单状态，将支付宝的状态转换为系统内部的支付账单状态
                    tradeResult.setPayBillStatus(convertPayBillStatus(alipayTradeQueryResponse.getString("trade_status")));
                    return tradeResult;
                }
            } else {
                log.error("支付宝交易查询结果失败 response : {}", JSON.toJSONString(response));
            }
        } catch (Exception e) {
            log.error("alipay trade query error", e);
        }
        return tradeResult;
    }

    /**
     * 发起支付宝退款
     *
     * @param outTradeNo 订单号
     * @param price      支付价格
     * @param reason     原因
     * @return RefundResult 退款结果
     */
    @Override
    public RefundResult refund(String outTradeNo, BigDecimal price, String reason) {
        // 创建支付宝退款请求对象
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        // 构建退款参数
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);  // 订单号
        bizContent.put("refund_amount", price);   // 退款金额
        bizContent.put("refund_reason", reason);  // 退款原因
        request.setBizContent(bizContent.toString());
        try {
            // 调用支付宝SDK执行退款请求
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            // 返回退款结果：是否成功 + 退款详情 + 消息
            return new RefundResult(response.isSuccess(), response.getBody(), response.getMsg());
        } catch (AlipayApiException e) {
            log.error("alipay refund error", e);
            throw new DaMaiFrameException(BaseCode.REFUND_ERROR);
        }
    }

    /**
     * 获取当前支付渠道标识
     *
     * @return
     */
    @Override
    public String getChannel() {
        return PayChannel.ALIPAY.getValue();
    }

    /**
     * 将支付宝的交易状态转换为系统内部的支付账单状态
     *
     * @param tradeStatus 支付宝返回的交易状态（如WAIT_BUYER_PAY、TRADE_SUCCESS）
     * @return Integer 系统内部的支付单状态码（如未支付、已支付、已取消）
     */
    private Integer convertPayBillStatus(String tradeStatus) {
        if (AlipayTradeStatus.WAIT_BUYER_PAY.getValue().equals(tradeStatus)) {
            // 等待买家付款 → 未支付
            return PayBillStatus.NO_PAY.getCode();
        } else if (AlipayTradeStatus.TRADE_CLOSED.getValue().equals(tradeStatus)) {
            // 交易关闭 → 已取消
            return PayBillStatus.CANCEL.getCode();
        } else if (AlipayTradeStatus.TRADE_SUCCESS.getValue().equals(tradeStatus) ||
                AlipayTradeStatus.TRADE_FINISHED.getValue().equals(tradeStatus)) {
            // 交易成功/交易结束 → 已支付
            return PayBillStatus.PAY.getCode();
        }
        throw new DaMaiFrameException(BaseCode.ALIPAY_TRADE_STATUS_NOT_EXIST);
    }
}
