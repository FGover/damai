package com.damai.servicelock.factory;

import com.damai.core.ManageLocker;
import com.damai.servicelock.LockType;
import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁工厂类
 * 根据锁类型（LockType）获取对应的分布式锁实例，封装不同类型分布式锁的创建逻辑，实现业务与具体锁实现的解耦
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class ServiceLockFactory {

    /**
     * 分布式锁管理器，负责创建和管理各种类型的分布式锁实例
     */
    private final ManageLocker manageLocker;

    /**
     * 根据锁类型获取对应的分布式锁实例
     * 支持公平锁、写锁、读锁、可重入锁等不同类型，满足多样化的分布式并发控制需求
     *
     * @param lockType 锁类型枚举（如Fair-公平锁、Write-写锁等）
     * @return 对应的分布式锁实例（ServiceLocker接口实现类）
     */
    public ServiceLocker getLock(LockType lockType) {
        // 根据锁类型选择对应的分布式锁
        return switch (lockType) {
            // 获取公平锁（按请求顺序获取锁，避免线程饥饿）
            case Fair -> manageLocker.getFairLocker();
            // 获取写锁（用于写操作，互斥性，同一时间仅允许一个写操作）
            case Write -> manageLocker.getWriteLocker();
            // 获取读锁（用于读操作，共享性，允许多个读操作同时进行）
            case Read -> manageLocker.getReadLocker();
            // 默认获取可重入锁（同一线程可多次获取锁，避免死锁）
            default -> manageLocker.getReentrantLocker();
        };
    }
}
