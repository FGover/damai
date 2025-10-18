package com.damai.initialize.execute;

import com.damai.initialize.execute.base.AbstractApplicationExecute;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;

import static com.damai.initialize.constant.InitializeHandlerType.APPLICATION_COMMAND_LINE_RUNNER;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 基于CommandLineRunner} 类型的应用启动初始化执行器
 * 继承Spring的CommandLineRunner接口，在应用启动完成后触发特定类型的初始化逻辑
 * 负责执行所有类型为APPLICATION_COMMAND_LINE_RUNNER的初始化处理器
 * @author: 阿星不是程序员
 **/
public class ApplicationCommandLineRunnerExecute extends AbstractApplicationExecute implements CommandLineRunner {

    /**
     * 构造方法：接收Spring应用上下文并传递给父类
     * 父类需通过上下文获取并处理初始化处理器
     *
     * @param applicationContext Spring应用上下文
     */
    public ApplicationCommandLineRunnerExecute(ConfigurableApplicationContext applicationContext) {
        super(applicationContext);
    }

    /**
     * 实现CommandLineRunner接口的run方法
     * 当Spring容器初始化完成后，会自动调用此方法，触发初始化逻辑执行
     *
     * @param args
     */
    @Override
    public void run(final String... args) {
        // 调用父类的execute()方法，执行所有匹配类型的初始化处理器
        execute();
    }

    /**
     * 定义当前执行器处理的初始化类型
     * 与初始化处理器的type()方法返回值匹配，确保只处理特定类型的初始化逻辑
     *
     * @return 初始化类型常量（APPLICATION_COMMAND_LINE_RUNNER）
     */
    @Override
    public String type() {
        return APPLICATION_COMMAND_LINE_RUNNER;
    }
}
