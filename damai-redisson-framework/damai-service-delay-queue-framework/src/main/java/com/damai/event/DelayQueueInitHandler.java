package com.damai.event;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.context.DelayQueueBasePart;
import com.damai.context.DelayQueuePart;
import com.damai.core.ConsumerTask;
import com.damai.core.DelayConsumerQueue;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

import java.util.Map;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟队列初始化处理器，负责在应用启动后初始化所有延迟队列的消费者
 * 监听应用启动事件，自动注册消息消费逻辑，确保延迟队列能正常接收和处理到期消息
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class DelayQueueInitHandler implements ApplicationListener<ApplicationStartedEvent> {

    // 延迟队列基础组件
    private final DelayQueueBasePart delayQueueBasePart;

    /**
     * 应用启动完成后触发，初始化延迟队列消费者
     *
     * @param event 应用启动事件，包含Spring上下文信息
     */
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        // 从Spring容器中获取所有实现了ConsumerTask接口的Bean（即所有延迟消息的消费任务）
        Map<String, ConsumerTask> consumerTaskMap = event.getApplicationContext().getBeansOfType(ConsumerTask.class);
        // 如果没有任何消费任务，则直接返回
        if (CollectionUtil.isEmpty(consumerTaskMap)) {
            return;
        }
        // 遍历所有消费任务，为每个任务创建对应的延迟队列消费者
        for (ConsumerTask consumerTask : consumerTaskMap.values()) {
            // 创建延迟队列分区组件，绑定基础组件和当前消费任务
            DelayQueuePart delayQueuePart = new DelayQueuePart(delayQueueBasePart, consumerTask);
            // 获取配置的隔离分区数（从DelayQueueProperties中读取，默认5）
            Integer isolationRegionCount = delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties()
                    .getIsolationRegionCount();
            // 为每个分区创建消费者队列，并启动监听
            // 分区机制：将同一主题的消息分散到多个分区，减少Redis队列的竞争，提高并发处理能力
            for (int i = 0; i < isolationRegionCount; i++) {
                // 构建分区队列名称：主题名 + 分区索引（如"order_timeout-0"、"order_timeout-1"）
                DelayConsumerQueue delayConsumerQueue = new DelayConsumerQueue(delayQueuePart,
                        delayQueuePart.getConsumerTask().topic() + "-" + i);
                // 启动消费者监听：开始从延迟队列中获取到期消息，并调用ConsumerTask的逻辑处理
                delayConsumerQueue.listenStart();
            }
        }
    }
}
