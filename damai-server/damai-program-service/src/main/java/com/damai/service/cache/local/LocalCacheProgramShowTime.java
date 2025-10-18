package com.damai.service.cache.local;

import com.damai.entity.ProgramShowTime;
import com.damai.util.DateUtils;
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
 * @description: 节目演出时间本地缓存组件
 * 基于Caffeine实现高性能本地缓存，用于存储节目演出时间信息，减少分布式缓存和数据库访问
 * @author: 阿星不是程序员
 **/
@Component
public class LocalCacheProgramShowTime {

    /**
     * Caffeine本地缓存实例，键为缓存key（通常是节目ID相关的字符串），值为节目演出时间对象
     */
    private Cache<String, ProgramShowTime> localCache;


    /**
     * 本地缓存的最大容量（可通过配置文件设置，默认10000条）
     * 控制缓存占用的内存大小，避免内存溢出
     */
    @Value("${maximumSize:10000}")
    private Long maximumSize;

    /**
     * 初始化本地缓存配置
     * 标注@PostConstruct，在Bean初始化时执行，完成缓存的参数设置
     */
    @PostConstruct
    public void localLockCacheInit() {
        localCache = Caffeine.newBuilder()
                // 设置缓存最大容量，超过后会根据LRU策略淘汰不常用的条目
                .maximumSize(maximumSize)
                // 自定义过期策略：根据节目演出时间动态设置缓存有效期
                .expireAfter(new Expiry<String, ProgramShowTime>() {
                    /**
                     * 缓存创建时设置过期时间
                     * 有效期 = 从当前时间到节目演出时间的秒数（演出结束后自动失效）
                     * @param key 缓存键
                     * @param value 节目演出时间对象
                     * @param currentTime 当前时间（纳秒）
                     * @return 过期时间（纳秒）
                     */
                    @Override
                    public long expireAfterCreate(@NonNull final String key, @NonNull final ProgramShowTime value,
                                                  final long currentTime) {
                        // 计算当前时间到演出时间的秒数，转换为纳秒作为过期时间
                        return TimeUnit.SECONDS
                                .toNanos(DateUtils.countBetweenSecond(DateUtils.now(), value.getShowTime()));
                    }

                    /**
                     * 缓存更新的过期策略（此处保持原有效期不变）
                     * @param currentDuration 当前剩余有效期（纳秒）
                     * @return 新的有效期（纳秒）
                     */
                    @Override
                    public long expireAfterUpdate(@NonNull final String key, @NonNull final ProgramShowTime value,
                                                  final long currentTime, @NonNegative final long currentDuration) {
                        return currentDuration;
                    }

                    /**
                     * 缓存读取时的过期策略（此处保持原有效期不变）
                     * @param currentDuration 当前剩余有效期（纳秒）
                     * @return 新的有效期（纳秒）
                     */
                    @Override
                    public long expireAfterRead(@NonNull final String key, @NonNull final ProgramShowTime value,
                                                final long currentTime, @NonNegative final long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * 获取缓存，若未命中则通过function加载并缓存
     * Caffeine的get方法是线程安全的，支持并发场景下的缓存加载
     *
     * @param id       缓存键
     * @param function 缓存未命中时的加载函数（通常是查询分布式缓存或数据库）
     * @return 节目演出时间对象
     */
    public ProgramShowTime getCache(String id, Function<String, ProgramShowTime> function) {
        // 从缓存中获取数据，若未命中则通过function加载数据并自动存入本地缓存
        return localCache.get(id, function);
    }

    /**
     * 仅从缓存中获取数据，不触发加载逻辑
     *
     * @param id 缓存键
     * @return 节目演出时间对象（未命中返回null）
     */
    public ProgramShowTime getCache(String id) {
        return localCache.getIfPresent(id);
    }

    /**
     * 删除指定缓存键的条目
     * 用于数据更新后主动清理缓存，保证数据一致性
     *
     * @param id 缓存键
     */
    public void del(String id) {
        localCache.invalidate(id);
    }
}
