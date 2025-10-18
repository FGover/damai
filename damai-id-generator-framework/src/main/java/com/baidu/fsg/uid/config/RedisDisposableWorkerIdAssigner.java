package com.baidu.fsg.uid.config;

import com.baidu.fsg.uid.worker.WorkerIdAssigner;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 基于Redis实现的WorkerId分配器，用于在分布式环境中为每个服务节点分配唯一的工作节点ID
 * @author: 阿星不是程序员
 **/
public class RedisDisposableWorkerIdAssigner implements WorkerIdAssigner {

    // Redis模板对象，用于操作Redis进行ID分配
    private RedisTemplate redisTemplate;

    /**
     * 构造方法：注入RedisTemplate依赖
     *
     * @param redisTemplate
     */
    public RedisDisposableWorkerIdAssigner(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 分配工作节点ID（workerId）的核心方法
     * 实现百度UidGenerator框架的WorkerIdAssigner接口，提供分布式环境下的唯一ID分配逻辑
     *
     * @return 分配给当前节点的唯一workerId（长整型）
     */
    @Override
    public long assignWorkerId() {
        // Redis中存储workerId的键名，用于标识自增计数器
        String key = "uid_work_id";
        // 调用Redis的自增命令（原子操作），确保在分布式环境下每次调用生成唯一的递增ID
        // 该操作是线程安全的，即使多个节点同时请求也能保证ID不重复
        Long increment = redisTemplate.opsForValue().increment(key);
        // 使用Optional处理可能的null值：如果自增结果为null（如Redis操作失败），则抛出自定义异常
        // 异常携带BaseCode.UID_WORK_ID_ERROR错误码，便于上层统一处理ID分配失败的场景
        return Optional.ofNullable(increment).orElseThrow(() -> new DaMaiFrameException(BaseCode.UID_WORK_ID_ERROR));
    }
}
