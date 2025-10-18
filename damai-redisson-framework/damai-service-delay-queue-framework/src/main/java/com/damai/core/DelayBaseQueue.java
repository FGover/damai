package com.damai.core;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列 阻塞队列
 * @author: 阿星不是程序员
 **/
@Slf4j
public class DelayBaseQueue {

    /**
     * Redisson客户端实例
     * 提供Redis分布式队列的操作能力，由子类继承使用
     */
    protected final RedissonClient redissonClient;

    /**
     * Redisson的阻塞队列实例（RBlockingQueue）
     * 用于存储和获取延迟消息，支持阻塞式获取（当队列空时，线程会等待直到有消息）
     * 队列名称由relTopic指定（如"order_timeout-0"）
     */
    protected final RBlockingQueue<String> blockingQueue;


    /**
     * 构造方法：初始化Redisson阻塞队列
     *
     * @param redissonClient Redisson客户端，用于创建分布式队列
     * @param relTopic       队列名称（关联的消息主题，含分区信息）
     */
    public DelayBaseQueue(RedissonClient redissonClient, String relTopic) {
        this.redissonClient = redissonClient;
        this.blockingQueue = redissonClient.getBlockingQueue(relTopic);
    }
}
