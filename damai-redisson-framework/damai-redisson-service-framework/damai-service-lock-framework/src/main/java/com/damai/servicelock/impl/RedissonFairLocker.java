package com.damai.servicelock.impl;

import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 基于Redisson的公平锁实现类
 * 实现ServiceLocker接口，提供分布式公平锁的具体操作，确保多线程/多节点环境下按请求顺序获取锁，避免线程饥饿
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class RedissonFairLocker implements ServiceLocker {

    /**
     * Redisson客户端实例，用于与redis交互创建公平锁
     */
    private final RedissonClient redissonClient;

    /**
     * 获取公平锁对象（不立即加锁）
     *
     * @param lockKey 锁的唯一标识
     * @return Redisson的RLock对象（公平锁实例）
     */
    @Override
    public RLock getLock(String lockKey) {
        // 通过RedissonClient获取公平锁（基于Redis的zset实现，保证获取顺序）
        return redissonClient.getFairLock(lockKey);
    }

    /**
     * 加锁（阻塞式，默认不自动释放，需手动解锁）
     * 公平锁特性：等待时间最长的线程优先获取锁
     *
     * @param lockKey 锁的唯一标识
     * @return 获取到的公平锁实例
     */
    @Override
    public RLock lock(String lockKey) {
        RLock lock = redissonClient.getFairLock(lockKey);
        // 阻塞等待，直到获取锁
        lock.lock();
        return lock;
    }

    /**
     * 加锁（阻塞式，带自动释放时间，单位：秒）
     *
     * @param lockKey   锁的唯一标识
     * @param leaseTime 自动释放时间（秒）
     * @return 获取到的公平锁实例
     */
    @Override
    public RLock lock(String lockKey, long leaseTime) {
        RLock lock = redissonClient.getFairLock(lockKey);
        // 加锁并设置自动释放时间（超过该时间未手动解锁则自动释放）
        lock.lock(leaseTime, TimeUnit.SECONDS);
        return lock;
    }

    /**
     * 加锁（阻塞式，带自动释放时间，支持自定义时间单位）
     *
     * @param lockKey   锁的唯一标识
     * @param unit      时间单位（如TimeUnit.MINUTES）
     * @param leaseTime 自动释放时间（基于unit的数值）
     * @return 获取到的公平锁实例
     */
    @Override
    public RLock lock(String lockKey, TimeUnit unit, long leaseTime) {
        RLock lock = redissonClient.getFairLock(lockKey);
        lock.lock(leaseTime, unit);
        return lock;
    }

    /**
     * 尝试加锁（非阻塞式，带等待时间）
     * 若在waitTime时间内未获取到锁，则返回false
     *
     * @param lockKey  锁的唯一标识
     * @param unit     时间单位
     * @param waitTime 最大等待时间（超时未获取则放弃）
     * @return true：获取锁成功；false：获取锁失败
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime) {
        RLock lock = redissonClient.getFairLock(lockKey);
        try {
            // 尝试在waitTime时间内获取锁，不设置自动释放时间（需手动解锁）
            return lock.tryLock(waitTime, unit);
        } catch (InterruptedException e) {
            // 线程被中断时返回获取失败
            return false;
        }
    }

    /**
     * 尝试加锁（非阻塞式，带等待时间和自动释放时间）
     *
     * @param lockKey   锁的唯一标识
     * @param unit      时间单位
     * @param waitTime  最大等待时间
     * @param leaseTime 自动释放时间
     * @return true：获取锁成功；false：获取锁失败
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime, long leaseTime) {
        RLock lock = redissonClient.getFairLock(lockKey);
        try {
            // 尝试在waitTime时间内获取锁，获取成功后设置自动释放时间
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 根据锁的key释放锁
     *
     * @param lockKey 锁的唯一标识
     */
    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getFairLock(lockKey);
        lock.unlock();
    }

    /**
     * 根据锁对象释放锁
     *
     * @param lock 要释放的公平锁实例
     */
    @Override
    public void unlock(RLock lock) {
        lock.unlock();
    }

}
