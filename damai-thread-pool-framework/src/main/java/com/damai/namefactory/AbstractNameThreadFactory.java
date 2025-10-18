package com.damai.namefactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 线程工厂抽象基类
 * @author: 阿星不是程序员
 **/
public abstract class AbstractNameThreadFactory implements ThreadFactory {

    /**
     * 静态原子计数器，用于记录线程池的数量
     * 采用AtomicLong保证多线程环境下的计数安全性
     */
    protected static final AtomicLong POOL_NUM = new AtomicLong(1);

    /**
     * 线程组，用于管理当前工厂创建的所有线程
     * 线程组提供线程的统一管理
     */
    private final ThreadGroup group;

    /**
     * 原子计数器：用于记录当前线程池中创建的线程数量
     * 每个线程工厂实例独立计数，确保线程名唯一
     */
    private final AtomicLong threadNum = new AtomicLong(1);

    /**
     * 线程名称前缀，由子类实现的getNamePrefix方法返回
     */
    private String namePrefix = "";


    /**
     * 构造方法，初始化线程组和线程名称前缀
     * 线程组优先从安全管理器获取，否则使用当前线程的线程组
     */
    public AbstractNameThreadFactory() {
        // 获取安全管理器
        SecurityManager s = System.getSecurityManager();
        // 确定线程组：有安全管理器则用其指定的线程组，否则用当前线程的线程组
        group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
        // 初始化线程名前缀，格式由子类定义的前缀 + "--thread--"组成
        namePrefix = getNamePrefix() + "--thread--";
    }

    /**
     * 子类实现获取线程池名称的前缀
     *
     * @return String
     */
    public abstract String getNamePrefix();

    /**
     * 将线程池工厂中设置线程名进行重写
     * 例子:子类重写的namePrefix--thread--2(每个线程池中线程的数量)
     */
    @Override
    public Thread newThread(Runnable r) {
        // 生成线程名称：前缀 + 自增序号（确保每个线程名唯一）
        String name = namePrefix + threadNum.getAndIncrement();
        // 创建线程实例：指定线程组、任务、名称和栈大小（0表示使用默认栈大小）
        Thread t = new Thread(group, r, name, 0);
        // 确保线程不是守护线程（守护线程会随主线程退出而终止，不适合业务处理）
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        // 确保线程优先级为正常优先级（避免线程优先级过高/过低导致的调度问题）
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}
