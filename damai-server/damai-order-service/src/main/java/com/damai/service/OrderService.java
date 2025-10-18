package com.damai.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.client.PayClient;
import com.damai.client.UserClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.dto.AccountOrderCountDto;
import com.damai.dto.NotifyDto;
import com.damai.dto.OrderCancelDto;
import com.damai.dto.OrderCreateDto;
import com.damai.dto.OrderGetDto;
import com.damai.dto.OrderListDto;
import com.damai.dto.OrderPayCheckDto;
import com.damai.dto.OrderPayDto;
import com.damai.dto.OrderTicketUserCreateDto;
import com.damai.dto.PayDto;
import com.damai.dto.ProgramOperateDataDto;
import com.damai.dto.RefundDto;
import com.damai.dto.TicketCategoryCountDto;
import com.damai.dto.TradeCheckDto;
import com.damai.dto.UserGetAndTicketUserListDto;
import com.damai.entity.Order;
import com.damai.entity.OrderTicketUser;
import com.damai.entity.OrderTicketUserAggregate;
import com.damai.enums.BaseCode;
import com.damai.enums.BusinessStatus;
import com.damai.enums.OrderStatus;
import com.damai.enums.PayBillStatus;
import com.damai.enums.PayChannel;
import com.damai.enums.SellStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.OrderMapper;
import com.damai.mapper.OrderTicketUserMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.damai.request.CustomizeRequestWrapper;
import com.damai.service.delaysend.DelayOperateProgramDataSend;
import com.damai.service.properties.OrderProperties;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import com.damai.util.StringUtil;
import com.damai.vo.AccountOrderCountVo;
import com.damai.vo.NotifyVo;
import com.damai.vo.OrderGetVo;
import com.damai.vo.OrderListVo;
import com.damai.vo.OrderPayCheckVo;
import com.damai.vo.OrderTicketInfoVo;
import com.damai.vo.SeatVo;
import com.damai.vo.TicketUserInfoVo;
import com.damai.vo.TicketUserVo;
import com.damai.vo.TradeCheckVo;
import com.damai.vo.UserAndTicketUserInfoVo;
import com.damai.vo.UserGetAndTicketUserListVo;
import com.damai.vo.UserInfoVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.constant.Constant.ALIPAY_NOTIFY_SUCCESS_RESULT;
import static com.damai.core.DistributedLockConstants.ORDER_CANCEL_LOCK;
import static com.damai.core.DistributedLockConstants.ORDER_PAY_CHECK;
import static com.damai.core.DistributedLockConstants.ORDER_PAY_NOTIFY_CHECK;
import static com.damai.core.RepeatExecuteLimitConstants.CANCEL_PROGRAM_ORDER;
import static com.damai.core.RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER_MQ;
import static com.damai.core.RepeatExecuteLimitConstants.PROGRAM_CACHE_REVERSE_MQ;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 订单 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class OrderService extends ServiceImpl<OrderMapper, Order> {

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderTicketUserMapper orderTicketUserMapper;

    @Autowired
    private OrderTicketUserService orderTicketUserService;

    @Autowired
    private OrderProgramCacheResolutionOperate orderProgramCacheResolutionOperate;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private PayClient payClient;

    @Autowired
    private UserClient userClient;

    @Autowired
    private OrderProperties orderProperties;

    @Lazy
    @Autowired
    private OrderService orderService;

    @Autowired
    private DelayOperateProgramDataSend delayOperateProgramDataSend;

    @Autowired
    private ServiceLockTool serviceLockTool;

    /**
     * 订单创建核心方法
     *
     * @param orderCreateDto
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(OrderCreateDto orderCreateDto) {
        // 1.构建订单查询条件
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderCreateDto.getOrderNumber());
        // 查询订单是否存在，防止重复提交订单
        Order oldOrder = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.nonNull(oldOrder)) {
            // 订单已存在，抛异常终止流程
            throw new DaMaiFrameException(BaseCode.ORDER_EXIST);
        }
        // 2.构建主订单实体
        Order order = new Order();
        BeanUtil.copyProperties(orderCreateDto, order);
        // 设置订单特有属性
        order.setDistributionMode("电子票");  // 配送方式：电子票（无需实体票邮寄）
        order.setTakeTicketMode("请使用购票人身份证直接入场");  // 取票/入场说明
        // 3.构建子订单（订单--票用户关联表）
        List<OrderTicketUser> orderTicketUserList = new ArrayList<>();
        for (OrderTicketUserCreateDto orderTicketUserCreateDto : orderCreateDto.getOrderTicketUserCreateDtoList()) {
            OrderTicketUser orderTicketUser = new OrderTicketUser();
            BeanUtil.copyProperties(orderTicketUserCreateDto, orderTicketUser); // 拷贝子订单属性
            orderTicketUser.setId(uidGenerator.getUid());  // 生成子订单唯一ID
            orderTicketUserList.add(orderTicketUser);
        }
        // 4.执行数据库插入操作
        orderMapper.insert(order);
        // 批量插入子订单
        orderTicketUserService.saveBatch(orderTicketUserList);
        // 5.更新用户订单计数缓存（用于统计用户对某个节目购买的票数）
        redisCache.incrBy(RedisKeyBuild.createRedisKey(
                        RedisKeyManage.ACCOUNT_ORDER_COUNT,
                        orderCreateDto.getUserId(),
                        orderCreateDto.getProgramId()),
                orderCreateDto.getOrderTicketUserCreateDtoList().size());  // 本次购买的票数（子订单数量）
        // 返回主订单号
        return String.valueOf(order.getOrderNumber());
    }

    /**
     * 订单取消，以订单编号加锁
     */
    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER, keys = {"#orderCancelDto.orderNumber"})
    @ServiceLock(name = ORDER_CANCEL_LOCK, keys = {"#orderCancelDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(OrderCancelDto orderCancelDto) {
        // 更新订单及关联数据（状态、库存、缓存等）
        updateOrderRelatedData(orderCancelDto.getOrderNumber(), OrderStatus.CANCEL);
        return true;
    }

    /**
     * 处理订单支付请求的核心方法
     *
     * @param orderPayDto 支付请求参数DTO
     * @return 支付相关数据
     */
    public String pay(OrderPayDto orderPayDto) {
        // 获取订单号
        Long orderNumber = orderPayDto.getOrderNumber();
        // 根据订单编号查询数据库中的订单记录
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderNumber);
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        // 订单不存在
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        // 订单已取消
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_CANCEL);
        }
        // 订单已支付
        if (Objects.equals(order.getOrderStatus(), OrderStatus.PAY.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_PAY);
        }
        // 订单已退款
        if (Objects.equals(order.getOrderStatus(), OrderStatus.REFUND.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_REFUND);
        }
        // 支付金额校验（防止金额篡改，确保支付金额与订单金额一致）
        if (orderPayDto.getPrice().compareTo(order.getOrderPrice()) != 0) {
            throw new DaMaiFrameException(BaseCode.PAY_PRICE_NOT_EQUAL_ORDER_PRICE);
        }
        // 构建支付客户端所需的参数对象
        PayDto payDto = getPayDto(orderPayDto, orderNumber);
        // 远程调用支付客户端接口完成支付
        ApiResponse<String> payResponse = payClient.commonPay(payDto);
        // 调用失败
        if (!Objects.equals(payResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(payResponse);
        }
        // 返回支付成功的相关数据
        return payResponse.getData();
    }

    /**
     * 构建支付客户端所需参数对象
     *
     * @param orderPayDto
     * @param orderNumber
     * @return
     */
    private PayDto getPayDto(OrderPayDto orderPayDto, Long orderNumber) {
        // 创建支付客户端参数对象
        PayDto payDto = new PayDto();
        // 设置订单号（转为字符串）
        payDto.setOrderNumber(String.valueOf(orderNumber));
        // 设置支付种类
        payDto.setPayBillType(orderPayDto.getPayBillType());
        // 设置订单主题
        payDto.setSubject(orderPayDto.getSubject());
        // 设置支付渠道
        payDto.setChannel(orderPayDto.getChannel());
        // 设置支付平台
        payDto.setPlatform(orderPayDto.getPlatform());
        // 设置支付金额
        payDto.setPrice(orderPayDto.getPrice());
        // 设置支付成功后通知接口地址
        payDto.setNotifyUrl(orderProperties.getOrderPayNotifyUrl());
        // 设置支付成功后跳转页面
        payDto.setReturnUrl(orderProperties.getOrderPayReturnUrl());
        return payDto;
    }

    /**
     * 支付后订单状态检查
     * 用于前端主动查询订单的支付状态，作为支付宝异步通知的补充机制，确保订单状态与实际支付结果一致
     * 采用订单编号加锁，防止并发场景下的重复更新操作
     *
     * @param orderPayCheckDto 订单支付检查请求参数
     * @return OrderPayCheckVo 订单支付状态检查结果
     */
    @ServiceLock(name = ORDER_PAY_CHECK, keys = {"#orderPayCheckDto.orderNumber"})
    public OrderPayCheckVo payCheck(OrderPayCheckDto orderPayCheckDto) {
        // 初始化返回结果对象，用于封装订单支付状态信息
        OrderPayCheckVo orderPayCheckVo = new OrderPayCheckVo();
        // 根据订单号查询系统中的订单记录
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderPayCheckDto.getOrderNumber());
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        // 订单不存在
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        //  将订单基本信息复制到返回结果对象中
        BeanUtil.copyProperties(order, orderPayCheckVo);
        // 如果订单已取消，则进行退款
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            RefundDto refundDto = new RefundDto();
            refundDto.setOrderNumber(String.valueOf(order.getOrderNumber()));   // 订单号
            refundDto.setAmount(order.getOrderPrice());   // 退款金额（与订单金额一致）
            refundDto.setChannel("alipay");   // 退款渠道（支付宝）
            refundDto.setReason("延迟订单关闭");  // 退款原因
            // 远程调用支付客户端发起退款
            ApiResponse<String> response = payClient.refund(refundDto);
            // 退款成功
            if (response.getCode().equals(BaseCode.SUCCESS.getCode())) {
                Order updateOrder = new Order();
                updateOrder.setEditTime(DateUtils.now());
                updateOrder.setOrderStatus(OrderStatus.REFUND.getCode());
                orderMapper.update(updateOrder, Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, order.getOrderNumber()));
            } else {
                log.error("pay服务退款失败 dto : {} response : {}", JSON.toJSONString(refundDto), JSON.toJSONString(response));
            }
            // 设置返回结果的状态为"已退款"，并记录取消时间
            orderPayCheckVo.setOrderStatus(OrderStatus.REFUND.getCode());
            orderPayCheckVo.setCancelOrderTime(DateUtils.now());
            return orderPayCheckVo;
        }
        // 构建支付渠道查询参数，准备查询第三方支付平台的真实支付状态
        TradeCheckDto tradeCheckDto = new TradeCheckDto();
        // 设置订单号
        tradeCheckDto.setOutTradeNo(String.valueOf(orderPayCheckDto.getOrderNumber()));
        // 设置支付渠道
        tradeCheckDto.setChannel(Optional.ofNullable(PayChannel.getRc(orderPayCheckDto.getPayChannelType()))
                .map(PayChannel::getValue).orElseThrow(() -> new DaMaiFrameException(BaseCode.PAY_CHANNEL_NOT_EXIST)));
        // 远程调用支付客户端查询支付渠道的真实支付状态
        ApiResponse<TradeCheckVo> tradeCheckVoApiResponse = payClient.tradeCheck(tradeCheckDto);
        // 根据支付渠道返回的状态，同步更新系统订单状态
        if (!Objects.equals(tradeCheckVoApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(tradeCheckVoApiResponse);
        }
        TradeCheckVo tradeCheckVo = Optional.ofNullable(tradeCheckVoApiResponse.getData())
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.PAY_BILL_NOT_EXIST));
        // 支付状态查询成功
        if (tradeCheckVo.isSuccess()) {
            // 支付账单状态
            Integer payBillStatus = tradeCheckVo.getPayBillStatus();
            // 系统当前订单状态
            Integer orderStatus = order.getOrderStatus();
            // 若系统订单状态和支付账单状态不一致
            if (!Objects.equals(orderStatus, payBillStatus)) {
                // 更新系统订单状态
                orderPayCheckVo.setOrderStatus(payBillStatus);
                try {
                    // 若支付账单状态为"已支付"，则设置支付成功时间，并更新系统订单状态为"已支付"和相关数据
                    if (Objects.equals(payBillStatus, PayBillStatus.PAY.getCode())) {
                        orderPayCheckVo.setPayOrderTime(DateUtils.now());
                        orderService.updateOrderRelatedData(order.getOrderNumber(), OrderStatus.PAY);
                    }
                    // 若支付账单状态为"已取消"，则设置取消时间，并更新系统订单状态为"已取消"和相关数据
                    else if (Objects.equals(payBillStatus, PayBillStatus.CANCEL.getCode())) {
                        orderPayCheckVo.setCancelOrderTime(DateUtils.now());
                        orderService.updateOrderRelatedData(order.getOrderNumber(), OrderStatus.CANCEL);
                    }
                } catch (Exception e) {
                    log.warn("updateOrderRelatedData warn message", e);
                }
            }
        } else {
            throw new DaMaiFrameException(BaseCode.PAY_TRADE_CHECK_ERROR);
        }
        // 返回最终的订单支付状态检查结果
        return orderPayCheckVo;
    }

    /**
     * 支付宝支付成功后，支付宝服务器主动调用的回调通知处理接口
     * 负责接收、验证支付结果通知，并更新系统内的订单状态（核心是确认支付成功后的后续处理）
     *
     * @param request
     * @return
     */
    public String alipayNotify(HttpServletRequest request) {
        // 1.从请求中提取支付宝回调的参数
        Map<String, String> params = new HashMap<>(256);
        // 判断请求是否为自定义的请求包装类（用于获取请求体中的参数）
        if (request instanceof final CustomizeRequestWrapper customizeRequestWrapper) {
            String requestBody = customizeRequestWrapper.getRequestBody();  // 获取请求体内容
            params = StringUtil.convertQueryStringToMap(requestBody);   // 将参数转换为Map格式
        }
        log.info("收到支付宝回调通知 params : {}", JSON.toJSONString(params));
        // 2.获取订单号
        String outTradeNo = params.get("out_trade_no");
        // 若为空则返回失败（支付宝会重试通知）
        if (StringUtil.isEmpty(outTradeNo)) {
            return "failure";
        }
        // 3.基于订单号加分布式锁，防止同一订单的并发回调导致数据不一致
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, ORDER_PAY_NOTIFY_CHECK, new String[]{outTradeNo});
        // 加锁（阻塞式）
        lock.lock();
        try {
            // 4.根据订单号查询系统中的订单记录，验证订单是否存在
            Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                    .eq(Order::getOrderNumber, Long.parseLong(outTradeNo)));
            // 订单不存在，抛出异常
            if (Objects.isNull(order)) {
                throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
            }
            // 5.若订单已取消，但用户仍完成支付（需自动退款）
            if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
                // 构建退款参数
                RefundDto refundDto = new RefundDto();
                refundDto.setOrderNumber(outTradeNo);   // 订单号
                refundDto.setAmount(order.getOrderPrice());  // 退款金额（与订单金额一致）
                refundDto.setChannel("alipay");  // 退款渠道（支付宝）
                refundDto.setReason("延迟订单关闭");   // 退款原因
                // 远程调用支付客户端发起退款
                ApiResponse<String> response = payClient.refund(refundDto);
                if (response.getCode().equals(BaseCode.SUCCESS.getCode())) {
                    // 退款成功，更新订单状态为“已退款”
                    Order updateOrder = new Order();
                    updateOrder.setEditTime(DateUtils.now());  // 更新时间
                    updateOrder.setOrderStatus(OrderStatus.REFUND.getCode());   // 状态改为已退款
                    orderMapper.update(updateOrder, Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, outTradeNo));
                } else {
                    log.error("pay服务退款失败 dto : {} response : {}", JSON.toJSONString(refundDto), JSON.toJSONString(response));
                }
                return ALIPAY_NOTIFY_SUCCESS_RESULT;  // 返回成功标识（通常为"success"），告知支付宝已处理
            }
            // 6.调用支付客户端验证通知的合法性（签名、金额、商户信息等）
            NotifyDto notifyDto = new NotifyDto();
            notifyDto.setChannel(PayChannel.ALIPAY.getValue());  // 支付渠道（支付宝）
            notifyDto.setParams(params);  // 支付宝回调的参数
            ApiResponse<NotifyVo> notifyResponse = payClient.notify(notifyDto);
            // 验证失败，抛出异常
            if (!Objects.equals(notifyResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                throw new DaMaiFrameException(notifyResponse);
            }
            // 7.验证成功且支付状态为成功，更新订单相关数据
            if (ALIPAY_NOTIFY_SUCCESS_RESULT.equals(notifyResponse.getData().getPayResult())) {
                try {
                    // 更新订单状态为“已支付”，并处理关联数据（如座位最终确认、库存扣减生效等）
                    orderService.updateOrderRelatedData(
                            Long.parseLong(notifyResponse.getData().getOutTradeNo()), // 订单号
                            OrderStatus.PAY   // 目标状态：已支付
                    );
                } catch (Exception e) {
                    log.warn("updateOrderRelatedData warn message", e);
                }
            }
            // 8.返回处理结果给支付宝（成功返回"success"，失败返回"failure"）
            return notifyResponse.getData().getPayResult();
        } finally {
            lock.unlock(); // 释放锁
        }

    }

    /**
     * 更新订单及关联的购票人订单状态，并同步操作相关缓存数据
     * 支持两种状态变更：订单支付（PAY）和订单取消（CANCEL）
     *
     * @param orderNumber 订单号
     * @param orderStatus 目标订单状态（CANCEL取消）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderRelatedData(Long orderNumber, OrderStatus orderStatus) {
        // 校验目标状态合法性：仅允许"支付（PAY）"和"取消（CANCEL）"两种状态
        if (!(Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()) ||
                Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()))) {
            throw new DaMaiFrameException(BaseCode.OPERATE_ORDER_STATUS_NOT_PERMIT);
        }
        // 根据订单号查询当前订单信息
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderNumber);
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        // 检查当前订单状态是否允许变更
        checkOrderStatus(order);
        // 构建主订单更新对象
        Order updateOrder = new Order();
        updateOrder.setId(order.getId());  // 设置订单ID
        updateOrder.setOrderStatus(orderStatus.getCode());  // 设置目标订单状态
        // 构建子订单（购票人关联表）更新对象
        OrderTicketUser updateOrderTicketUser = new OrderTicketUser();
        updateOrderTicketUser.setOrderStatus(orderStatus.getCode());  // 同步设置子订单目标订单状态
        // 根据目标订单状态补充时间字段
        if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
            // 如果是“支付”状态，记录支付时间
            updateOrder.setPayOrderTime(DateUtils.now());
            updateOrderTicketUser.setPayOrderTime(DateUtils.now());
        } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            // 如果是“取消”状态，记录取消时间
            updateOrder.setCancelOrderTime(DateUtils.now());
            updateOrderTicketUser.setCancelOrderTime(DateUtils.now());
        }
        // 根据订单号更新主订单
        LambdaUpdateWrapper<Order> orderLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, order.getOrderNumber());
        int updateOrderResult = orderMapper.update(updateOrder, orderLambdaUpdateWrapper);
        // 根据订单号批量更新子订单（购票人关联表）
        LambdaUpdateWrapper<OrderTicketUser> orderTicketUserLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        int updateTicketUserOrderResult =
                orderTicketUserMapper.update(updateOrderTicketUser, orderTicketUserLambdaUpdateWrapper);
        // 校验更新结果：若主订单或子订单更新失败，抛出异常回滚事务
        if (updateOrderResult <= 0 || updateTicketUserOrderResult <= 0) {
            throw new DaMaiFrameException(BaseCode.ORDER_CANAL_ERROR);
        }
        // 根据订单号查询更新后的子订单列表
        LambdaQueryWrapper<OrderTicketUser> orderTicketUserLambdaQueryWrapper =
                Wrappers.lambdaQuery(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        List<OrderTicketUser> orderTicketUserList = orderTicketUserMapper.selectList(orderTicketUserLambdaQueryWrapper);
        // 校验子订单列表是否为空，若为空则抛出异常
        if (CollectionUtil.isEmpty(orderTicketUserList)) {
            throw new DaMaiFrameException(BaseCode.TICKET_USER_ORDER_NOT_EXIST);
        }
        // 处理“取消”状态的缓存回滚：用户购票计数减少
        if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            redisCache.incrBy(RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT,
                    order.getUserId(), order.getProgramId()), -updateTicketUserOrderResult); // 减少本次取消的票数（负数表示扣减）
        }
        // 按票档ID分组子订单，收集每个票档对应的座位ID
        Long programId = order.getProgramId();  // 节目ID
        // 分组逻辑：key：票档ID，value：该票档下的所有子订单
        Map<Long, List<OrderTicketUser>> orderTicketUserSeatList = orderTicketUserList.stream()
                .collect(Collectors.groupingBy(OrderTicketUser::getTicketCategoryId));
        // 转换为：key=票档ID，value=该票档下的座位ID列表
        Map<Long, List<Long>> seatMap = new HashMap<>(orderTicketUserSeatList.size());
        orderTicketUserSeatList.forEach((ticketCategoryId, orderTicketUsers) ->
                seatMap.put(ticketCategoryId, orderTicketUsers.stream()
                        .map(OrderTicketUser::getSeatId).collect(Collectors.toList()))
        );
        // 调用方法更新节目相关缓存（座位状态、票档余票等）
        updateProgramRelatedDataResolution(programId, seatMap, orderStatus);
    }

    /**
     * 校验当前订单状态是否允许执行后续操作
     * 用于拦截非法的订单状态流转（如对已取消的订单中再次取消，或对已支付的订单重复支付）
     *
     * @param order 待校验的订单对象
     */
    public void checkOrderStatus(Order order) {
        // 判断订单是否为空
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        // 判断订单状态是否为取消状态
        // 防止对已取消的订单再次执行取消或支付操作
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_CANCEL);
        }
        // 判断订单状态是否为支付状态
        // 防止对已支付的订单重复支付或取消（需走退款流程）
        if (Objects.equals(order.getOrderStatus(), OrderStatus.PAY.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_PAY);
        }
        // 判断订单状态是否为退款状态
        // 防止对已退款的订单执行无效操作（退款后订单状态不可逆转）
        if (Objects.equals(order.getOrderStatus(), OrderStatus.REFUND.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_REFUND);
        }
    }

    /**
     * 更新节目相关缓存数据
     * 根据订单状态执行不同操作：
     * - 支付（PAY）：将座位从"锁定缓存"迁移到"已售缓存"，状态更新为已售
     * - 取消（CANCEL）：将座位从"锁定缓存"迁移到"未售缓存"，状态更新为未售
     *
     * @param programId   节目Id
     * @param seatMap     票档 -> 座位ID列表
     * @param orderStatus 目标订单状态（PAY或CANCEL）
     */
    public void updateProgramRelatedDataResolution(Long programId, Map<Long, List<Long>> seatMap, OrderStatus orderStatus) {
        // 从Redis的“锁定座位缓存”中查询所有涉及的座位详情
        // 按票档ID分组存储（key：票档ID；value：该票档下的座位详情列表）
        Map<Long, List<SeatVo>> seatVoMap = new HashMap<>(seatMap.size());
        // 批量查询哈希缓存中指定座位ID的详情（转换为SeatVo对象）
        // 座位ID转为字符串
        seatMap.forEach((ticketCategoryId, seatIdList) -> seatVoMap.put(ticketCategoryId, redisCache.multiGetForHash(
                RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, ticketCategoryId),
                seatIdList.stream().map(String::valueOf).collect(Collectors.toList()), SeatVo.class)));
        // 校验座位详情列表是否为空，若为空则抛出异常
        if (CollectionUtil.isEmpty(seatVoMap)) {
            throw new DaMaiFrameException(BaseCode.LOCK_SEAT_LIST_EMPTY);
        }
        // 初始化缓存操作所需的数据容器
        JSONArray unLockSeatIdjsonArray = new JSONArray();  // 需从锁定缓存删除的座位信息
        JSONArray addSeatDatajsonArray = new JSONArray();  // 需添加到目标缓存的座位数据
        JSONArray ticketRemainJsonArray = new JSONArray();  // 票档余票变更数据
        // 票档-座位数映射（用于统计）
        List<TicketCategoryCountDto> ticketCategoryCountDtoList = new ArrayList<>(seatVoMap.size());
        // 所有需解锁的座位ID（用于支付场景）
        List<Long> unLockSeatIdList = new ArrayList<>();
        // 按票档处理座位状态迁移和余票更新
        seatVoMap.forEach((ticketCategoryId, seatVos) -> {
            // 记录需从“锁定缓存”中删除的座位信息
            JSONObject unLockSeatIdjsonObject = new JSONObject();
            // 锁定座位缓存键（用于删除操作）
            unLockSeatIdjsonObject.put("programSeatLockHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH,
                    programId,
                    ticketCategoryId
            ).getRelKey());
            // 该票档下需删除的座位ID列表（转为字符串）
            unLockSeatIdjsonObject.put("unLockSeatIdList", seatVos.stream()
                    .map(SeatVo::getId).map(String::valueOf).collect(Collectors.toList()));
            unLockSeatIdjsonArray.add(unLockSeatIdjsonObject);
            // 确定目标缓存键和座位状态（根据订单状态）
            String targetSeatHashKey = "";  // 目标缓存键（已售/未售）
            if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                // 订单取消：座位迁移到“未售缓存”，状态更新为“未售”
                targetSeatHashKey = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey();
                seatVos.forEach(seatVo -> seatVo.setSellStatus(SellStatus.NO_SOLD.getCode()));
            } else if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
                // 订单支付：座位迁移到“已售缓存”，状态更新为“已售”
                targetSeatHashKey = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey();
                seatVos.forEach(seatVo -> seatVo.setSellStatus(SellStatus.SOLD.getCode()));
            }
            // 记录需添加到目标缓存的座位数据
            JSONObject addSeatjsonObject = new JSONObject();
            addSeatjsonObject.put("seatHashKeyAdd", targetSeatHashKey);  // 目标缓存键
            // 座位数据列表（格式：[座位ID1, 座位1详情JSON, 座位ID2, 座位2详情JSON, ...]）
            List<String> seatDataList = new ArrayList<>();
            seatVos.forEach(seatVo -> {
                seatDataList.add(String.valueOf(seatVo.getId()));  // 座位ID（哈希的field）
                seatDataList.add(JSON.toJSONString(seatVo));       // 座位详情（哈希的value）
            });
            addSeatjsonObject.put("seatDataList", seatDataList);
            addSeatDatajsonArray.add(addSeatjsonObject);
            // 记录票档余票变更数据（取消时恢复余票，支付时余票不变但需同步状态）
            JSONObject ticketRemainjsonObject = new JSONObject();
            // 票档余票缓存键（格式：PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION:programId:ticketCategoryId）
            ticketRemainjsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
            // 票档ID
            ticketRemainjsonObject.put("ticketCategoryId", String.valueOf(ticketCategoryId));
            // 变更数量（取消时为正数，恢复余票）
            ticketRemainjsonObject.put("count", seatVos.size());
            ticketRemainJsonArray.add(ticketRemainjsonObject);
            // 收集辅助数据（用于支付场景的延迟消息）
            TicketCategoryCountDto ticketCategoryCountDto = new TicketCategoryCountDto();
            ticketCategoryCountDto.setTicketCategoryId(ticketCategoryId);
            ticketCategoryCountDto.setCount((long) seatVos.size());  // 该票档的座位数量
            ticketCategoryCountDtoList.add(ticketCategoryCountDto);
            // 收集所有需解锁的座位ID
            unLockSeatIdList.addAll(seatVos.stream().map(SeatVo::getId).toList());
        });
        // 执行Lua脚本
        List<String> keys = new ArrayList<>();
        keys.add(String.valueOf(orderStatus.getCode()));  // 传入订单状态码作为参数
        Object[] data = new String[3];
        data[0] = JSON.toJSONString(unLockSeatIdjsonArray);  // 解锁座位数据
        data[1] = JSON.toJSONString(addSeatDatajsonArray);  // 添加座位数据
        data[2] = JSON.toJSONString(ticketRemainJsonArray);  // 余票变更数据
        // 调用工具类执行Lua脚本，保证缓存操作的原子性（避免中间状态）
        orderProgramCacheResolutionOperate.programCacheReverseOperate(keys, data);
        // 支付状态的额外处理：发送延迟消息（用于异步更新统计数据等）
        if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
            ProgramOperateDataDto programOperateDataDto = new ProgramOperateDataDto();
            programOperateDataDto.setProgramId(programId);
            programOperateDataDto.setSeatIdList(unLockSeatIdList);  // 已售座位ID列表
            programOperateDataDto.setTicketCategoryCountDtoList(ticketCategoryCountDtoList);   // 票档-数量映射
            programOperateDataDto.setSellStatus(SellStatus.SOLD.getCode());   // 状态为已售
            // 发送延迟消息，后续由消费者处理
            delayOperateProgramDataSend.sendMessage(JSON.toJSONString(programOperateDataDto));
        }
    }

    /**
     * 根据用户ID获取订单列表信息
     *
     * @param orderListDto 用户ID查询DTO
     * @return 订单列表视图对象集合
     */
    public List<OrderListVo> selectList(OrderListDto orderListDto) {
        // 初始化返回的订单列表视图集合
        List<OrderListVo> orderListVos = new ArrayList<>();
        // 根据用户ID查询订单主表信息并按订单创建时间降序排序
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getUserId, orderListDto.getUserId())
                        .orderByDesc(Order::getCreateOrderTime);
        List<Order> orderList = orderMapper.selectList(orderLambdaQueryWrapper);
        // 如果订单列表为空，直接返回空集合
        if (CollectionUtil.isEmpty(orderList)) {
            return orderListVos;
        }
        // 复制订单基本信息
        orderListVos = BeanUtil.copyToList(orderList, OrderListVo.class);
        // 批量查询每个订单的票券总数
        List<OrderTicketUserAggregate> orderTicketUserAggregateList =
                orderTicketUserMapper.selectOrderTicketUserAggregate(orderList.stream().map(Order::getOrderNumber).
                        collect(Collectors.toList()));
        // 构建订单号 -> 票券数量的映射
        Map<Long, Integer> orderTicketUserAggregateMap = orderTicketUserAggregateList.stream()
                .collect(Collectors.toMap(OrderTicketUserAggregate::getOrderNumber,
                        OrderTicketUserAggregate::getOrderTicketUserCount, (v1, v2) -> v2));
        // 为每个订单视图对象设置票券数量
        for (OrderListVo orderListVo : orderListVos) {
            orderListVo.setTicketCount(orderTicketUserAggregateMap.get(orderListVo.getOrderNumber()));
        }
        // 返回订单列表视图对象集合
        return orderListVos;
    }

    /**
     * 根据订单查询条件获取订单详情信息
     *
     * @param orderGetDto 订单查询DTO
     * @return 订单详情视图对象（包含订单基本信息、票务信息、用户信息等）
     */
    public OrderGetVo get(OrderGetDto orderGetDto) {
        // 根据订单号查询订单主表信息
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderGetDto.getOrderNumber());
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        // 如果订单不存在，抛出异常
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        // 根据订单号查询购票人订单
        LambdaQueryWrapper<OrderTicketUser> orderTicketUserLambdaQueryWrapper =
                Wrappers.lambdaQuery(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        List<OrderTicketUser> orderTicketUserList = orderTicketUserMapper.selectList(orderTicketUserLambdaQueryWrapper);
        // 如果购票人订单不存在，抛出异常
        if (CollectionUtil.isEmpty(orderTicketUserList)) {
            throw new DaMaiFrameException(BaseCode.TICKET_USER_ORDER_NOT_EXIST);
        }
        // 组装订单基本信息到返回视图对象
        OrderGetVo orderGetVo = new OrderGetVo();
        // 复制订单主表属性到视图对象（如订单编号、状态、金额等）
        BeanUtil.copyProperties(order, orderGetVo);
        // 组装购票订单详情信息
        List<OrderTicketInfoVo> orderTicketInfoVoList = new ArrayList<>();
        // 按票价分组：key为票价，value为该票价对应的所有购票人记录
        Map<BigDecimal, List<OrderTicketUser>> orderTicketUserMap =
                orderTicketUserList.stream().collect(Collectors.groupingBy(OrderTicketUser::getOrderPrice));
        // 遍历每个票价分组，组装购票订单详情
        orderTicketUserMap.forEach((k, v) -> {
            OrderTicketInfoVo orderTicketInfoVo = new OrderTicketInfoVo();
            // 处理座位信息：若支持选座，则拼接所有座位信息；否则显示"暂无座位信息"
            String seatInfo = "暂无座位信息";
            if (order.getProgramPermitChooseSeat().equals(BusinessStatus.YES.getCode())) {
                seatInfo = v.stream()
                        .map(OrderTicketUser::getSeatInfo)   // 提取每个票的座位信息
                        .collect(Collectors.joining(","));   // 用逗号拼接
            }
            // 设置座位信息
            orderTicketInfoVo.setSeatInfo(seatInfo);
            // 设置票价
            orderTicketInfoVo.setPrice(v.get(0).getOrderPrice());
            // 设置票数
            orderTicketInfoVo.setQuantity(v.size());
            // 设置总金额（票价 * 数量）
            orderTicketInfoVo.setRelPrice(v.stream()
                    .map(OrderTicketUser::getOrderPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
            );
            orderTicketInfoVoList.add(orderTicketInfoVo);
        });
        // 将购票订单详情信息设置到返回视图对象
        orderGetVo.setOrderTicketInfoVoList(orderTicketInfoVoList);
        // 构建用户查询DTO
        UserGetAndTicketUserListDto userGetAndTicketUserListDto = new UserGetAndTicketUserListDto();
        userGetAndTicketUserListDto.setUserId(order.getUserId());  // 用户ID
        // 调用远程用户服务
        ApiResponse<UserGetAndTicketUserListVo> userGetAndTicketUserApiResponse =
                userClient.getUserAndTicketUserList(userGetAndTicketUserListDto);
        // 如果调用失败，抛出异常
        if (!Objects.equals(userGetAndTicketUserApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(userGetAndTicketUserApiResponse);

        }
        // 获取返回的用户信息，若为空则抛出异常
        UserGetAndTicketUserListVo userAndTicketUserListVo = Optional.ofNullable(userGetAndTicketUserApiResponse.getData())
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.RPC_RESULT_DATA_EMPTY));
        // 校验用户信息是否存在
        if (Objects.isNull(userAndTicketUserListVo.getUserVo())) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        // 校验购票人信息是否存在
        if (CollectionUtil.isEmpty(userAndTicketUserListVo.getTicketUserVoList())) {
            throw new DaMaiFrameException(BaseCode.TICKET_USER_EMPTY);
        }
        // 过滤出该订单下购票人的信息
        List<TicketUserVo> filterTicketUserVoList = new ArrayList<>();
        // 构建该用户下所有购票人ID到购票人信息的映射
        Map<Long, TicketUserVo> ticketUserVoMap = userAndTicketUserListVo.getTicketUserVoList()
                .stream().collect(Collectors.toMap(TicketUserVo::getId, ticketUserVo -> ticketUserVo, (v1, v2) -> v2));
        for (OrderTicketUser orderTicketUser : orderTicketUserList) {
            filterTicketUserVoList.add(ticketUserVoMap.get(orderTicketUser.getTicketUserId()));
        }
        // 组装用户信息到视图对象
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtil.copyProperties(userAndTicketUserListVo.getUserVo(), userInfoVo);
        // 封装用户信息和购票人信息
        UserAndTicketUserInfoVo userAndTicketUserInfoVo = new UserAndTicketUserInfoVo();
        userAndTicketUserInfoVo.setUserInfoVo(userInfoVo);
        userAndTicketUserInfoVo.setTicketUserInfoVoList(BeanUtil.copyToList(filterTicketUserVoList, TicketUserInfoVo.class));
        orderGetVo.setUserAndTicketUserInfoVo(userAndTicketUserInfoVo);
        // 返回完整的订单详情视图对象
        return orderGetVo;
    }

    public AccountOrderCountVo accountOrderCount(AccountOrderCountDto accountOrderCountDto) {
        AccountOrderCountVo accountOrderCountVo = new AccountOrderCountVo();
        accountOrderCountVo.setCount(orderMapper.accountOrderCount(accountOrderCountDto.getUserId(),
                accountOrderCountDto.getProgramId()));
        return accountOrderCountVo;
    }


    @RepeatExecuteLimit(name = CREATE_PROGRAM_ORDER_MQ, keys = {"#orderCreateDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public String createMq(OrderCreateDto orderCreateDto) {
        String orderNumber = create(orderCreateDto);
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_MQ, orderNumber), orderNumber, 1, TimeUnit.MINUTES);
        return orderNumber;
    }

    @RepeatExecuteLimit(name = PROGRAM_CACHE_REVERSE_MQ, keys = {"#programId"})
    public void updateProgramRelatedDataMq(Long programId, Map<Long, List<Long>> seatMap, OrderStatus orderStatus) {
        updateProgramRelatedDataResolution(programId, seatMap, orderStatus);
    }

    public String getCache(OrderGetDto orderGetDto) {
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_MQ, orderGetDto.getOrderNumber()), String.class);
    }

    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER, keys = {"#orderCancelDto.orderNumber"})
    @ServiceLock(name = ORDER_CANCEL_LOCK, keys = {"#orderCancelDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public boolean initiateCancel(OrderCancelDto orderCancelDto) {
        Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getOrderNumber, orderCancelDto.getOrderNumber()));
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        if (!Objects.equals(order.getOrderStatus(), OrderStatus.NO_PAY.getCode())) {
            throw new DaMaiFrameException(BaseCode.CAN_NOT_CANCEL);
        }
        return cancel(orderCancelDto);
    }


    public void delOrderAndOrderTicketUser() {
        orderMapper.relDelOrder();
        orderTicketUserMapper.relDelOrderTicketUser();
    }
}
