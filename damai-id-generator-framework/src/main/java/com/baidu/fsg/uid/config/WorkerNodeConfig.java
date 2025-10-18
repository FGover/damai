package com.baidu.fsg.uid.config;

import com.baidu.fsg.uid.UidGenerator;
import com.baidu.fsg.uid.impl.CachedUidGenerator;
import com.baidu.fsg.uid.worker.WorkerIdAssigner;
import com.damai.toolkit.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 百度uid集成配置类
 * 用于将百度UidGenerator框架与自定义雪花算法生成器整合，实现高并发场景下的高性能、高可用分布式ID生成
 * @author: 阿星不是程序员
 **/
@Configuration
public class WorkerNodeConfig {

    /**
     * 定义名为"cachedUidGenerator"的UidGenerator Bean，作为分布式ID生成的核心组件
     * 该Bean整合了百度CachedUidGenerator的缓存预生成能力与自定义雪花算法的ID生成逻辑
     *
     * @param disposableWorkerIdAssigner WorkerId分配器，用于为当前节点分配唯一的工作节点ID
     *                                   （通常基于Redis等分布式存储实现，保证集群中节点ID不重复）
     * @param snowflakeIdGenerator       自定义的雪花算法ID生成器，提供核心的ID生成逻辑
     *                                   （封装了时间戳、机器ID、序列号等雪花算法关键逻辑）
     * @return 配置完成的CachedUidGenerator实例，可直接注入到业务代码中用于生成分布式ID
     */
    @Bean("cachedUidGenerator")
    public UidGenerator uidGenerator(WorkerIdAssigner disposableWorkerIdAssigner,
                                     SnowflakeIdGenerator snowflakeIdGenerator) {
        // 实例化百度的CachedUidGenerator，它是带缓存的ID生成器实现，用于提升高并发下的性能
        CachedUidGenerator cachedUidGenerator = new CachedUidGenerator();
        // 为CachedUidGenerator设置WorkId分配器，用于获取当前节点的唯一标识（workerId）
        // 确保集群中每个节点的workerId不重复，是分布式ID唯一性的基础
        cachedUidGenerator.setWorkerIdAssigner(disposableWorkerIdAssigner);
        // 将自定义的雪花算法生成器注入到CachedUidGenerator中，让缓存框架底层使用自定义的雪花逻辑生成ID
        // 这样既保留了自定义雪花算法的业务适配性，又利用了CachedUidGenerator的缓存预生成能力
        cachedUidGenerator.setSnowflakeIdGenerator(snowflakeIdGenerator);
        // 返回配置好的CachedUidGenerator实例，作为Spring Bean供全局使用
        return cachedUidGenerator;
    }
}