package com.damai;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: redis-stream发送消息器
 * 负责将消息封装为Stream格式并发送到指定的Redis Stream中，是消息生产方与Redis Stream之间的桥梁
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
public class RedisStreamPushHandler {

    /**
     * Redis操作模板
     */
    private final StringRedisTemplate stringRedisTemplate;
    /**
     * Redis Stream配置属性
     */
    private final RedisStreamConfigProperties redisStreamConfigProperties;

    /**
     * 向Redis Stream发送消息
     *
     * @param msg 要发送的消息内容（字符串格式）
     * @return RecordId 消息在Stream中的唯一标识（由Redis自动生成）
     */
    public RecordId push(String msg) {
        // 1.构建Stream消息记录
        // - 指定消息所属的Stream（从配置属性中获取流名称）
        // - 设置消息内容（字符串类型）
        // - 自动生成消息ID（格式：时间戳-序列号，确保唯一性和时序性）
        ObjectRecord<String, String> record = StreamRecords.newRecord()
                .in(redisStreamConfigProperties.getStreamName())  // 关联到目标Stream
                .ofObject(msg)  // 设置消息体内容
                .withId(RecordId.autoGenerate());   // 自动生成消息ID
        // 2.发送消息到Redis Stream，并获取消息ID
        RecordId recordId = this.stringRedisTemplate.opsForStream().add(record);
        // 3.记录日志
        log.info("redis streamName : {} message : {}", redisStreamConfigProperties.getStreamName(), msg);
        // 4.返回消息ID
        return recordId;
    }
}
