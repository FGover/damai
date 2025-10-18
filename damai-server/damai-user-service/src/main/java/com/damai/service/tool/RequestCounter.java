package com.damai.service.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 请求计数器工具类
 * 用于限制单位时间内的请求频率（默认1秒内最大请求数）
 * 基于原子类实现线程安全的计数，防止高并发场景下的超量请求
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class RequestCounter {

    // 原子计数器：记录单位时间内的请求次数（线程安全）
    private final AtomicInteger count = new AtomicInteger(0);
    // 原子时间戳：记录计数器上次重置的时间（线程安全）
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    // 每秒最大请求阈值（从配置文件读取，默认1000次/秒）
    @Value("${request_count_threshold:1000}")
    private int maxRequestsPerSecond = 1000;

    /**
     * 处理请求时调用，判断当前请求是否超过频率限制
     * 采用synchronized保证多线程下时间窗口判断与计数的原子性
     *
     * @return boolean true-超过限制；false-未超过限制
     */
    public synchronized boolean onRequest() {
        // 获取当前时间戳（毫秒）
        long currentTime = System.currentTimeMillis();
        // 时间窗口大小：1000毫秒（1秒）
        long differenceValue = 1000;
        // 检查是否超出当前时间窗口（距离上次重置已超过1秒）
        if (currentTime - lastResetTime.get() >= differenceValue) {
            count.set(0);  // 重置计数器为0
            lastResetTime.set(currentTime);  // 更新重置时间为当前时间（开启新的时间窗口）
        }
        // 计数器自增并判断是否超过阈值
        if (count.incrementAndGet() > maxRequestsPerSecond) {
            log.warn("请求超过每秒{}次限制", maxRequestsPerSecond);
            // 重置计数器（立即开启新窗口，避免长期阻塞）
            count.set(0);
            lastResetTime.set(System.currentTimeMillis());
            return true;
        }
        return false;
    }
}
