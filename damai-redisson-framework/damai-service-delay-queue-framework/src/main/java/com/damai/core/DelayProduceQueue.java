package com.damai.core;

import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列的消息生产者类，负责向Redisson延迟队列发送带延迟时间的消息
 * 基于Redisson的RDelayedQueue实现分布式延迟消息的投递
 * @author: 阿星不是程序员
 **/
public class DelayProduceQueue extends DelayBaseQueue {

    /**
     * Redisson的延迟队列实例（RDelayedQueue）
     * 用于存储带延迟时间的消息，当延迟时间到期后，消息会自动迁移到阻塞队列（blockingQueue）
     */
    private final RDelayedQueue<String> delayedQueue;

    /**
     * 构造方法：初始化延迟队列，关联阻塞队列
     *
     * @param redissonClient Redisson客户端，用于创建延迟队列
     * @param relTopic       队列名称（如"order_timeout-0"，含分区索引的主题）
     */
    public DelayProduceQueue(RedissonClient redissonClient, final String relTopic) {
        // 调用父类构造方法，初始化阻塞队列（blockingQueue）
        super(redissonClient, relTopic);
        // 创建Redisson延迟队列，与父类的阻塞队列绑定
        // 原理：delayedQueue负责暂存延迟消息，到期后自动将消息移至blockingQueue，供消费者获取
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
    }

    /**
     * 发送延迟消息到队列
     *
     * @param content   消息内容（通常为JSON字符串，包含业务数据如订单ID）
     * @param delayTime 延迟时间（如15）
     * @param timeUnit  延迟时间单位（如TimeUnit.MINUTES，即15分钟后消息到期）
     */
    public void offer(String content, long delayTime, TimeUnit timeUnit) {
        // 调用Redisson延迟队列的offer方法，发送带延迟的消息
        // 底层逻辑：消息先存储在delayedQueue，到期后自动转移到blockingQueue，被消费者监听获取
        delayedQueue.offer(content, delayTime, timeUnit);
    }
}
