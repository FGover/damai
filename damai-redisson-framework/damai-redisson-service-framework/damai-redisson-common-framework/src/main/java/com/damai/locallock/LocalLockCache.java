package com.damai.locallock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 本地锁缓存工具类
 * 基于Caffeine缓存框架管理本地ReentrantLock锁实例，用于同一JVM内的并发控制，减少分布式锁的网络开销
 * @author: 阿星不是程序员
 **/
public class LocalLockCache {

    /**
     * 本地锁缓存容器，键为锁名称
     * 用于缓存和管理本地锁，避免频繁创建锁对象
     */
    private Cache<String, ReentrantLock> localLockCache;

    /**
     * 本地锁的过期时间(小时单位)
     * 从配置文件读取，默认值为48小时，用于自动清理长时间未使用的锁对象，释放内存
     */
    @Value("${durationTime:48}")
    private Integer durationTime;

    /**
     * 初始化本地锁缓存（PostConstruct注解：在依赖注入完成后执行）
     * 配置Caffeine缓存的过期策略，写入后超过指定时间未使用则自动过期
     */
    @PostConstruct
    public void localLockCacheInit() {
        localLockCache = Caffeine.newBuilder()
                // 设置过期时间：写入后durationTime小时内未被访问，则自动移除
                .expireAfterWrite(durationTime, TimeUnit.HOURS)
                .build();
    }

    /**
     * 获取或创建本地锁（线程安全）
     * Caffeine的get方法是线程安全的，确保并发场景下锁对象的唯一性
     *
     * @param lockKey 锁的唯一标识（如传进来的锁名称）
     * @param fair    是否为公平锁（true：按请求顺序获取锁；false：非公平锁，性能更高）
     * @return 对应的ReentrantLock实例（若不存在则创建并缓存）
     */
    public ReentrantLock getLock(String lockKey, boolean fair) {
        // 调用Caffeine的get方法：若lockKey存在则返回缓存的锁，否则执行lambda创建新锁并缓存
        return localLockCache.get(lockKey, key -> new ReentrantLock(fair));
    }
}
