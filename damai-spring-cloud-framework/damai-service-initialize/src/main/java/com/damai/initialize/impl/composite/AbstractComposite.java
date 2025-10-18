package com.damai.initialize.impl.composite;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * @param <T> 泛型参数，表示执行业务时传递的参数类型
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 抽象类 AbstractComposite 表示组合接口
 * 基于组合模式的抽象组件基类，用于构建树形结构的业务逻辑组件，支持层级化管理和有序执行复杂业务逻辑，通过泛型适配不同业务参数类型
 * @author: 阿星不是程序员
 **/
public abstract class AbstractComposite<T> {

    /**
     * 存储当前组件的子组件列表
     * 体现组合模式的核心特性：容器组件可以包含其他组件（叶子或容器）
     */
    protected List<AbstractComposite<T>> list = new ArrayList<>();

    /**
     * 执行具体业务逻辑的抽象方法，由子类具体实现。
     * 叶子组件：实现具体的原子业务逻辑
     * 容器组件：可空实现或实现当前层级的聚合逻辑
     *
     * @param param 泛型参数，用于传递业务执行所需的数据（如订单DTO、请求参数等）
     */
    protected abstract void execute(T param);

    /**
     * 获取当前组件的业务类型标识
     * 用于区分不同业务领域的组件
     *
     * @return 组件类型字符串
     */
    public abstract String type();

    /**
     * 获取父级组件的执行顺序，用于构建父子层级关系
     * 根节点返回0，其他节点返回其父节点的executeOrder()值，实现树形结构的层级绑定
     *
     * @return 返回父级执行顺序，用于建立层级关系.(根节点的话返回值为0)
     */
    public abstract Integer executeParentOrder();

    /**
     * 获取当前组件的执行层级（树的深度）
     * 用于控制跨层级的执行优先级（如层级1的组件优先于层级2的组件执行）
     *
     * @return 执行层级（从1开始递增）
     */
    public abstract Integer executeTier();

    /**
     * 获取当前组件在同一层级中的执行顺序
     * 同层级内按此值升序执行（值越小优先级越高）
     *
     * @return 同层级执行顺序（从1开始递增）
     */
    public abstract Integer executeOrder();

    /**
     * 向当前组件添加子组件，构建树形结构
     * 支持链式调用：如parent.add(child1).add(child2)
     *
     * @param abstractComposite 子组件实例（可为叶子组件或其他容器组件）
     */
    public void add(AbstractComposite<T> abstractComposite) {
        list.add(abstractComposite);
    }

    /**
     * 按树形结构的层级顺序执行所有组件的业务逻辑
     * 采用广度优先遍历（BFS）确保：
     * 1. 先执行完当前层级的所有组件，再执行下一层级
     * 2. 同层级内按executeOrder()排序执行
     *
     * @param param 泛型参数，传递给所有组件的execute()方法
     */
    public void allExecute(T param) {
        // 用队列实现广度优先遍历
        Queue<AbstractComposite<T>> queue = new LinkedList<>();
        // 从当前组件（通常是根节点）开始遍历
        queue.add(this);
        // 循环处理队列中的所有组件
        while (!queue.isEmpty()) {
            // 当前层级的组件数量（控制按层级执行）
            int levelSize = queue.size();
            // 处理当前层级的所有组件
            for (int i = 0; i < levelSize; i++) {
                // 取出队列头部的组件
                AbstractComposite<T> current = queue.poll();
                // 执行当前组件的业务逻辑（子类实现的具体逻辑）
                assert current != null;
                current.execute(param);
                // 将当前组件的所有子组件加入队列，等待下一层级处理
                queue.addAll(current.list);
            }
        }
    }
}
