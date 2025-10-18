package com.baidu.fsg.uid.config;

import com.baidu.fsg.uid.worker.WorkerIdAssigner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @program: cook-frame
 * @description: 对百度开源id生成器进行redis适配
 * 用于创建Redis相关组件，支持分布式环境下的WorkerId分配
 * @author: 阿星不是程序员
 * @create: 2023-05-23
 * @see <a href="https://github.com/baidu/uid-generator/">百度开源id生成器</a>
 **/
@Configuration(proxyBeanMethods = false)
// 条件注解：仅当配置文件中存在"spring.data.redis.host"属性时，才加载该配置类
// 用于控制Redis相关Bean的创建时机，避免无Redis配置时的错误
@ConditionalOnProperty("spring.data.redis.host")
public class IdGeneratorRedisConfig {

    /**
     * 创建专用于ID生成器的RedisTemplate Bean
     * 命名为"idGeneratorRedisTemplate"，与其他业务的RedisTemplate区分
     *
     * @param redisConnectionFactory Redis连接工厂，由Spring自动配置提供
     * @return 配置好的RedisTemplate实例
     */
    @Bean("idGeneratorRedisTemplate")
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate redisTemplate = new RedisTemplate();
        // 设置默认序列化器为StringRedisSerializer，避免Redis中存储的键值出现乱码
        // 确保WorkerId相关的Redis操作（如自增）使用字符串格式正常交互
        redisTemplate.setDefaultSerializer(new StringRedisSerializer());
        // 关联Redis连接工厂，获取Redis连接资源
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }

    /**
     * 创建基于Redis的WorkerId分配器Bean，命名为"disposableWorkerIdAssigner"
     * 实现百度uid-generator框架的WorkerIdAssigner接口，提供分布式环境下的唯一ID分配能力
     *
     * @param redisTemplate 注入专用于ID生成器的RedisTemplate（通过@Qualifier指定名称）
     * @return RedisDisposableWorkerIdAssigner实例，用于分配WorkerId
     */
    @Bean("disposableWorkerIdAssigner")
    public WorkerIdAssigner redisDisposableWorkerIdAssigner(@Qualifier("idGeneratorRedisTemplate") RedisTemplate redisTemplate) {
        // 实例化RedisDisposableWorkerIdAssigner，传入专用的RedisTemplate
        return new RedisDisposableWorkerIdAssigner(redisTemplate);
    }
}
