package com.damai.handler;


import com.damai.config.BloomFilterProperties;
import com.damai.core.SpringUtil;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;


/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 布隆过滤器处理器
 * 基于Redisson实现分布式布隆过滤器，用于解决缓存穿透问题
 * 提供数据添加、存在性判断等核心操作，适配分布式环境下的高效查询
 * @author: 阿星不是程序员
 **/
public class BloomFilterHandler {

    /**
     * Redisson提供的布隆过滤器实例，用于存储数据并执行高效的存在性判断
     * 泛型为String，表示存储的数据类型为字符串（如节目ID的字符串形式）
     */
    private final RBloomFilter<String> cachePenetrationBloomFilter;

    /**
     * 构造方法：初始化布隆过滤器
     * 从配置中读取参数，创建或获取Redisson布隆过滤器实例
     *
     * @param redissonClient        Redisson客户端，用于操作Redis中的布隆过滤器
     * @param bloomFilterProperties 布隆过滤器配置属性（包含预期插入量、误判率等）
     */
    public BloomFilterHandler(RedissonClient redissonClient, BloomFilterProperties bloomFilterProperties) {
        // 区分不同环境的布隆过滤器，避免冲突
        RBloomFilter<String> cachePenetrationBloomFilter = redissonClient.getBloomFilter(
                SpringUtil.getPrefixDistinctionName() + "-" + bloomFilterProperties.getName());
        // 初始化布隆过滤器（若不存在则创建）
        cachePenetrationBloomFilter.tryInit(
                bloomFilterProperties.getExpectedInsertions(),  // 预期插入的数据量（影响底层位数组大小）
                bloomFilterProperties.getFalseProbability()   // 可接受的误判概率（值越小，需要的位数组越大，内存占用越高）
        );
        this.cachePenetrationBloomFilter = cachePenetrationBloomFilter;
    }

    /**
     * 向布隆过滤器添加数据
     * 数据会通过多个哈希函数映射到位数组，标记为"存在"
     *
     * @param data 待添加的数据（字符串类型，如节目ID的字符串形式）
     * @return 若数据是首次添加返回true，否则返回false（Redisson实现特性）
     */
    public boolean add(String data) {
        return cachePenetrationBloomFilter.add(data);
    }

    /**
     * 判断数据是否可能存在于布隆过滤器中
     * 存在两种结果：
     * 1. 返回false：数据一定不存在（100%准确）
     * 2. 返回true：数据可能存在（存在一定误判率，由初始化参数决定）
     *
     * @param data 待判断的数据（字符串类型）
     * @return 是否可能存在的判断结果
     */
    public boolean contains(String data) {
        return cachePenetrationBloomFilter.contains(data);
    }

    public long getExpectedInsertions() {
        return cachePenetrationBloomFilter.getExpectedInsertions();
    }

    public double getFalseProbability() {
        return cachePenetrationBloomFilter.getFalseProbability();
    }

    /**
     * 获取布隆过滤器底层位数组的大小（单位：位）
     *
     * @return 位数组大小
     */
    public long getSize() {
        return cachePenetrationBloomFilter.getSize();
    }

    public int getHashIterations() {
        return cachePenetrationBloomFilter.getHashIterations();
    }

    /**
     * 估算当前布隆过滤器中已添加的数据量
     * 注意：这是基于概率的估算值，非精确计数
     *
     * @return 估算的已添加数据量
     */
    public long count() {
        return cachePenetrationBloomFilter.count();
    }
}
