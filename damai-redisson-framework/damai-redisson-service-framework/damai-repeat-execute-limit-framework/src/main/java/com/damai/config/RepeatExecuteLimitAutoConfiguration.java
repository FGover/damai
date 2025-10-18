package com.damai.config;

import com.damai.constant.LockInfoType;
import com.damai.handle.RedissonDataHandle;
import com.damai.locallock.LocalLockCache;
import com.damai.lockinfo.LockInfoHandle;
import com.damai.lockinfo.factory.LockInfoHandleFactory;
import com.damai.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.damai.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import com.damai.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Bean;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 防重复执行（幂等性）自动配置类
 * 负责初始化防重复执行相关的核心组件，通过Spring IOC容器管理这些组件的生命周期，
 * @author: 阿星不是程序员
 **/
public class RepeatExecuteLimitAutoConfiguration {

    /**
     * 创建重复执行限制的所信息处理器Bean
     * 用于处理@RepeatExecuteLimit注解对应的锁信息，并通过Bean名称（LockInfoType.REPEAT_EXECUTE_LIMIT）标识其用途，便于工厂类根据类型获取
     *
     * @return 重复执行限制的锁信息处理器实例
     */
    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle() {
        return new RepeatExecuteLimitLockInfoHandle();
    }

    /**
     * 创建重复执行限制的切面Bean（核心组件）
     * 切面负责拦截被@RepeatExecuteLimit注解标记的方法，实现防重复执行的逻辑
     *
     * @param localLockCache        本地锁缓存，用于本地缓存锁信息，减少分布式锁的网络开销
     * @param lockInfoHandleFactory 锁信息处理器工厂，用于根据类型获取对应的LockInfoHandle
     * @param serviceLockFactory    服务锁工厂，用于创建分布式锁实例（如Redisson锁）
     * @param redissonDataHandle    Redisson数据处理器，用于操作Redisson分布式锁
     * @return
     */
    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LocalLockCache localLockCache,
                                                             LockInfoHandleFactory lockInfoHandleFactory,
                                                             ServiceLockFactory serviceLockFactory,
                                                             RedissonDataHandle redissonDataHandle) {
        return new RepeatExecuteLimitAspect(localLockCache, lockInfoHandleFactory, serviceLockFactory, redissonDataHandle);
    }
}
    