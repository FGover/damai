package com.damai.config;


import com.damai.context.DelayQueueBasePart;
import com.damai.context.DelayQueueContext;
import com.damai.event.DelayQueueInitHandler;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列自动配置类，负责初始化分布式延迟队列
 * 基于Redisson实现，支撑订单超时取消、库存延迟回补等延迟任务场景
 * @author: 阿星不是程序员
 **/
@EnableConfigurationProperties(DelayQueueProperties.class)
public class DelayQueueAutoConfig {

    /**
     * 注册延迟队列基础组件
     * 封装Redisson分布式延迟队列的核心操作（如发送延迟消息、监听队列）
     *
     * @param redissonClient       Redisson客户端，用于操作Redis分布式队列
     * @param delayQueueProperties 延迟队列配置属性（从配置文件读取的参数）
     * @return 延迟队列基础组件实例
     */
    @Bean
    public DelayQueueBasePart delayQueueBasePart(RedissonClient redissonClient, DelayQueueProperties delayQueueProperties) {
        return new DelayQueueBasePart(redissonClient, delayQueueProperties);
    }

    /**
     * 注册延迟队列初始化处理器
     * 项目启动时初始化延迟队列的消息消费者，确保到期消息能被自动处理
     *
     * @param delayQueueBasePart 延迟队列基础组件
     * @return 延迟队列初始化处理器实例
     */
    @Bean
    public DelayQueueInitHandler delayQueueInitHandler(DelayQueueBasePart delayQueueBasePart) {
        return new DelayQueueInitHandler(delayQueueBasePart);
    }

    /**
     * 注册延迟队列上下文
     *
     * @param delayQueueBasePart 延迟队列基础组件
     * @return 延迟队列上下文实例
     */
    @Bean
    public DelayQueueContext delayQueueContext(DelayQueueBasePart delayQueueBasePart) {
        return new DelayQueueContext(delayQueueBasePart);
    }
}
