package com.damai.util;


import com.damai.constant.LockInfoType;
import com.damai.lockinfo.LockInfoHandle;
import com.damai.lockinfo.factory.LockInfoHandleFactory;
import com.damai.servicelock.LockType;
import com.damai.servicelock.ServiceLocker;
import com.damai.servicelock.factory.ServiceLockFactory;
import com.damai.servicelock.info.LockTimeOutStrategy;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁 方法类型操作
 * 无需通过@ServiceLock注解，直接通过代码调用实现分布式锁的获取、释放及任务执行
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class ServiceLockTool {

    private final LockInfoHandleFactory lockInfoHandleFactory;

    private final ServiceLockFactory serviceLockFactory;

    /**
     * 没有返回值的加锁执行
     *
     * @param taskRun 要执行的任务
     * @param name    锁的业务名
     * @param keys    锁的标识
     */
    public void execute(TaskRun taskRun, String name, String[] keys) {
        execute(taskRun, name, keys, 20);
    }

    /**
     * 没有返回值的加锁执行
     *
     * @param taskRun  要执行的任务
     * @param name     锁的业务名
     * @param keys     锁的标识
     * @param waitTime 等待时间
     */
    public void execute(TaskRun taskRun, String name, String[] keys, long waitTime) {
        execute(LockType.Reentrant, taskRun, name, keys, waitTime);
    }

    /**
     * 没有返回值的加锁执行
     *
     * @param lockType 锁类型
     * @param taskRun  要执行的任务
     * @param name     锁的业务名
     * @param keys     锁的标识
     */
    public void execute(LockType lockType, TaskRun taskRun, String name, String[] keys) {
        execute(lockType, taskRun, name, keys, 20);
    }

    /**
     * 没有返回值的加锁执行
     *
     * @param lockType 锁类型
     * @param taskRun  要执行的任务
     * @param name     锁的业务名
     * @param keys     锁的标识
     * @param waitTime 等待时间
     */
    public void execute(LockType lockType, TaskRun taskRun, String name, String[] keys, long waitTime) {
        // 获取锁信息处理器，生成唯一锁名称
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        // 简单拼装锁名称（不依赖切面信息，用于快速生成锁标识）
        String lockName = lockInfoHandle.simpleGetLockName(name, keys);
        // 根据锁类型获取对应的锁实例
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        // 尝试在指定时间内获取锁
        boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, waitTime);
        // 获取锁成功
        if (result) {
            try {
                // 执行任务
                taskRun.run();
            } finally {
                // 释放锁
                lock.unlock(lockName);
            }
        } else {
            // 获取锁失败，执行默认失败策略
            LockTimeOutStrategy.FAIL.handler(lockName);
        }
    }

    /**
     * 有返回值的加锁执行
     *
     * @param taskCall 要执行的任务
     * @param name     锁的业务名
     * @param keys     锁的标识
     * @return 要执行的任务的返回值
     */
    public <T> T submit(TaskCall<T> taskCall, String name, String[] keys) {
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        String lockName = lockInfoHandle.simpleGetLockName(name, keys);
        ServiceLocker lock = serviceLockFactory.getLock(LockType.Reentrant);
        boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, 30);
        if (result) {
            try {
                return taskCall.call();
            } finally {
                lock.unlock(lockName);
            }
        } else {
            LockTimeOutStrategy.FAIL.handler(lockName);
        }
        return null;
    }

    /**
     * 手动获取锁实例（根据锁类型和动态参数生成锁名）
     *
     * @param lockType 锁类型
     * @param name     锁的业务名称
     * @param keys     锁的标识
     * @return 锁实例（可手动调用lock()、unlock()控制）
     */
    public RLock getLock(LockType lockType, String name, String[] keys) {
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        String lockName = lockInfoHandle.simpleGetLockName(name, keys);
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        return lock.getLock(lockName);
    }

    /**
     * 手动获取锁实例（直接指定锁名）
     *
     * @param lockType 锁类型
     * @param lockName 锁的唯一标识
     * @return 锁实例
     */
    public RLock getLock(LockType lockType, String lockName) {
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        return lock.getLock(lockName);
    }
}
