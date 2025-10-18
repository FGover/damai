package com.damai.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列 发送者上下文
 * @author: 阿星不是程序员
 **/
public class DelayQueueContext {

    /**
     * 延迟队列基础组件，提供Redis客户端和配置参数
     * 用于创建消息发送器（DelayQueueProduceCombine）
     */
    private final DelayQueueBasePart delayQueueBasePart;

    /**
     * 消息发送器缓存Map（线程安全的ConcurrentHashMap）
     * key：消息主题（topic，如"order_timeout"）
     * value：对应主题的消息发送器（DelayQueueProduceCombine）
     * 作用：缓存已创建的发送器，避免重复创建，提升性能
     */
    private final Map<String, DelayQueueProduceCombine> delayQueueProduceCombineMap = new ConcurrentHashMap<>();

    /**
     * 构造方法，注入延迟队列基础组件
     *
     * @param delayQueueBasePart 延迟队列基础资源（Redis客户端+配置）
     */
    public DelayQueueContext(DelayQueueBasePart delayQueueBasePart) {
        this.delayQueueBasePart = delayQueueBasePart;
    }

    /**
     * 发送延迟消息的核心方法，业务层通过此方法发送消息到指定主题
     *
     * @param topic     消息主题
     * @param content   消息内容
     * @param delayTime 延迟时间
     * @param timeUnit  迟时间单位
     */
    public void sendMessage(String topic, String content, long delayTime, TimeUnit timeUnit) {
        // 从缓存中获取或创建指定主题的消息发送器
        // computeIfAbsent：若topic不存在则创建发送器并缓存，存在则直接返回
        DelayQueueProduceCombine delayQueueProduceCombine = delayQueueProduceCombineMap.computeIfAbsent(
                topic, k -> new DelayQueueProduceCombine(delayQueueBasePart, topic));
        // 通过发送器发送延迟消息（底层调用Redisson的延迟队列API）
        delayQueueProduceCombine.offer(content, delayTime, timeUnit);
    }
}
