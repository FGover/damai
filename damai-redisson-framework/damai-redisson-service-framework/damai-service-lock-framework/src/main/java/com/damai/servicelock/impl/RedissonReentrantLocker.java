package com.damai.servicelock.impl;

import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁重入锁
 * 可重入锁特性：同一线程可多次获取同一把锁，无需等待，只需对应次数解锁即可
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class RedissonReentrantLocker implements ServiceLocker {

    // Redisson客户端实例，用于与Redis交互实现分布式锁
    private final RedissonClient redissonClient;

    /**
     * 获取可重入锁实例
     *
     * @param lockKey 锁的唯一标识
     * @return 分布式可重入锁对象
     */
    @Override
    public RLock getLock(String lockKey) {
        // 通过Redisson客户端获取指定key的可重入锁
        return redissonClient.getLock(lockKey);
    }

    /**
     * 阻塞式获取锁（无超时设置，需手动解锁）
     * 注意：实际生产环境建议使用带超时的方法，避免死锁风险
     *
     * @param lockKey 锁的唯一标识
     * @return 已获取的锁对象
     */
    @Override
    public RLock lock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();  // 阻塞直到获取锁，无自动释放时间
        return lock;
    }

    /**
     * 阻塞式获取锁（指定持有时间，单位为秒）
     * 超时后自动释放锁，无需手动解锁（但手动解锁更安全）
     *
     * @param lockKey   锁的唯一标识
     * @param leaseTime 锁的持有时间（秒），超时自动释放
     * @return 已获取的锁对象
     */
    @Override
    public RLock lock(String lockKey, long leaseTime) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(leaseTime, TimeUnit.SECONDS);
        return lock;
    }

    /**
     * 阻塞式获取锁（指定持有时间和时间单位）
     *
     * @param lockKey   锁的唯一标识
     * @param unit      时间单位（如毫秒、分钟）
     * @param leaseTime 锁的持有时间，超时自动释放
     * @return 已获取的锁对象
     */
    @Override
    public RLock lock(String lockKey, TimeUnit unit, long leaseTime) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(leaseTime, unit);
        return lock;
    }

    /**
     * 非阻塞式尝试获取锁（指定最大等待时间）
     * 在等待时间内获取到锁则返回true，否则返回false
     *
     * @param lockKey  锁的唯一标识
     * @param unit     时间单位
     * @param waitTime 最大等待时间
     * @return true：获取锁成功；false：获取锁失败
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试在waitTime内获取锁，无持有时间限制（需手动解锁）
            return lock.tryLock(waitTime, unit);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 非阻塞式尝试获取锁（指定等待时间和持有时间）
     *
     * @param lockKey   锁的唯一标识
     * @param unit      时间单位
     * @param waitTime  最大等待时间（获取锁的超时时间）
     * @param leaseTime 锁的持有时间（获取成功后自动释放）
     * @return true：获取锁成功；false：获取锁失败
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime, long leaseTime) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 根据锁标识释放锁
     *
     * @param lockKey 锁的唯一标识
     */
    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.unlock();
    }

    /**
     * 直接释放锁对象
     *
     * @param lock 已获取的锁对象
     */
    @Override
    public void unlock(RLock lock) {
        lock.unlock();
    }

}
