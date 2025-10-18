package com.damai.kafka;

import com.damai.core.SpringUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 接口数据 Kafka 消息发送器，负责将接口相关数据（如限流日志、请求记录等）封装并发送到指定 Kafka 主题，
 * 用于后续的日志分析、监控告警或数据统计
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
public class ApiDataMessageSend {

    /**
     * Kafka 模板工具类，提供发送消息到 Kafka 集群的能力
     */
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 消息要发送到的 Kafka 主题名称（基础主题名，会结合前缀生成最终主题）
     */
    private String topic;

    /**
     * 发送消息到 Kafka 指定主题
     *
     * @param message 要发送的消息内容（通常为 JSON 字符串，如限流日志的 ApiDataDto 对象）
     */
    public void sendMessage(String message) {
        log.info("sendMessage message : {}", message);
        // 发送消息到 Kafka：使用 KafkaTemplate 发送，键类型为 String，值为消息内容
        kafkaTemplate.send(SpringUtil.getPrefixDistinctionName() + "-" + topic, message);
    }
}
