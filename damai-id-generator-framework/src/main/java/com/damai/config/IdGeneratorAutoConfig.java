package com.damai.config;

import com.damai.toolkit.SnowflakeIdGenerator;
import com.damai.toolkit.WorkAndDataCenterIdHandler;
import com.damai.toolkit.WorkDataCenterId;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分布式id生成器自动配置类，负责配置分布式环境下生成唯一id所需的相关Bean
 * @author: 阿星不是程序员
 **/
public class IdGeneratorAutoConfig {

    /**
     * 创建并配置工作节点与数据中心id处理器Bean
     *
     * @param stringRedisTemplate
     * @return
     */
    @Bean
    public WorkAndDataCenterIdHandler workAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate) {
        return new WorkAndDataCenterIdHandler(stringRedisTemplate);
    }

    /**
     * 创建并配置工作节点与数据中心id Bean
     * 该Bean封装了当前服务实例的工作节点ID和数据中心ID
     *
     * @param workAndDataCenterIdHandler
     * @return
     */
    @Bean
    public WorkDataCenterId workDataCenterId(WorkAndDataCenterIdHandler workAndDataCenterIdHandler) {
        return workAndDataCenterIdHandler.getWorkAndDataCenterId();
    }

    /**
     * 创建并配置雪花算法id生成器Bean
     * 基于雪花算法(Snowflake)生成分布式环境下的全局唯一ID
     *
     * @param workDataCenterId
     * @return
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(WorkDataCenterId workDataCenterId) {
        return new SnowflakeIdGenerator(workDataCenterId);
    }
}
