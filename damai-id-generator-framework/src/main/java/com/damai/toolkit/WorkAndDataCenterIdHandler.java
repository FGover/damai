package com.damai.toolkit;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Arrays;
import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: workId和dataCenterId
 * @author: 阿星不是程序员
 **/
@Slf4j
public class WorkAndDataCenterIdHandler {

    // redis中存储工作节点id的key
    private final String SNOWFLAKE_WORK_ID_KEY = "snowflake_work_id";
    // redis中存储数据中心id的key
    private final String SNOWFLAKE_DATA_CENTER_ID_key = "snowflake_data_center_id";
    // redis操作中使用的键列表，包含工作节点id和数据中心的键名
    public final List<String> keys = Arrays.asList(SNOWFLAKE_WORK_ID_KEY, SNOWFLAKE_DATA_CENTER_ID_key);
    // 注入redisTemplate
    private final StringRedisTemplate stringRedisTemplate;
    // redis脚本对象，用于执行Lua脚本
    private DefaultRedisScript<String> redisScript;

    /**
     * 构造函数，初始化redisTemplate和redisScript
     *
     * @param stringRedisTemplate
     */
    public WorkAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        try {
            // 初始化redis脚本对象
            redisScript = new DefaultRedisScript<>();
            // 设置Lua脚本，从类路径下加载指定的Lua脚本
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/workAndDataCenterId.lua")));
            // 设置脚本执行结果类型为String
            redisScript.setResultType(String.class);
        } catch (Exception e) {
            log.error("redisScript init lua error", e);
        }
    }

    /**
     * 获取工作节点id和数据中心的id
     * 通过执行Lua脚本在redis中原子性地获取唯一的id组合
     *
     * @return WorkDataCenterId
     */
    public WorkDataCenterId getWorkAndDataCenterId() {
        WorkDataCenterId workDataCenterId = new WorkDataCenterId();
        try {
            // 准备脚本参数：最大工作节点ID和最大数据中心ID
            Object[] data = new String[2];
            data[0] = String.valueOf(IdGeneratorConstant.MAX_WORKER_ID);
            data[1] = String.valueOf(IdGeneratorConstant.MAX_DATA_CENTER_ID);
            // 执行Lua脚本，获取工作节点id和数据中心的id
            String result = stringRedisTemplate.execute(redisScript, keys, data);
            // 将JSON字符串结果解析为WorkDataCenterId对象
            workDataCenterId = JSON.parseObject(result, WorkDataCenterId.class);
        } catch (Exception e) {
            log.error("getWorkAndDataCenterId error", e);
        }
        return workDataCenterId;
    }
}
