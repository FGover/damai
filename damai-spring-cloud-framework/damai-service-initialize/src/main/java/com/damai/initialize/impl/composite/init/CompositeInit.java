package com.damai.initialize.impl.composite.init;

import com.damai.initialize.base.AbstractApplicationStartEventListenerHandler;
import com.damai.initialize.impl.composite.CompositeContainer;
import lombok.AllArgsConstructor;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 组合模式初始化操作执行器
 * 负责在应用启动阶段触发组合模式容器的初始化，是组合式业务组件树的启动入口
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class CompositeInit extends AbstractApplicationStartEventListenerHandler {

    /**
     * 组合模式容器实例（注入的是整个业务组件树的根容器）
     * 包含了所有按树形结构组织的业务组件（叶子节点和容器节点）
     */
    private final CompositeContainer compositeContainer;

    /**
     * 定义当前初始化器的执行顺序
     * 返回值越小，执行优先级越高（此处返回1，表示在同类型初始化器中较早执行）
     *
     * @return 执行顺序数值（1）
     */
    @Override
    public Integer executeOrder() {
        return 1;
    }

    /**
     * 应用启动时执行的初始化方法
     * 触发组合模式容器的初始化流程，加载并执行所有业务组件树中的逻辑
     *
     * @param context Spring应用上下文对象，可用于获取容器中的Bean或配置信息
     */
    @Override
    public void executeInit(ConfigurableApplicationContext context) {
        // 调用组合容器的init方法，启动整个组件树的初始化
        // 内部会按树形结构的层级和顺序执行所有子组件的业务逻辑
        compositeContainer.init(context);
    }
}
