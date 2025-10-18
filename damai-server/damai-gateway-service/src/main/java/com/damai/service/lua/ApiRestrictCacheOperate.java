package com.damai.service.lua;

import com.alibaba.fastjson.JSON;
import com.damai.redis.RedisCache;
import com.damai.service.ApiRestrictData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 接口限流Lua脚本执行器，负责加载并执行限流相关的Lua脚本，通过Redis实现分布式环境下的原子性限流判断
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ApiRestrictCacheOperate {

    @Autowired
    private RedisCache redisCache;

    // redis脚本对象，封装限流逻辑的Lua脚本
    private DefaultRedisScript<String> redisScript;

    /**
     * 初始化方法：在对象创建后加载Lua脚本
     * 被@PostConstruct注解修饰，会在构造方法执行后、依赖注入完成时自动调用
     */
    @PostConstruct
    public void init() {
        try {
            // 初始化redis脚本对象
            redisScript = new DefaultRedisScript<>();
            // 从类路径下加载lua文件
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/apiLimit.lua")));
            // 设置脚本执行结果的类型为String（返回JSON格式的字符串）
            redisScript.setResultType(String.class);
        } catch (Exception e) {
            // 记录脚本加载失败的日志，避免初始化异常导致整个服务启动失败
            log.error("redisScript init lua error", e);
        }
    }

    /**
     * 执行限流规则的Lua脚本，实现原子性的限流判断
     *
     * @param keys Lua脚本中使用的键列表（通常用于指定Redis中的键名）
     * @param args Lua脚本的参数数组（通常用于传递限流阈值、时间窗口等配置）
     * @return 限流判断结果封装对象（包含是否触发限流、请求次数等信息）
     */
    public ApiRestrictData apiRuleOperate(List<String> keys, Object[] args) {
        // 调用redis执行Lua脚本，获取执行结果（JSON字符串）
        // redisCache.getInstance()获取原生RedisTemplate，execute方法执行脚本
        Object object = redisCache.getInstance().execute(redisScript, keys, args);
        // 将JSON字符串结果解析为ApiRestrictData对象并返回
        return JSON.parseObject((String) object, ApiRestrictData.class);
    }
}
