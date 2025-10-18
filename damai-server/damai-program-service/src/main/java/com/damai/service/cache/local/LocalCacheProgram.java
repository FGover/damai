package com.damai.service.cache.local;

import com.damai.util.DateUtils;
import com.damai.vo.ProgramVo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目信息的本地缓存组件
 * 基于Caffeine实现高性能本地缓存，用于存储节目演出时间信息，减少分布式缓存和数据库访问
 * @author: 阿星不是程序员
 **/
@Component
public class LocalCacheProgram {

    /**
     * Caffeine本地缓存实例
     * 键为缓存key（通常是节目ID相关的字符串），值为节目详情VO对象（ProgramVo）
     */
    private Cache<String, ProgramVo> localCache;

    /**
     * 本地缓存的最大容量
     * 可通过配置文件动态设置，默认存储10000条节目信息，防止内存溢出
     */
    @Value("${maximumSize:10000}")
    private Long maximumSize;

    /**
     * 初始化本地缓存配置
     * 标注@PostConstruct，在Bean初始化时执行，完成Caffeine缓存的参数配置
     */
    @PostConstruct
    public void localLockCacheInit() {
        localCache = Caffeine.newBuilder()
                // 设置缓存最大容量，超过后会根据LRU（最近最少使用）策略淘汰旧数据
                .maximumSize(maximumSize)
                // 自定义过期策略：根据节目演出时间动态调整缓存有效期
                .expireAfter(new Expiry<String, ProgramVo>() {
                    /**
                     * 缓存创建时设置过期时间
                     * 有效期 = 从当前时间到节目演出时间的毫秒数（演出结束后自动失效）
                     * @param key 缓存键
                     * @param value 节目详情VO对象
                     * @param currentTime 当前时间（纳秒）
                     * @return 过期时间（纳秒）
                     */
                    @Override
                    public long expireAfterCreate(@NonNull final String key, @NonNull final ProgramVo value,
                                                  final long currentTime) {
                        return TimeUnit.MILLISECONDS
                                .toNanos(DateUtils.countBetweenSecond(DateUtils.now(), value.getShowTime()));
                    }

                    /**
                     * 缓存更新时的过期策略（保持当前剩余有效期不变）
                     * @param currentDuration 当前剩余有效期（纳秒）
                     * @return 新的有效期（纳秒）
                     */
                    @Override
                    public long expireAfterUpdate(@NonNull final String key, @NonNull final ProgramVo value,
                                                  final long currentTime, @NonNegative final long currentDuration) {
                        return currentDuration;
                    }

                    /**
                     * 缓存读取时的过期策略（保持当前剩余有效期不变）
                     * @param currentDuration 当前剩余有效期（纳秒）
                     * @return 新的有效期（纳秒）
                     */
                    @Override
                    public long expireAfterRead(@NonNull final String key, @NonNull final ProgramVo value,
                                                final long currentTime, @NonNegative final long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * 获取缓存，未命中则通过函数加载并自动缓存
     * Caffeine的get方法是线程安全的，并发场景下加载函数只会执行一次
     *
     * @param id       缓存键
     * @param function 缓存未命中时的加载函数（通常查询分布式缓存或数据库）
     * @return 节目详情VO对象
     */
    public ProgramVo getCache(String id, Function<String, ProgramVo> function) {
        return localCache.get(id, function);
    }

    public ProgramVo getCache(String id) {
        return localCache.getIfPresent(id);
    }

    public void del(String id) {
        localCache.invalidate(id);
    }
}
