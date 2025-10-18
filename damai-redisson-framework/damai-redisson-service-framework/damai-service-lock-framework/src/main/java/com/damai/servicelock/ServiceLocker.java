package com.damai.servicelock;

import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁接口
 * 定义分布式锁的通用操作方法，统一各类分布式锁的使用，屏蔽不同锁实现的细节差异
 * @author: 阿星不是程序员
 **/
public interface ServiceLocker {

    /**
     * 获取分布式锁对象（不立即加锁，仅返回锁实例）
     *
     * @param lockKey 锁的唯一标识（如业务场景+参数键组成的字符串）
     * @return Redisson的RLock对象（分布式锁实例）
     */
    RLock getLock(String lockKey);

    /**
     * 加锁（阻塞式，默认不自动释放，需手动解锁）
     * 若锁已被占用，则当前线程会阻塞等待，直到获取到锁
     *
     * @param lockKey 锁的唯一标识
     * @return 获取到的RLock对象
     */
    RLock lock(String lockKey);

    /**
     * 加锁（带自动释放时间，单位：毫秒）
     * 若锁已被占用，当前线程会阻塞等待；获取锁后，超过leaseTime未释放则自动解锁
     *
     * @param lockKey   锁的唯一标识
     * @param leaseTime 自动释放时间（毫秒）
     * @return 获取到的RLock对象
     */
    RLock lock(String lockKey, long leaseTime);

    /**
     * 加锁（带自动释放时间，支持自定义时间单位）
     * 功能同上面的lock方法，仅时间单位可自定义（如秒、分钟等）
     *
     * @param lockKey   锁的唯一标识
     * @param unit      时间单位（如TimeUnit.SECONDS）
     * @param leaseTime 自动释放时间（基于unit的数值）
     * @return 获取到的RLock对象
     */
    RLock lock(String lockKey, TimeUnit unit, long leaseTime);

    /**
     * 尝试加锁（非阻塞式，带等待时间）
     * 若锁未被占用，则立即获取锁并返回true；若已被占用，会等待waitTime时间，超时则返回false
     *
     * @param lockKey  锁的唯一标识
     * @param unit     时间单位
     * @param waitTime 最大等待时间（超时未获取则放弃）
     * @return true：获取锁成功；false：获取锁失败
     */
    boolean tryLock(String lockKey, TimeUnit unit, long waitTime);

    /**
     * 尝试加锁（非阻塞式，带等待时间和自动释放时间）
     * 结合tryLock和lock的特性：等待waitTime获取锁，获取后超过leaseTime自动释放
     *
     * @param lockKey   锁的唯一标识
     * @param unit      时间单位
     * @param waitTime  最大等待时间（超时未获取则放弃）
     * @param leaseTime 自动释放时间（获取锁后，超过此时长自动解锁）
     * @return true：获取锁成功；false：获取锁失败
     */
    boolean tryLock(String lockKey, TimeUnit unit, long waitTime, long leaseTime);

    /**
     * 解锁（根据锁的key释放锁）
     * 需与加锁操作配对使用，避免死锁
     *
     * @param lockKey 锁的唯一标识（需与加锁时的key一致）
     */
    void unlock(String lockKey);

    /**
     * 解锁（根据锁对象释放锁）
     * 适用于已通过getLock方法获取到RLock对象的场景
     *
     * @param lock 要释放的RLock对象
     */
    void unlock(RLock lock);
}