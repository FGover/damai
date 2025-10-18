package com.damai.core;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import static com.damai.constant.Constant.DEFAULT_PREFIX_DISTINCTION_NAME;
import static com.damai.constant.Constant.PREFIX_DISTINCTION_NAME;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: spring工具
 * @author: 阿星不是程序员
 **/

public class SpringUtil implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    // Sprin 的可配置上下文对象，静态存储，方便全局调用
    private static ConfigurableApplicationContext configurableApplicationContext;

    /**
     * 从 Spring 环境配置中读取前缀区分名称（prefix distinction name）
     *
     * @return 配置文件中 prefix.distinction.name 的值，若未配置则返回默认值 DEFAULT_PREFIX_DISTINCTION_NAME
     */
    public static String getPrefixDistinctionName() {
        return configurableApplicationContext.getEnvironment().getProperty(PREFIX_DISTINCTION_NAME,
                DEFAULT_PREFIX_DISTINCTION_NAME);
    }

    /**
     * Spring 容器启动时自动调用的方法
     * 将当前可配置应用上下文保存到静态变量中，方便后续静态方法访问环境配置
     *
     * @param applicationContext Spring 容器上下文
     */
    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        configurableApplicationContext = applicationContext;
    }
}
