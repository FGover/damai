package com.damai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 布隆过滤器 配置属性
 * @author: 阿星不是程序员
 **/
@Data
@ConfigurationProperties(prefix = BloomFilterProperties.PREFIX)
public class BloomFilterProperties {

    public static final String PREFIX = "bloom-filter";

    // 布隆过滤器名字
    private String name;
    // 布隆过滤器容量
    private Long expectedInsertions = 20000L;
    // 布隆过滤器碰撞率
    private Double falseProbability = 0.01D;
}
