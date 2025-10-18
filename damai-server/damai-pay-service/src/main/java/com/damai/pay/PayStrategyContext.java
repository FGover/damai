package com.damai.pay;

import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 支付策略上下文类，用于管理和获取不同支付渠道的策略处理器，实现支付渠道的动态切换
 * @author: 阿星不是程序员
 **/
public class PayStrategyContext {

    // 存储支付渠道与对应策略处理器的映射关系（键：支付渠道标识，如"alipay"、"wx"；值：对应渠道的支付处理器）
    private final Map<String, PayStrategyHandler> payStrategyHandlerMap = new HashMap<>();

    /**
     * 注册支付策略处理器
     * 将支付渠道与对应的处理器关联并存储，便于后续根据渠道获取处理器
     *
     * @param channel            支付渠道标识（如"alipay"表示支付宝）
     * @param payStrategyHandler 该渠道对应的支付策略处理器（实现了PayStrategyHandler接口的具体类）
     */
    public void put(String channel, PayStrategyHandler payStrategyHandler) {
        payStrategyHandlerMap.put(channel, payStrategyHandler);
    }

    /**
     * 根据支付渠道获取对应的支付策略处理器
     * 若渠道不存在对应处理器，则抛出异常
     *
     * @param channel 支付渠道标识（如"alipay"表示支付宝）
     * @return 对应渠道的支付策略处理器
     */
    public PayStrategyHandler get(String channel) {
        // 使用Optional避免空指针：若map中存在该渠道的处理器则返回，否则抛出异常
        return Optional.ofNullable(payStrategyHandlerMap.get(channel)).orElseThrow(
                () -> new DaMaiFrameException(BaseCode.PAY_STRATEGY_NOT_EXIST));
    }
}
