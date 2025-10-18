package com.damai.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: kafka 生产者配置，负责初始化Kafka相关的Bean，根据配置条件决定是否启用Kafka消息发送功能
 * @author: 阿星不是程序员
 **/
// 条件注解：仅当配置文件中存在"spring.kafka.bootstrap-servers"配置时，才加载该配置类
@ConditionalOnProperty(value = "spring.kafka.bootstrap-servers")
public class ProducerConfig {

    /**
     * 创建Kafka主题配置对象
     * 用于从配置文件中读取Kafka主题名称等信息
     *
     * @return KafkaTopic对象，封装了Kafka主题相关配置
     */
    @Bean
    public KafkaTopic kafkaTopic() {
        return new KafkaTopic();
    }

    /**
     * 创建接口数据消息发送器Bean
     * 注入Kafka模板和主题配置，构建ApiDataMessageSend实例
     *
     * @param kafkaTemplate Kafka操作模板，提供消息发送能力
     * @param kafkaTopic    主题配置对象，包含要发送的目标主题名称
     * @return ApiDataMessageSend实例，用于发送接口相关数据到Kafka
     */
    @Bean
    public ApiDataMessageSend apiDataMessageSend(KafkaTemplate<String, String> kafkaTemplate, KafkaTopic kafkaTopic) {
        // 传入Kafka模板和从配置中获取的主题名称，初始化消息发送器
        return new ApiDataMessageSend(kafkaTemplate, kafkaTopic.getTopic());
    }
}
