package com.damai.repeatexecutelimit.aspect;

import com.damai.constant.LockInfoType;
import com.damai.exception.DaMaiFrameException;
import com.damai.handle.RedissonDataHandle;
import com.damai.locallock.LocalLockCache;
import com.damai.lockinfo.LockInfoHandle;
import com.damai.lockinfo.factory.LockInfoHandleFactory;
import com.damai.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.damai.servicelock.LockType;
import com.damai.servicelock.ServiceLocker;
import com.damai.servicelock.factory.ServiceLockFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.damai.repeatexecutelimit.constant.RepeatExecuteLimitConstant.PREFIX_NAME;
import static com.damai.repeatexecutelimit.constant.RepeatExecuteLimitConstant.SUCCESS_FLAG;

/**
 * /**
 *
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 防重复执行（幂等性）切面
 * 通过AOP环绕通知拦截被@RepeatExecuteLimit注解标记的方法，结合本地锁+分布式锁+Redis标记实现高并发场景下的幂等性控制，防止重复执行
 * @author: 阿星不是程序员
 **/

/**
 * 1.先看 “只用分布式锁” 的问题：性能瓶颈
 * 分布式锁（如基于 Redis 的锁）的核心作用是解决跨服务实例的并发问题（多台服务器、多个 JVM 进程之间的同步）。但它有一个天然缺陷：
 * 每次获取 / 释放锁都需要网络通信（与 Redis 服务器交互），而网络 IO 的耗时远高于本地内存操作（通常是毫秒级 vs 纳秒级）。
 * 在高并发场景下（比如每秒数万次请求），如果所有请求都直接竞争分布式锁，会导致：
 * (1)大量网络请求涌向 Redis，可能压垮 Redis 服务器；
 * (2)每个请求的响应时间被拉长（等待锁的网络开销）；
 * (3)分布式锁本身成为系统瓶颈。
 * ---------------------------------------------------------------------------------------------------------------
 * 2.再看 “只用本地锁” 的问题：分布式一致性失效
 * 本地锁（如 ReentrantLock）的作用是解决同一 JVM 进程内的并发问题（同一台服务器上的多个线程之间的同步），它的优势是纯内存操作，性能极高。
 * 但它有一个致命缺陷：本地锁只能在当前服务实例内生效，无法跨实例同步。
 * 在分布式系统中（多台服务器部署同一服务），如果只依赖本地锁：
 * (1)实例 A 的线程 1 获取到本地锁执行任务时，实例 B 的线程 2 完全不受影响，仍能执行相同任务，导致 “重复执行”（比如重复插入数据）；
 * (2)最终无法保证分布式环境下的数据一致性。
 * ---------------------------------------------------------------------------------------------------------------
 * 3. “本地锁 + 分布式锁” 的协同作用：兼顾性能与一致性（先本地拦截，再分布式控制）
 * (1)本地锁：拦截同一实例内的并发请求（性能优化）
 * 当多个线程在同一服务实例内并发调用被保护的方法（如saveApiData）时：
 * 本地锁（如LocalLockCache中的 ReentrantLock）会先拦截，只允许一个线程通过，其他线程直接失败（或等待）；
 * 这一步几乎没有性能损耗（纯内存操作），能过滤掉大部分 “同实例内的重复请求”，大幅减少后续分布式锁的竞争压力。
 * (2)分布式锁：控制跨实例的并发请求（一致性保证）
 * 经过本地锁过滤后，可能仍有不同服务实例的线程并发请求（比如实例 A 和实例 B 同时有一个线程通过本地锁）：
 * 此时分布式锁（如 Redisson 公平锁）会发挥作用，确保跨实例的请求中只有一个能执行，避免 “分布式重复执行”；
 * 由于本地锁已经过滤了大部分请求，分布式锁的竞争压力大幅降低，网络开销也随之减少。
 */

@Slf4j
@Aspect  // 标识为切面类，用于拦截目标方法并增强
// 分布式锁的Order是-10，而幂等要在分布式前执行，所以order的值要比分布式锁小
@Order(-11)   // 切面执行顺序（数值越小越先执行），确保在事务等其他切面之前执行，避免锁释放问题
@AllArgsConstructor
public class RepeatExecuteLimitAspect {

