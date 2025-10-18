package com.damai.context;

import com.damai.core.ConsumerTask;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列分区组件，关联延迟队列基础资源与具体业务消费任务
 * @author: 阿星不是程序员
 **/
@Data
public class DelayQueuePart {

    /**
     * 延迟队列基础组件
     * 包含Redis客户端和全局配置参数，提供底层队列操作能力（如消息存储、读取）
     */
    private final DelayQueueBasePart delayQueueBasePart;

    /**
     * 消费任务接口实例
     * 对应具体的业务消费逻辑（如订单超时取消、库存延迟回补），由业务层实现
     * 封装了消息主题（topic）和消息处理方法
     */
    private final ConsumerTask consumerTask;

    /**
     * 构造方法，关联基础组件与消费任务
     *
     * @param delayQueueBasePart 延迟队列基础资源（Redis客户端+配置）
     * @param consumerTask       具体业务的消费逻辑
     */
    public DelayQueuePart(DelayQueueBasePart delayQueueBasePart, ConsumerTask consumerTask) {
        this.delayQueueBasePart = delayQueueBasePart;
        this.consumerTask = consumerTask;
    }
}
