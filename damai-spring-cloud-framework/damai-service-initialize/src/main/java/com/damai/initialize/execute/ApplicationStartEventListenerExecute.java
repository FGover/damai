package com.damai.initialize.execute;

import com.damai.initialize.execute.base.AbstractApplicationExecute;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

import static com.damai.initialize.constant.InitializeHandlerType.APPLICATION_EVENT_LISTENER;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 基于ApplicationStartedEvent事件的应用启动初始化执行器
 * 实现Spring的ApplicationListener接口，监听应用启动完成事件，触发特定类型的初始化逻辑
 * 负责执行所有类型为APPLICATION_EVENT_LISTENER的初始化处理器
 * @author: 阿星不是程序员
 **/
public class ApplicationStartEventListenerExecute extends AbstractApplicationExecute implements
        ApplicationListener<ApplicationStartedEvent> {

    /**
     * 构造方法：接收Spring应用上下文Context并传递给父类
     * 确保父类能够获取容器中的初始化处理器Bean
     *
     * @param applicationContext Spring应用上下文
     */
    public ApplicationStartEventListenerExecute(ConfigurableApplicationContext applicationContext) {
        super(applicationContext);
    }

    /**
     * 实现ApplicationListener接口的事件处理方法
     * 当Spring容器完全启动（所有Bean加载完成，应用可对外提供服务）后，会触发ApplicationStartedEvent事件
     * 此方法作为事件回调，触发初始化逻辑执行
     *
     * @param event 应用启动完成事件对象（包含事件相关信息）
     */
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        // 调用父类的execute()方法，执行类型匹配的初始化处理器
        execute();
    }

    /**
     * 定义当前执行器处理的初始化类型
     * 与初始化处理器的type()方法返回值匹配，实现类型过滤
     *
     * @return 初始化类型常量（APPLICATION_EVENT_LISTENER）
     */
    @Override
    public String type() {
        return APPLICATION_EVENT_LISTENER;
    }
}
