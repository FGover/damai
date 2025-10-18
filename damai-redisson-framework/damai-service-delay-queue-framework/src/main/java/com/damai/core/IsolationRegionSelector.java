package com.damai.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列 分区选择器
 * 用于在多个分区中轮询选择目标分区，实现消息负载均衡
 * @author: 阿星不是程序员
 **/
public class IsolationRegionSelector {

    /**
     * 原子计数器（线程安全）
     * 用于记录当前已选择的分区索引，支持并发环境下的自增操作
     */
    private final AtomicInteger count = new AtomicInteger(0);

    /**
     * 分区总数阈值（即配置的隔离分区数isolationRegionCount）
     * 例如：阈值为5时，分区索引范围为0~4
     */
    private final Integer thresholdValue;

    /**
     * 构造方法，初始化分区总数阈值
     *
     * @param thresholdValue 分区总数（从配置文件获取）
     */
    public IsolationRegionSelector(Integer thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    /**
     * 重置计数器为0
     *
     * @return 重置后的计数值（0）
     */
    private int reset() {
        count.set(0);
        return count.get();
    }

    /**
     * 线程安全地获取下一个分区索引（轮询策略）
     *
     * @return 分区索引（0 ~ thresholdValue-1）
     */
    public synchronized int getIndex() {
        // 获取当前计数器值
        int cur = count.get();
        // 若当前索引已达到阈值（超过最大分区索引），则重置为0
        if (cur >= thresholdValue) {
            cur = reset();
        } else {
            // 否则计数器自增（下次获取时使用下一个索引）
            count.incrementAndGet();
        }
        // 返回当前分区索引
        return cur;
    }
}
