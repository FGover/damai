package com.damai.config;

import com.damai.constant.LockInfoType;
import com.damai.core.ManageLocker;
import com.damai.lockinfo.LockInfoHandle;
import com.damai.lockinfo.factory.LockInfoHandleFactory;
import com.damai.lockinfo.impl.ServiceLockInfoHandle;
import com.damai.servicelock.aspect.ServiceLockAspect;
import com.damai.servicelock.factory.ServiceLockFactory;
import com.damai.util.ServiceLockTool;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁自动配置类
 * @author: 阿星不是程序员
 **/
public class ServiceLockAutoConfiguration {

    /**
     * 注册服务锁信息处理器
     * 用于生成分布式锁的名称
     *
     * @return 服务锁信息处理器实例
     */
    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle() {
        return new ServiceLockInfoHandle();
    }

    /**
     * 注册锁管理器
     * 封装Redisson客户端，提供各类锁的基础操作
     *
     * @param redissonClient Redisson客户端实例（由外部配置注入）
     * @return 锁管理器实例
     */
    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient) {
        return new ManageLocker(redissonClient);
    }

    /**
     * 注册服务锁工厂
     * 根据锁类型（如可重入锁、读锁、写锁）创建对应的锁实例，实现锁的多态管理
     *
     * @param manageLocker 锁管理器，提供底层锁操作支持
     * @return 服务锁工厂实例
     */
    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker) {
        return new ServiceLockFactory(manageLocker);
    }

    /**
     * 注册服务锁切面
     * 实现AOP环绕通知，自动为@ServiceLock注解的方法加锁/解锁
     *
     * @param lockInfoHandleFactory 锁信息处理器工厂，用于获取锁名称生成器
     * @param serviceLockFactory    服务锁工厂，用于获取具体锁实例
     * @return 服务锁切面实例
     */
    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory,
                                               ServiceLockFactory serviceLockFactory) {
        return new ServiceLockAspect(lockInfoHandleFactory, serviceLockFactory);
    }

    /**
     * 注册分布式锁工具类
     * 提供手动加锁/解锁的静态方法，便于非注解场景下使用分布式锁
     *
     * @param lockInfoHandleFactory 锁信息处理器工厂
     * @param serviceLockFactory    服务锁工厂
     * @return 服务锁工具类实例
     */
    @Bean
    public ServiceLockTool serviceLockUtil(LockInfoHandleFactory lockInfoHandleFactory,
                                           ServiceLockFactory serviceLockFactory) {
        return new ServiceLockTool(lockInfoHandleFactory, serviceLockFactory);
    }
}
