package com.damai.service.lua;

import com.damai.initialize.base.AbstractApplicationPostConstructHandler;
import com.damai.redis.RedisCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 基于Lua脚本的验证码触发判断操作类
 * 负责加载并执行Lua脚本，通过redis原子操作判断用户是否需要验证验证码
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class CheckNeedCaptchaOperate extends AbstractApplicationPostConstructHandler {

    @Autowired
    private RedisCache redisCache;

    private DefaultRedisScript<String> redisScript;

    /**
     * 指定初始化执行顺序
     *
     * @return
     */
    @Override
    public Integer executeOrder() {
        return 1;
    }

    /**
     * 应用初始化时加载Lua脚本
     * 在Spring容器初始化完成后执行，确保Lua脚本在服务启动时就准备就绪
     *
     * @param context Spring应用上下文对象，可用于获取容器中的Bean或配置信息
     */
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        try {
            // 初始化Redis脚本对象
            redisScript = new DefaultRedisScript<>();
            // 加载classpath下的Lua脚本文件（路径：resources/lua/checkNeedCaptcha.lua）
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/checkNeedCaptcha.lua")));
            // 指定脚本执行结果的类型为String（后续转换为Boolean）
            redisScript.setResultType(String.class);
        } catch (Exception e) {
            log.error("redisScript init lua error", e);
        }
    }

    /**
     * 执行Lua脚本判断是否需要验证码，通过Redis执行原子操作，避免高并发下的计数混乱
     *
     * @param keys Redis键列表（用于脚本中的键参数，如计数器键、时间戳键等）
     * @param args 脚本参数数组（如阈值、当前时间戳、过期时间等）
     * @return Boolean 是否需要验证验证码（true-需要，false-不需要）
     */
    public Boolean checkNeedCaptchaOperate(List<String> keys, String[] args) {
        Object object = redisCache.getInstance().execute(redisScript, keys, args);
        return Boolean.parseBoolean((String) object);
    }
}
