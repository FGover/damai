package com.damai;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.stream.StreamListener;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: redis-stream消息监听器
 * 作为Redis Stream消息的中间处理器，负责接收消息、记录日志并转发给业务消费逻辑
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
public class RedisStreamListener implements StreamListener<String, ObjectRecord<String, String>> {

    /**
     * 业务消息消费者，由业务方实现具体的消息处理逻辑
     */
    private final MessageConsumer messageConsumer;

    /**
     * 接受并处理Redis Stream消息的核心方法
     * 实现StreamListener接口的回调方法，当监听到新消息时自动触发
     *
     * @param message 从Redis Stream接收到的消息对象，包含消息ID、所属流名称和消息内容
     */
    @Override
    public void onMessage(ObjectRecord<String, String> message) {
        try {
            // 1.提取消息元数据和内容
            RecordId messageId = message.getId();   // 消息唯一ID
            String value = message.getValue();  // 消息体内容
            // 2. 记录消费日志，便于追踪消息流转
            log.info("redis stream 消费到了数据 messageId : {}, streamName : {}, message : {}",
                    messageId, message.getStream(), value);
            // 3. 将消息转发给业务消费者处理（解耦框架与业务逻辑）
            messageConsumer.accept(message);
        } catch (Exception e) {
            log.error("onMessage error", e);
        }
    }
}
