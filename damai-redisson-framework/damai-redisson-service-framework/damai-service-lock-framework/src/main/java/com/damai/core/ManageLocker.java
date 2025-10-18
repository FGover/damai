package com.damai.core;

import com.damai.servicelock.LockType;
import com.damai.servicelock.ServiceLocker;
import com.damai.servicelock.impl.RedissonFairLocker;
import com.damai.servicelock.impl.RedissonReadLocker;
import com.damai.servicelock.impl.RedissonReentrantLocker;
import com.damai.servicelock.impl.RedissonWriteLocker;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.Map;

import static com.damai.servicelock.LockType.Fair;
import static com.damai.servicelock.LockType.Read;
import static com.damai.servicelock.LockType.Reentrant;
import static com.damai.servicelock.LockType.Write;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁管理器
 * 负责初始化并缓存多种类型的分布式锁实例，提供统一的锁获取入口，简化分布式锁的创建和管理流程
 * @author: 阿星不是程序员
 **/
public class ManageLocker {

    /**
     * 分布式锁缓存容器：键为锁类型（LockType），值为对应的分布式锁实例（ServiceLocker）
     * 用于缓存已创建的锁实例，避免重复初始化
     */
    private final Map<LockType, ServiceLocker> cacheLocker = new HashMap<>();

    /**
     * 构造方法：初始化所有支持的分布式锁实例并缓存
     *
     * @param redissonClient Redisson客户端实例，用于创建基于Redis的分布式锁
     */
    public ManageLocker(RedissonClient redissonClient) {
        // 初始化可重入锁并缓存（默认锁类型，支持同一线程多次获取）
        cacheLocker.put(Reentrant, new RedissonReentrantLocker(redissonClient));
        // 初始化公平锁并缓存（按请求顺序获取锁，避免线程饥饿）
        cacheLocker.put(Fair, new RedissonFairLocker(redissonClient));
        // 初始化写锁并缓存（用于写操作，互斥性，与读锁互斥）
        cacheLocker.put(Write, new RedissonWriteLocker(redissonClient));
        // 初始化读锁并缓存（用于读操作，共享性，多个读操作可同时进行）
        cacheLocker.put(Read, new RedissonReadLocker(redissonClient));
    }

    /**
     * 获取可重入锁实例
     *
     * @return 可重入分布式锁（RedissonReentrantLocker实例）
     */
    public ServiceLocker getReentrantLocker() {
        return cacheLocker.get(Reentrant);
    }

    /**
     * 获取公平锁实例
     *
     * @return 公平分布式锁（RedissonFairLocker实例）
     */
    public ServiceLocker getFairLocker() {
        return cacheLocker.get(Fair);
    }

    /**
     * 获取写锁实例
     *
     * @return 分布式写锁（RedissonWriteLocker实例）
     */
    public ServiceLocker getWriteLocker() {
        return cacheLocker.get(Write);
    }

    /**
     * 获取读锁实例
     * @return 分布式读锁（RedissonReadLocker实例）
     */
    public ServiceLocker getReadLocker() {
        return cacheLocker.get(Read);
    }
}
