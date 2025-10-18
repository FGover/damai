package com.damai.conf;


import com.damai.util.StringUtil;
import com.damai.util.BusinessEsHandle;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: elasticsearch自动配置类
 * 负责创建和配置Elasticsearch相关的Bean，根据配置文件动态初始化客户端
 * @author: 阿星不是程序员
 **/
// 启用指定的配置属性类，使BusinessEsProperties生效并被Spring容器管理
@EnableConfigurationProperties(BusinessEsProperties.class)
// 条件注解：当配置文件中存在"elasticsearch.ip"属性时，当前配置类才会生效
@ConditionalOnProperty(value = "elasticsearch.ip")
public class BusinessEsAutoConfig {

    // 地址格式长度（IP:端口 分割后应为两部分）
    private static final int ADDRESS_LENGTH = 2;
    // 通信协议（默认使用HTTP）
    private static final String HTTP_SCHEME = "http";

    /**
     * 创建Elasticsearch的RestClient实例
     * RestClient是Elasticsearch官方提供的低级客户端，用于发送HTTP请求
     *
     * @param businessEsProperties 自动注入的Elasticsearch配置属性对象
     * @return 配置好的RestClient实例
     */
    @Bean
    public RestClient businessEsRestClient(BusinessEsProperties businessEsProperties) {
        // 默认用户名/密码占位符（用于判断是否配置了真实的认证信息）
        String defaultValue = "default";
        // 将配置的IP地址数组转换为HttpHost数组（包含主机、端口和协议）
        HttpHost[] hosts = Arrays.stream(businessEsProperties.getIp())
                .map(this::makeHttpHost)  // 转换每个地址为HttpHost对象
                .filter(Objects::nonNull)  // 过滤无效地址
                .toArray(HttpHost[]::new);
        // 创建RestClient构建器
        RestClientBuilder builder = RestClient.builder(hosts);
        // 获取配置的用户名和密码
        String userName = businessEsProperties.getUserName();
        String passWord = businessEsProperties.getPassWord();
        // 当用户名和密码不为空且不是默认值时，配置身份认证
        if (StringUtil.isNotEmpty(userName) && !defaultValue.equals(userName) && StringUtil.isNotEmpty(passWord)
                && !defaultValue.equals(passWord)) {
            // 创建凭证提供器
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            // 设置用户名密码凭证（适用于Elasticsearch的Basic Auth认证）
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, passWord));
            // 配置HTTP客户端，设置凭证提供器和IO线程数
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                            .setDefaultIOReactorConfig(
                                    IOReactorConfig.custom()
                                            // 设置IO线程数（根据最大连接数配置）
                                            .setIoThreadCount(businessEsProperties.getMaxConnectNum())
                                            .build()));
        }
        // 设置全局默认请求头（所有请求都会携带这些头信息）
        Header[] defaultHeaders = {
                new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"),  // 默认JSON格式
                new BasicHeader("Role", "Read")  // 自定义角色头（示例）
        };
        // 设置每个请求需要发送的默认headers，这样就不用在每个请求中指定它们。
        builder.setDefaultHeaders(defaultHeaders);
        // 配置请求超时参数
        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(businessEsProperties.getConnectTimeOut())   // 连接超时
                .setSocketTimeout(businessEsProperties.getSocketTimeOut())  // 数据传输超时
                .setConnectionRequestTimeout(businessEsProperties.getConnectionRequestTimeOut()));  // 连接池获取连接超时
        // 构建并返回RestClient实例
        return builder.build();
    }

    /**
     * 创建Elasticsearch操作工具类实例
     * 封装了Elasticsearch的常用操作，提供业务层调用的API
     *
     * @param businessEsRestClient 注入的Elasticsearch RestClient
     * @param businessEsProperties 注入的配置属性对象
     * @return 业务操作工具类实例
     */
    @Bean
    public BusinessEsHandle businessEsHandle(@Qualifier("businessEsRestClient") RestClient businessEsRestClient,
                                             BusinessEsProperties businessEsProperties) {
        // 初始化工具类，传入客户端和功能开关配置
        return new BusinessEsHandle(
                businessEsRestClient,
                businessEsProperties.getEsSwitch(),   // Elasticsearch总开关
                businessEsProperties.getEsTypeSwitch()  // Type功能开关
        );
    }

    /**
     * 将字符串地址（格式：IP:端口）转换为HttpHost对象
     *
     * @param s 地址字符串，如"192.168.1.1:9200"
     * @return 包含主机、端口和协议的HttpHost对象，格式错误时返回null
     */
    private HttpHost makeHttpHost(String s) {
        assert StringUtil.isNotEmpty(s);
        // 按":"分割地址（IP和端口）
        String[] address = s.split(":");
        // 验证格式是否正确（必须包含IP和端口两部分）
        if (address.length == ADDRESS_LENGTH) {
            String ip = address[0];
            int port = Integer.parseInt(address[1]);   // 转换端口为整数
            return new HttpHost(ip, port, HTTP_SCHEME);  // 创建HttpHost对象
        } else {
            // 格式错误返回null（会被过滤掉）
            return null;
        }
    }
}
