package com.damai.servicelock.aspect;

import com.damai.constant.LockInfoType;
import com.damai.util.StringUtil;
import com.damai.lockinfo.LockInfoHandle;
import com.damai.lockinfo.factory.LockInfoHandleFactory;
import com.damai.servicelock.LockType;
import com.damai.servicelock.ServiceLocker;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.servicelock.factory.ServiceLockFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁切面类，通过AOP方式实现分布式锁的自动加锁与解锁
 * 拦截被@ServiceLock注解标记的方法，在方法执行前加锁，执行后解锁，确保分布式环境下的方法原子性执行
 * @author: 阿星不是程序员
 **/
@Slf4j
@Aspect
@Order(-10)
@AllArgsConstructor
public class ServiceLockAspect {

    // 锁信息处理器工厂
    private final LockInfoHandleFactory lockInfoHandleFactory;
    // 分布式锁工厂
    private final ServiceLockFactory serviceLockFactory;

    /**
     * 环绕通知：拦截被@ServiceLock注解标记的方法，实现分布式锁的加锁与解锁逻辑
     *
     * @param joinPoint   连接点对象，包含被拦截方法的信息（参数、目标对象等）
     * @param servicelock 方法上的@ServiceLock注解实例，包含锁的配置信息
     * @return 被拦截方法的执行结果
     */
    @Around("@annotation(servicelock)")  // 切入点：匹配所有标注了@ServiceLock的方法
    public Object around(ProceedingJoinPoint joinPoint, ServiceLock servicelock) throws Throwable {
        // 获取锁信息处理器，用于生成具体的锁名称
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        // 生成锁名称
        String lockName = lockInfoHandle.getLockName(joinPoint, servicelock.name(), servicelock.keys());
        // 从注解中获取锁配置：锁类型、等待时间、时间单位
        LockType lockType = servicelock.lockType();
        long waitTime = servicelock.waitTime();
        TimeUnit timeUnit = servicelock.timeUnit();
        // 根据锁类型获取具体的分布式锁实现
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        // 尝试加锁：在指定时间内获取锁，成功则执行业务，失败则执行超时策略
        boolean result = lock.tryLock(lockName, timeUnit, waitTime);
        // 加锁成功
        if (result) {
            try {
                // 执行被拦截的业务方法
                return joinPoint.proceed();
            } finally {
                // 释放锁
                lock.unlock(lockName);
            }
        } else {
            log.warn("Timeout while acquiring serviceLock:{}", lockName);
            // 处理加锁超时：优先使用自定义策略，否则使用注解配置的默认策略
            String customLockTimeoutStrategy = servicelock.customLockTimeoutStrategy();
            // 如果不为空，执行自定义超时处理方法（如返回特定提示、降级处理）
            if (StringUtil.isNotEmpty(customLockTimeoutStrategy)) {
                return handleCustomLockTimeoutStrategy(customLockTimeoutStrategy, joinPoint);
            } else {
                // 执行注解配置的默认超时处理策略（如抛出异常、返回特定提示）
                servicelock.lockTimeoutStrategy().handler(lockName);
            }
            // 根据业务需求决定是否继续执行原方法
            return joinPoint.proceed();
        }
    }

    /**
     * 执行自定义的加锁超时处理策略
     *
     * @param customLockTimeoutStrategy 自定义处理方法的方法名（在被拦截的目标类中定义）
     * @param joinPoint                 连接点对象，用于获取目标方法信息和参数
     * @return 自定义处理方法的返回结果
     */
    public Object handleCustomLockTimeoutStrategy(String customLockTimeoutStrategy, JoinPoint joinPoint) {
        // 获取被拦截的目标方法信息
        Method currentMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // 被拦截的目标对象（业务类实例）
        Object target = joinPoint.getTarget();
        Method handleMethod;
        try {
            // 反射获取目标类中定义的自定义超时处理方法
            handleMethod = target.getClass().getDeclaredMethod(customLockTimeoutStrategy, currentMethod.getParameterTypes());
            // 允许访问私有方法
            handleMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Illegal annotation param customLockTimeoutStrategy :" + customLockTimeoutStrategy, e);
        }
        // 准备方法参数
        Object[] args = joinPoint.getArgs();
        // 反射调用自定义超时处理方法
        Object result;
        try {
            result = handleMethod.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Fail to illegal access custom lock timeout handler: " + customLockTimeoutStrategy, e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Fail to invoke custom lock timeout handler: " + customLockTimeoutStrategy, e);
        }
        return result;
    }
}
