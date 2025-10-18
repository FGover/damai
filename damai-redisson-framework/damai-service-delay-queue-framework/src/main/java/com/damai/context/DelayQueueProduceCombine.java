package com.damai.context;

import com.damai.core.DelayProduceQueue;
import com.damai.core.IsolationRegionSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列消息发送组合器，负责将消息分散到多个分区队列，实现负载均衡
 * 基于分片策略提升高并发场景下的消息发送效率，避免单队列瓶颈
 * @author: 阿星不是程序员
 **/
public class DelayQueueProduceCombine {

    /**
     * 分区选择器，用于在多个分区中选择一个目标分区
     * 基于配置的分区数（isolationRegionCount）实现轮询或哈希等选择策略
     */
    private final IsolationRegionSelector isolationRegionSelector;

    /**
     * 延迟消息发送队列列表，每个元素对应一个分区的发送器
     * 例如：分区数为5时，列表包含5个DelayProduceQueue，对应"topic-0"到"topic-4"
     */
    private final List<DelayProduceQueue> delayProduceQueueList = new ArrayList<>();

    /**
     * 构造方法：初始化分区发送器和分区选择器
     *
     * @param delayQueueBasePart 延迟队列基础组件
     * @param topic              消息主题
     */
    public DelayQueueProduceCombine(DelayQueueBasePart delayQueueBasePart, String topic) {
        // 获取配置的分区数
        Integer isolationRegionCount = delayQueueBasePart.getDelayQueueProperties().getIsolationRegionCount();
        // 初始化分区选择器，指定总分区数
        isolationRegionSelector = new IsolationRegionSelector(isolationRegionCount);
        // 为每个分区创建对应的发送队列（DelayProduceQueue）
        // 分区队列名称格式：主题 + 分区索引（如"order_timeout-0"）
        for (int i = 0; i < isolationRegionCount; i++) {
            delayProduceQueueList.add(new DelayProduceQueue(delayQueueBasePart.getRedissonClient(), topic + "-" + i));
        }
    }

    /**
     * 发送延迟消息：通过分区选择器选择一个分区，将消息发送到该分区的延迟队列
     *
     * @param content   消息内容（JSON字符串）
     * @param delayTime 延迟时间
     * @param timeUnit  延迟时间单位
     */
    public void offer(String content, long delayTime, TimeUnit timeUnit) {
        // 由分区选择器计算目标分区索引
        int index = isolationRegionSelector.getIndex();
        // 获取对应分区的发送队列，发送消息
        delayProduceQueueList.get(index).offer(content, delayTime, timeUnit);
    }
}
