package com.damai.core;

import com.damai.context.DelayQueuePart;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列消费者实现类，负责监听延迟队列中的到期消息，并调度执行消费逻辑
 * 基于双层线程池（监听线程池+执行线程池）实现消息的可靠获取与高效处理
 * @author: 阿星不是程序员
 **/
@Slf4j
public class DelayConsumerQueue extends DelayBaseQueue {

    /**
     * 监听线程计数器，用于生成唯一的监听线程名
     */
    private final AtomicInteger listenStartThreadCount = new AtomicInteger(1);

    /**
     * 任务执行线程计数器，用于生成唯一的任务执行线程名
     */
    private final AtomicInteger executeTaskThreadCount = new AtomicInteger(1);

    /**
     * 监听线程池，负责启动一个持续运行的线程，从延迟队列中阻塞获取到期消息
     */
    private final ThreadPoolExecutor listenStartThreadPool;

    /**
     * 任务执行线程池，负责执行具体的消息消费逻辑
     */
    private final ThreadPoolExecutor executeTaskThreadPool;

    /**
     * 运行状态标识，控制监听线程的启动/停止，确保监听逻辑只被初始化一次
     */
    private final AtomicBoolean runFlag = new AtomicBoolean(false);

    /**
     * 消费任务接口实例，封装具体的业务消费逻辑
     */
    private final ConsumerTask consumerTask;

    /**
     * 构造方法：初始化消费者队列，创建线程池并绑定业务消费任务
     *
     * @param delayQueuePart 延迟队列分区组件
     * @param relTopic       分区队列名称
     */
    public DelayConsumerQueue(DelayQueuePart delayQueuePart, String relTopic) {
        // 调用父类构造方法，初始化Redis阻塞队列
        super(delayQueuePart.getDelayQueueBasePart().getRedissonClient(), relTopic);
        // 初始化监听线程池（单线程：核心线程和最大线程均为1）
        // 启动一个线程持续监听队列，避免频繁创建线程
        this.listenStartThreadPool = new ThreadPoolExecutor(
                1,    // 核心线程数
                1,              // 最大线程数
                60,             // 空闲线程存活时间：60s
                TimeUnit.SECONDS,   // 时间单位：秒
                new LinkedBlockingQueue<>(),  // 任务队列
                // 线程工厂：生成唯一线程名（如"listen-start-thread-1"）
                r -> new Thread(Thread.currentThread().getThreadGroup(),
                        r, "listen-start-thread-" + listenStartThreadCount.getAndIncrement())
        );
        // 初始化任务执行线程池，并发执行消费逻辑，避免单线程处理瓶颈
        this.executeTaskThreadPool = new ThreadPoolExecutor(
                // 核心线程数：从配置获取（如默认4）
                delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getCorePoolSize(),
                // 最大线程数：从配置获取（如默认4）
                delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getMaximumPoolSize(),
                // 空闲线程存活时间：从配置获取（如默认30秒）
                delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getKeepAliveTime(),
                // 时间单位：从配置获取（如默认秒）
                delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getUnit(),
                // 任务队列容量：从配置获取（如默认256）
                new LinkedBlockingQueue<>(delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getWorkQueueSize()),
                // 线程工厂：生成唯一线程名（如"delay-queue-consume-thread-1"）
                r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                        "delay-queue-consume-thread-" + executeTaskThreadCount.getAndIncrement()));
        // 绑定业务消费任务（具体的消息处理逻辑）
        this.consumerTask = delayQueuePart.getConsumerTask();
    }

    /**
     * 启动消息监听逻辑（线程安全，确保只启动一次）
     * 核心逻辑：通过监听线程池启动一个持续运行的线程，阻塞获取到期消息并提交给执行线程池处理
     */
    public synchronized void listenStart() {
        // 若未启动，则执行初始化
        if (!runFlag.get()) {
            runFlag.set(true);  // 标记为已启动，防止重复执行
            // 向监听线程池提交任务：启动一个循环线程，持续获取消息
            listenStartThreadPool.execute(() -> {
                // 循环监听（直到线程被中断）
                while (!Thread.interrupted()) {
                    try {
                        // 从Redis阻塞队列中获取到期消息（blockingQueue由父类初始化，基于Redisson的RBlockingQueue）
                        // take()是阻塞方法：若队列无消息，线程会阻塞等待，直到有消息到来
                        assert blockingQueue != null;
                        String content = blockingQueue.take();
                        // 将消息提交给执行线程池，由业务消费任务处理
                        executeTaskThreadPool.execute(() -> {
                            try {
                                // 调用业务层实现的消费逻辑
                                consumerTask.execute(content);
                            } catch (Exception e) {
                                log.error("consumer execute error", e);
                            }
                        });
                    } catch (InterruptedException e) {
                        // 线程被中断时，销毁执行线程池（释放资源）
                        destroy(executeTaskThreadPool);
                    } catch (Throwable e) {
                        log.error("blockingQueue take error", e);
                    }
                }
            });
        }
    }

    /**
     * 销毁线程池
     *
     * @param executorService 待销毁的线程池
     */
    public void destroy(ExecutorService executorService) {
        try {
            if (Objects.nonNull(executorService)) {
                executorService.shutdown();  // 优雅关闭线程池（等待任务完成后关闭）
            }
        } catch (Exception e) {
            log.error("destroy error", e);
        }
    }
}
