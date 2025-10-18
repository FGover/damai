package com.damai;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: redis-stream核心操作处理器
 * 封装消费组创建、流初始化、消息删除等核心功能，负责Redis Stream的基础管理与操作
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
public class RedisStreamHandler {

    /**
     * 消息推送处理器
     */
    private final RedisStreamPushHandler redisStreamPushHandler;
    /**
     * Redis操作模板
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 为指定Stream创建消费组
     * 注意：仅当Stream已存在时才能创建消费组
     *
     * @param streamName Stream名称（Redis key）
     * @param groupName  消费组名称
     */
    public void addGroup(String streamName, String groupName) {
        stringRedisTemplate.opsForStream().createGroup(streamName, groupName);
    }

    /**
     * 判断指定Stream是否存在
     *
     * @param key Stream名称（Redis key）
     * @return boolean 存在返回true，否则返回false
     */
    public Boolean hasKey(String key) {
        if (Objects.isNull(key)) {
            return false;
        } else {
            return stringRedisTemplate.hasKey(key);
        }

    }

    /**
     * 从Stream中删除指定消息
     *
     * @param key       Stream名称（Redis key）
     * @param recordIds 要删除的消息ID
     */
    public void del(String key, RecordId recordIds) {
        stringRedisTemplate.opsForStream().delete(key, recordIds);
    }

    /**
     * 初始化Stream与消费组的绑定关系
     * 若Stream不存在，则先创建Stream并初始化消费组，再清理初始化消息
     *
     * @param streamName Stream名称
     * @param group      消费组名称
     */
    public void streamBindingGroup(String streamName, String group) {
        // 1. 检查Stream是否已存在
        boolean hasKey = hasKey(streamName);
        // 2. 若Stream不存在，先发送一条临时消息创建Stream（Redis Stream在首次添加消息时自动创建）
        if (!hasKey) {
            Map<String, Object> map = new HashMap<>(2);
            map.put("key", "value"); // 临时消息内容（无实际业务意义）
            RecordId recordId = redisStreamPushHandler.push(JSON.toJSONString(map));
            // 3. 为新创建的Stream创建消费组
            addGroup(streamName, group);
            // 4. 删除临时消息（避免干扰业务消费）
            del(streamName, recordId);
            // 5. 记录初始化日志
            log.info("initStream streamName : {} group : {}", streamName, group);
        }
    }
}
