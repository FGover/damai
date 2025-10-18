package com.damai.context;

import com.damai.config.DelayQueueProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.redisson.api.RedissonClient;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列配置信息
 * @author: 阿星不是程序员
 **/
@Data
@AllArgsConstructor
public class DelayQueueBasePart {

    /**
     * Redisson客户端实例
     * 用于操作Redis的分布式延迟队列（RDelayedQueue），提供消息的存储、延迟、获取等底层能力
     * 是实现分布式延迟队列的核心依赖
     */
    private final RedissonClient redissonClient;

    /**
     * 延迟队列配置属性对象
     * 包含线程池参数（如核心线程数）、分区数等配置，用于定制延迟队列的行为
     * 配置参数从application.yml等配置文件中读取
     */
    private final DelayQueueProperties delayQueueProperties;
}