    // 本地锁缓存，用于存储本地ReentrantLock，减少分布式锁的网络开销
    private final LocalLockCache localLockCache;
    // 锁信息处理器工厂，用于获取防重复执行场景的锁信息处理器
    private final LockInfoHandleFactory lockInfoHandleFactory;
    // 服务锁工厂，用于创建分布式锁
    private final ServiceLockFactory serviceLockFactory;
    // Redisson数据处理器，用于操作redis（如设置/获取重复执行标记）
    private final RedissonDataHandle redissonDataHandle;

    /**
     * 环绕通知：拦截被@RepeatExecuteLimit注解标记的方法，实现防重复执行逻辑
     *
     * @param joinPoint   环绕通知的连接点对象，用于执行目标方法（proceed()）
     * @param repeatLimit 注解对象，包含防重复执行的配置参数（如时长、提示消息等）
     * @return 目标方法的执行结果
     */
    @Around("@annotation(repeatLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatLimit) throws Throwable {
        // 从注解中获取幂等性保持时长（秒）
        long durationTime = repeatLimit.durationTime();
        // 从注解中获取重复执行时的提示消息
        String message = repeatLimit.message();
        // 用于存储目标方法的执行结果
        Object obj;
        // 获取防重复执行场景的锁信息处理器（RepeatExecuteLimitLockInfoHandle）
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.REPEAT_EXECUTE_LIMIT);
        // 生成唯一锁名称（环境前缀（如"dev-"） + 业务前缀（如"REPEAT_EXECUTE_LIMIT"） + 分隔符（:） + 业务名 + 解析后的键）
        String lockName = lockInfoHandle.getLockName(joinPoint, repeatLimit.name(), repeatLimit.keys());
        // 生成redis中用于标记“已执行成功”的键（前缀（repeat_flag）+ 锁名称，避免键冲突）
        String repeatFlagName = PREFIX_NAME + lockName;
        // 先检查redis中是否已有“已执行成功”的标记（快速失败，减少锁竞争）
        String flagObject = redissonDataHandle.get(repeatFlagName);
        // 如果 flagObject == "success"
        if (SUCCESS_FLAG.equals(flagObject)) {
            // 若存在标记，说明该请求已执行过，直接抛出异常提示
            throw new DaMaiFrameException(message);
        }
        // 获取本地锁（ReentrantLock），防止同一JVM内的重复请求（减少分布式锁压力）
        ReentrantLock localLock = localLockCache.getLock(lockName, true);
        // 尝试获取本地锁（非阻塞，立即返回结果）
        boolean localLockResult = localLock.tryLock();
        if (!localLockResult) {
            // 本地锁获取失败，说明同一JVM内有重复请求，抛出异常
            throw new DaMaiFrameException(message);
        }
        // 获取本地锁成功
        try {
            //  获取分布式公平锁（确保多节点环境下的请求按顺序执行，避免饥饿）
            ServiceLocker lock = serviceLockFactory.getLock(LockType.Fair);
            // 尝试获取分布式锁（等待0秒，即立即返回，不阻塞）
            boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, 0);
            // 分布式锁获取成功
            if (result) {
                try {
                    // 再次检查Redis标记（双重校验，防止分布式环境下的并发问题）
                    flagObject = redissonDataHandle.get(repeatFlagName);
                    if (SUCCESS_FLAG.equals(flagObject)) {
                        throw new DaMaiFrameException(message);
                    }
                    // 执行目标方法
                    obj = joinPoint.proceed();
                    // 若配置了幂等时长，在Redis中设置"已执行成功"标记（有效期为durationTime秒）
                    if (durationTime > 0) {
                        try {
                            redissonDataHandle.set(repeatFlagName, SUCCESS_FLAG, durationTime, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.error("getBucket error", e);
                        }
                    }
                    return obj;
                } finally {
                    // 释放分布式锁（必须在finally中执行，确保锁一定被释放）
                    lock.unlock(lockName);
                }
            } else {
                // 分布式锁获取失败（其他节点正在执行相同请求）
                throw new DaMaiFrameException(message);
            }
        } finally {
            // 释放本地锁（必须在finally中执行，避免死锁）
            localLock.unlock();
        }
    }
}
