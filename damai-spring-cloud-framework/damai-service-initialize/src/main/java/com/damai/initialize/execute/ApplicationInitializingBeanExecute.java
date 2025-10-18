package com.damai.initialize.execute;

import com.damai.initialize.execute.base.AbstractApplicationExecute;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ConfigurableApplicationContext;

import static com.damai.initialize.constant.InitializeHandlerType.APPLICATION_INITIALIZING_BEAN;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 基于InitializingBean接口的应用程序启动初始化执行器
 * 实现Spring的InitializingBean接口，在Bean实例Bean属性初始化完成后触发特定类型的初始化逻辑
 * 负责执行所有类型为APPLICATION_INITIALIZING_BEAN的初始化处理器
 * @author: 阿星不是程序员
 **/

public class ApplicationInitializingBeanExecute extends AbstractApplicationExecute implements InitializingBean {

    /**
     * 构造方法：接收Spring应用上下文并传递给父类
     * 确保父类能够获取容器中的初始化处理器Bean
     *
     * @param applicationContext Spring应用上下文
     */
    public ApplicationInitializingBeanExecute(ConfigurableApplicationContext applicationContext) {
        super(applicationContext);
    }

    /**
     * 实现InitializingBean接口的afterPropertiesSet方法
     * 在当前Bean的所有属性被Spring容器注入完成后，自动调用此方法
     * 触发父类的初始化执行逻辑
     */
    @Override
    public void afterPropertiesSet() {
        execute();
    }

    /**
     * 定义当前执行器处理的初始化类型
     * 与初始化处理器的type()方法返回值匹配，实现类型过滤
     *
     * @return 初始化类型常量（APPLICATION_INITIALIZING_BEAN）
     */
    @Override
    public String type() {
        return APPLICATION_INITIALIZING_BEAN;
    }
}
