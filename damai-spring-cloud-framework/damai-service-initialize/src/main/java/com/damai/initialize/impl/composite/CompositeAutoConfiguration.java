package com.damai.initialize.impl.composite;

import com.damai.initialize.impl.composite.init.CompositeInit;
import org.springframework.context.annotation.Bean;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 组合模式配置。负责将组合模式核心组件注册为Spring Bean，实现与Spring框架的集成
 * @author: 阿星不是程序员
 **/
public class CompositeAutoConfiguration {

    /**
     * 注册组合模式容器为Spring Bean
     * CompositeContainer是组件树的核心管理器，负责组件收集、树构建和执行控制
     *
     * @return CompositeContainer实例，将被Spring容器管理
     */
    @Bean
    public CompositeContainer compositeContainer() {
        return new CompositeContainer();
    }

    /**
     * 注册组合模式初始化器为Spring Bean
     * CompositeInit是组合模式与Spring启动流程的衔接点，负责在应用启动时触发容器初始化
     *
     * @param compositeContainer 注入CompositeContainer实例（依赖注入）
     * @return CompositeInit实例，将被Spring容器管理
     */
    @Bean
    public CompositeInit compositeInit(CompositeContainer compositeContainer) {
        // 将容器实例传入初始化器，使初始化器能调用容器的init()方法
        return new CompositeInit(compositeContainer);
    }
}
