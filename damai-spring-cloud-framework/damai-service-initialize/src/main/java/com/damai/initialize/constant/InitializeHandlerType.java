package com.damai.initialize.constant;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 初始化执行 不同策略类型
 * 定义了系统中所有初始化处理器的类型标识
 * @author: 阿星不是程序员
 **/
public class InitializeHandlerType {

    /**
     * 应用事件监听器类型
     * 标识该初始化处理器用于注册或配置Spring应用事件监听器（ApplicationListener）
     * 用于处理系统事件响应逻辑的初始化
     */
    public static final String APPLICATION_EVENT_LISTENER = "application_event_listener";

    /**
     * PostConstruct 初始化类型
     * 标识该初始化处理器对应@PostConstruct注解标注的初始化方法
     * 用于处理Bean初始化完成后的后置操作
     */
    public static final String APPLICATION_POST_CONSTRUCT = "application_post_construct";

    /**
     * InitializingBean 初始化类型
     * 标识该初始化处理器实现了InitializingBean接口的初始化逻辑
     * 对应afterPropertiesSet()方法的初始化操作
     */
    public static final String APPLICATION_INITIALIZING_BEAN = "application_initializing_bean";

    /**
     * 命令行运行器类型
     * 标识该初始化处理器对应CommandLineRunner接口的初始化逻辑
     * 用于处理应用启动后需要立即执行的命令行参数相关逻辑
     */
    public static final String APPLICATION_COMMAND_LINE_RUNNER = "application_command_line_runner";
}
