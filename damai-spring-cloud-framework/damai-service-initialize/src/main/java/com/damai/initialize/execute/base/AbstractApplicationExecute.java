package com.damai.initialize.execute.base;

import com.damai.initialize.base.InitializeHandler;
import lombok.AllArgsConstructor;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Comparator;
import java.util.Map;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 用于处理应用程序启动执行器的基类
 * 负责扫描并执行特定类型的初始化处理器，实现启动流程的模块化管理
 * 子类通过指定类型，可分别处理不同阶段或不同业务域的初始化逻辑
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public abstract class AbstractApplicationExecute {

    /**
     * Spring应用上下文，用于获取所有初始化处理器的Bean实例
     */
    private final ConfigurableApplicationContext applicationContext;

    /**
     * 执行指定类型的所有初始化处理器
     * 流程：扫描Bean -> 筛选类型 -> 按顺序执行 -> 调用初始化方法
     */
    public void execute() {
        // 从Spring容器中获取所有实现了InitializeHandler接口的Bean实例
        Map<String, InitializeHandler> initializeHandlerMap = applicationContext.getBeansOfType(InitializeHandler.class);
        initializeHandlerMap.values()
                .stream()
                // 过滤出与当前执行器类型匹配的处理器（确保只执行特定类型的初始化逻辑）
                .filter(initializeHandler -> initializeHandler.type().equals(type()))
                // 按执行顺序排序（executeOrder返回值越小，优先级越高）
                .sorted(Comparator.comparingInt(InitializeHandler::executeOrder))
                // 依次执行每个处理器的初始化方法，并传入应用上下文
                .forEach(initializeHandler -> {
                    initializeHandler.executeInit(applicationContext);
                });
    }

    /**
     * 抽象方法：定义当前执行器处理的初始化类型
     * 子类需实现此方法，指定要处理的初始化逻辑类型（如"program"、"user"等）
     *
     * @return 初始化类型字符串
     */
    public abstract String type();
}
