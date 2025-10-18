package com.damai.conf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: elasticsearch配置属性类
 * 用于封装与Elasticsearch连接相关的配置信息，通过配置文件自动注入属性值
 * @author: 阿星不是程序员
 **/
@Data
@ConfigurationProperties(prefix = BusinessEsProperties.PREFIX)
public class BusinessEsProperties {

    /**
     * 配置文件中Elasticsearch相关配置的前缀
     * 例如：elasticsearch.ip、elasticsearch.userName等
     */
    public static final String PREFIX = "elasticsearch";

    /**
     * Elasticsearch集群地址列表
     * 格式示例：["192.168.1.100:9200", "192.168.1.101:9200"]
     */
    private String[] ip;

    /**
     * 连接Elasticsearch的用户名
     */
    private String userName;

    /**
     * 连接Elasticsearch的密码
     */
    private String passWord;

    /**
     * Elasticsearch功能开关
     * true：启用Elasticsearch相关功能
     * false：禁用Elasticsearch相关功能
     * 默认值：true（开启）
     */
    private Boolean esSwitch = true;

    /**
     * Elasticsearch类型(type)功能开关
     * Elasticsearch 7.x及以上版本已逐渐废弃type概念
     * true：启用type功能
     * false：禁用type功能
     * 默认值：false（关闭）
     */
    private Boolean esTypeSwitch = false;

    /**
     * 连接超时时间：表示客户端与Elasticsearch服务器建立连接的最大等待时间（单位：毫秒）
     */
    private Integer connectTimeOut = 40000;

    /**
     * Socket超时时间：表示客户端与Elasticsearch服务器之间数据传输的最大等待时间（单位：毫秒）
     */
    private Integer socketTimeOut = 40000;

    /**
     * 连接请求超时时间：表示从连接池获取连接的最大等待时间（单位：毫秒）
     */
    private Integer connectionRequestTimeOut = 40000;

    /**
     * 最大连接数：表示客户端允许同时建立的最大连接数量，用于控制连接池大小
     */
    private Integer maxConnectNum = 400;
}
