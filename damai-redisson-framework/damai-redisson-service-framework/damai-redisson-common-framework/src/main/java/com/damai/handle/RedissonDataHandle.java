package com.damai.handle;

import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: Redisson操作工具类
 * 封装Redisson客户端的常用操作方法，简化Redis字符串类型数据的读写及过期时间设置
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class RedissonDataHandle {

    /**
     * Redisson客户端实例，用于与redis服务器交互
     */
    private final RedissonClient redissonClient;

    /**
     * 从Redis中获取指定键的值
     *
     * @param key 要获取的键名
     * @return 键对应的字符串值（若键不存在则返回null）
     */
    public String get(String key) {
        return (String) redissonClient.getBucket(key).get();
    }

    /**
     * 向Redis中设置键值对（无过期时间，永久有效）
     *
     * @param key   要设置的键名
     * @param value 要设置的字符串值
     */
    public void set(String key, String value) {
        // 通过Redisson的Bucket对象设置值，默认无过期时间
        redissonClient.getBucket(key).set(value);
    }

    /**
     * 向Redis中设置键值对并指定过期时间
     *
     * @param key        要设置的键名
     * @param value      要设置的字符串值
     * @param timeToLive 过期时间长度
     * @param timeUnit   过期时间单位（如秒、分钟、小时等）
     */
    public void set(String key, String value, long timeToLive, TimeUnit timeUnit) {
        // 先将时间单位转换为Duration，再设置带过期时间的键值对
        redissonClient.getBucket(key).set(value, getDuration(timeToLive, timeUnit));
    }

    /**
     * 将时间长度和单位转换为Duration对象
     * 适配Redisson设置过期时间时对Duration类型的要求
     *
     * @param timeToLive 时间长度
     * @param timeUnit   时间单位
     * @return 转换后的Duration对象
     */
    public Duration getDuration(long timeToLive, TimeUnit timeUnit) {
        // 根据不同的时间单位，生成对应的Duration实例
        switch (timeUnit) {
            // 分钟
            case MINUTES -> {
                return Duration.ofMinutes(timeToLive);
            }
            // 小时
            case HOURS -> {
                return Duration.ofHours(timeToLive);
            }
            // 天
            case DAYS -> {
                return Duration.ofDays(timeToLive);
            }
            // 默认处理秒级单位（包含SECONDS及未明确指定的单位）
            default -> {
                return Duration.ofSeconds(timeToLive);
            }
        }
    }
}
