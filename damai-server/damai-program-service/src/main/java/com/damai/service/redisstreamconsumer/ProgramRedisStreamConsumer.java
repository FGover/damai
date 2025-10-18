package com.damai.service.redisstreamconsumer;

import com.damai.MessageConsumer;
import com.damai.service.ProgramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目相关的Redis Stream消息消费者
 * 负责处理节目状态变更（如失效）时的消息，清理本地缓存以保证数据一致性
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramRedisStreamConsumer implements MessageConsumer {

    @Autowired
    private ProgramService programService;

    /**
     * 处理接收到的节目相关消息
     * 当监听到节目状态变更消息时，触发本地缓存清理操作
     *
     * @param message 从Redis Stream接收到的消息对象，消息体为节目ID（字符串格式）
     */
    @Override
    public void accept(ObjectRecord<String, String> message) {
        // 1. 解析消息体，将字符串格式的节目ID转换为Long类型
        Long programId = Long.parseLong(message.getValue());
        // 2. 调用节目服务的方法，清理该节目在本地缓存中的数据
        programService.delLocalCache(programId);
    }
}
