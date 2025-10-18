package com.damai.initialize.base;

import org.springframework.context.ConfigurableApplicationContext;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 初始化执行 顶级抽象 接口
 * 定义了系统初始化处理器的标准接口，所有初始化逻辑都应实现该接口
 * @author: 阿星不是程序员
 **/
public interface InitializeHandler {
    /**
     * 初始化执行类型
     * 用于标识当前初始化处理器的业务类型，可用于在执行过程中对不同类型的初始化逻辑进行分类处理或过滤
     *
     * @return 初始化类型字符串
     */
    String type();

    /**
     * 执行顺序
     * 定义当前初始化处理器的执行优先级，数值越小优先级越高
     * 框架会根据此值对所有初始化处理器进行排序后依次执行
     *
     * @return 执行顺序的整数值
     */
    Integer executeOrder();

    /**
     * 执行初始化逻辑的核心方法，具体的初始化操作在此实现
     *
     * @param context Spring应用上下文对象，可用于获取容器中的Bean或配置信息
     */
    void executeInit(ConfigurableApplicationContext context);

}
