package com.damai.kafka;

import com.alibaba.fastjson.JSON;
import com.damai.entity.ApiData;
import com.damai.service.ApiDataService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.damai.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 接口数据 Kafka 消息消费者
 * 负责消费 Kafka 中接口相关的消息（如限流日志），并将其持久化到数据库，用于后续的数据分析、监控统计或风控审计
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
@Component
public class ApiDataMessageConsumer {

    /**
     * 接口数据服务类，用于将消费的消息数据保存到数据库
     */
    @Autowired
    private ApiDataService apiDataService;

    /**
     * 消费 Kafka 中的接口数据消息（如限流日志）
     * 通过 @KafkaListener 注解指定监听的主题，支持动态拼接主题名称（环境前缀 + 配置的主题名）
     *
     * @param consumerRecord Kafka 消费记录对象，包含消息的键（key）和值（value）
     */
    @KafkaListener(topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + "${spring.kafka.topic:save_api_data}"})
    public void consumerOrderMessage(ConsumerRecord<String, String> consumerRecord) {
        try {
            // 安全处理消息内容：判断消息值是否存在，避免空指针异常
            Optional.ofNullable(consumerRecord.value())
                    .map(String::valueOf)  // 确保消息值为字符串类型
                    .ifPresent(value -> {
                        // 打印消费的消息内容，便于调试和日志追踪
                        log.info("consumerOrderMessage message:{}", value);
                        // 将 JSON 格式的消息内容解析为 ApiData 实体对象
                        ApiData apiData = JSON.parseObject(value, ApiData.class);
                        // 调用服务层方法将接口数据保存到数据库
                        apiDataService.saveApiData(apiData);
                    });
        } catch (Exception e) {// 捕获并记录消费过程中的异常，避免单个消息处理失败导致消费者阻塞

            log.error("consumerApiDataMessage error", e);
        }
    }
}
