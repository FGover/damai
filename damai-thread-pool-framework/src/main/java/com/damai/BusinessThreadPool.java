package com.damai;


import com.damai.base.BaseThreadPool;
import com.damai.namefactory.BusinessNameThreadFactory;
import com.damai.rejectedexecutionhandler.ThreadPoolRejectedExecutionHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 线程池
 * @author: 阿星不是程序员
 **/

public class BusinessThreadPool extends BaseThreadPool {

    // 线程池执行器实例，静态变量确保全局唯一
    private static ThreadPoolExecutor execute = null;

    // 静态初始化块，在类加载时初始化线程池
    static {
        execute = new ThreadPoolExecutor(
                // 核心线程数：CPU核心数 + 1，确保基本并发处理能力
                Runtime.getRuntime().availableProcessors() + 1,
                // 最大线程数
                maximumPoolSize(),
                // 空闲线程存活时间60s，超过此时间未使用的非核心线程会被回收
                60,
                // 时间单位
                TimeUnit.SECONDS,
                // 任务阻塞队列：使用有界队列，容量600，防止任务过多导致内存溢出
                new ArrayBlockingQueue<>(600),
                // 线程工厂：用于创建具有业务标识的线程
                new BusinessNameThreadFactory(),
                // 拒绝策略：当线程池和队列都满时，采用业务自定义的拒绝处理策略
                new ThreadPoolRejectedExecutionHandler.BusinessAbortPolicy());
    }

    /**
     * 计算线程池的最大线程数
     * 公式：CPU核心数 / 0.2，向上取整（四舍五入）
     * 该公式基于业务场景对CPU利用率的考量，确保充分利用系统资源
     *
     * @return 最大线程数
     */
    private static Integer maximumPoolSize() {
        return new BigDecimal(Runtime.getRuntime().availableProcessors())
                .divide(new BigDecimal("0.2"), 0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 提交Runnable任务到线程池执行
     * 对任务进行包装，添加上下文信息（从父类获取）
     *
     * @param r 待执行的任务
     */
    public static void execute(Runnable r) {
        // 调用父类的方法包装任务，添加上下文信息
        execute.execute(wrapTask(r, getContextForTask(), getContextForHold()));
    }

    /**
     * 提交Callable任务到线程池执行，并返回Future对象
     * 可通过Future获取任务执行结果或取消任务
     *
     * @param c   待执行的Callable任务
     * @param <T> 任务返回值类型
     * @return Future对象
     */
    public static <T> Future<T> submit(Callable<T> c) {
        // 包装任务并提交，添加上下文信息
        return execute.submit(wrapTask(c, getContextForTask(), getContextForHold()));
    }
}
