package com.damai.service;

import com.damai.captcha.service.CaptchaCacheService;
import lombok.Setter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: redis实现验证码缓存服务实现类
 * 适用于分布式部署环境，通过Redis提供集中式缓存存储，解决多实例间数据共享问题
 * @author: 阿星不是程序员
 **/
@Setter
public class CaptchaCacheServiceRedisImpl implements CaptchaCacheService {

    /**
     * -- SETTER --
     *  设置Redis操作模板对象（依赖注入）
     *  通过Spring容器注入StringRedisTemplate，用于执行Redis操作
     *
     * @param stringRedisTemplate
     */
    // Redis字符串操作模板，用于与Redis服务器交互
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取当前缓存实现的类型标识
     * 用于工厂类识别并选择对应的缓存服务实现
     *
     * @return 缓存类型标识，返回“redis”表示Redis缓存实现
     */
    @Override
    public String type() {
        return "redis";
    }

    /**
     * 向Redis中存储键值对，并设置过期时间
     *
     * @param key              键
     * @param value            值
     * @param expiresInSeconds 过期时间
     */
    @Override
    public void set(String key, String value, long expiresInSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, expiresInSeconds, TimeUnit.SECONDS);
    }

    /**
     * 判断指定键是否存在于Redis中
     * 用于验证用户提交的验证码是否有效
     *
     * @param key 键
     * @return boolean
     */
    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 从Redis中删除指定键值对
     *
     * @param key 键
     */
    @Override
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 从Redis中获取指定键的值
     *
     * @param key 键
     * @return
     */
    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 递增操作
     *
     * @param key 键
     * @param val 值
     * @return
     */
    @Override
    public Long increment(String key, long val) {
        return stringRedisTemplate.opsForValue().increment(key, val);
    }
}