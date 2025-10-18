package com.damai.util;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式锁 方法类型执行 无返回值的业务
 * @author: 阿星不是程序员
 **/
@FunctionalInterface
public interface TaskRun {

    /**
     * 执行任务
     * TaskRun 虽然只是一个接口（没有显式的实现类），但在实际使用时，会通过 Lambda 表达式、方法引用或匿名内部类 动态生成它的实现，从而完成调用
     */
    void run();
}
