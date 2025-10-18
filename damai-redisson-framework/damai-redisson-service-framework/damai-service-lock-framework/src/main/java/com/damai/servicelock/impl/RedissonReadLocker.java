package com.damai.servicelock.impl;

import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁读锁
 * 在读写分离场景中，提供共享读锁支持，允许多个线程同时获取读锁，但会阻塞写锁获取
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class RedissonReadLocker implements ServiceLocker {

    // Redisson客户端实例，用于操作Redis分布式锁
    private final RedissonClient redissonClient;

    /**
     * 获取读锁实例
     *
     * @param lockKey 锁的唯一标识（如业务场景+参数键组成的字符串）
     * @return
     */
    @Override
    public RLock getLock(String lockKey) {
        // 通过Redisson的读写锁（ReadWriteLock）获取读锁
        // 读写锁特性：多个读锁可共存，读锁与写锁互斥，写锁与写锁互斥
        return redissonClient.getReadWriteLock(lockKey).readLock();
    }

    /**
     * 获取读锁
     * 注意：实际使用中应避免无超时锁，防止死锁（如服务宕机未释放锁）
     *
     * @param lockKey 锁的唯一标识
     * @return
     */
    @Override
    public RLock lock(String lockKey) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        lock.lock();  // 阻塞式获取锁，直到成功
        return lock;
    }

    /**
     * 获取读锁（指定超时时间，单位默认秒）
     * 超时后自动释放锁，避免死锁风险
     *
     * @param lockKey   锁的唯一标识
     * @param leaseTime 自动释放时间（毫秒）
     * @return
     */
    @Override
    public RLock lock(String lockKey, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        lock.lock(leaseTime, TimeUnit.SECONDS);
        return lock;
    }

    /**
     * 获取读锁（指定超时时间和时间单位）
     *
     * @param lockKey   锁的唯一标识
     * @param unit      时间单位（如TimeUnit.SECONDS）
     * @param leaseTime 自动释放时间（基于unit的数值）
     * @return
     */
    @Override
    public RLock lock(String lockKey, TimeUnit unit, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        lock.lock(leaseTime, unit);
        return lock;
    }

    /**
     * 尝试获取读锁（指定等待时间，获取失败则返回false）
     * 非阻塞式获取：在waitTime时间内未获取到锁则返回失败
     *
     * @param lockKey  锁的唯一标识
     * @param unit     时间单位
     * @param waitTime 最大等待时间（超过此时间未获取则失败）
     * @return true：获取锁成功；false：获取锁失败
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        try {
            return lock.tryLock(waitTime, unit);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 尝试获取读锁（指定等待时间和持有时间）
     *
     * @param lockKey   锁的唯一标识
     * @param unit      时间单位
     * @param waitTime  最大等待时间（获取锁的超时时间）
     * @param leaseTime 锁的持有时间（获取成功后，超过此时间自动释放）
     * @return true：获取锁成功；false：获取锁失败
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 释放锁（根据锁标识释放）
     *
     * @param lockKey 锁的唯一标识
     */
    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        // 注意：只有持有锁的线程才能释放锁，否则会抛出IllegalMonitorStateException
        lock.unlock();
    }

    /**
     * 释放锁（直接操作锁对象）
     *
     * @param lock 已获取的锁对象
     */
    @Override
    public void unlock(RLock lock) {
        // 直接释放传入的锁对象，适用于需要显式控制锁释放的场景
        lock.unlock();
    }

}
