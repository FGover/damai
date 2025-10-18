package com.damai.initialize.execute;

import com.damai.initialize.execute.base.AbstractApplicationExecute;
import org.springframework.context.ConfigurableApplicationContext;

import jakarta.annotation.PostConstruct;

import static com.damai.initialize.constant.InitializeHandlerType.APPLICATION_POST_CONSTRUCT;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 基于@PostConstruct注解的应用启动初始化执行器
 * 利用JSR-250规范的@PostConstruct注解，在当前Bean初始化完成后触发特定类型的初始化逻辑
 * 负责执行所有类型为APPLICATION_POST_CONSTRUCT的初始化处理器
 * @author: 阿星不是程序员
 **/
public class ApplicationPostConstructExecute extends AbstractApplicationExecute {

    /**
     * 构造方法：接收Spring应用上下文并传递给父类
     * 父类需通过上下文扫描并处理初始化处理器Bean
     *
     * @param applicationContext Spring应用上下文
     */
    public ApplicationPostConstructExecute(ConfigurableApplicationContext applicationContext) {
        super(applicationContext);
    }

    /**
     * 标注@PostConstruct的初始化方法
     * 当当前Bean的依赖注入完成后，由Spring容器自动调用
     * 触发父类的初始化执行逻辑
     */
    @PostConstruct
    public void postConstructExecute() {
        execute();
    }

    /**
     * 定义当前执行器处理的初始化类型
     * 与初始化处理器的type()方法返回值匹配，实现类型过滤
     *
     * @return 初始化类型常量（APPLICATION_POST_CONSTRUCT）
     */
    @Override
    public String type() {
        return APPLICATION_POST_CONSTRUCT;
    }
}
