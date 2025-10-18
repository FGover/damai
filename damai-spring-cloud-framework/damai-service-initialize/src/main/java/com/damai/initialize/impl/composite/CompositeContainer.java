package com.damai.initialize.impl.composite;

import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 组合模式容器类
 * 负责管理所有业务组件的生命周期：从Spring容器中收集组件、按规则构建树形结构、存储根节点、触发执行
 * 支持按组件类型（type）隔离管理多棵组件树
 * @author: 阿星不是程序员
 **/
public class CompositeContainer<T> {

    /**
     * 存储所有组件树的根节点，按组件类型（type）分组
     * 键：组件类型（如"user-validation"）
     * 值：该类型对应的组件树的根节点（AbstractComposite）
     */
    private final Map<String, AbstractComposite> allCompositeInterfaceMap = new HashMap<>();

    /**
     * 初始化方法：从Spring容器中收集所有组件，按类型分组并构建组件树
     * 流程：收集组件 -> 按type分组 -> 为每组构建树形结构 -> 存储根节点
     *
     * @param applicationEvent Spring应用上下文，用于获取所有AbstractComposite类型的Bean
     */
    public void init(ConfigurableApplicationContext applicationEvent) {
        // 从Spring容器中获取所有实现了AbstractComposite的Bean（键为Bean名称，值为组件实例）
        Map<String, AbstractComposite> compositeInterfaceMap = applicationEvent.getBeansOfType(AbstractComposite.class);
        // 按组件的type()方法返回值分组（同类型的组件属于同一棵树）
        Map<String, List<AbstractComposite>> collect = compositeInterfaceMap.values()
                .stream()
                .collect(Collectors.groupingBy(AbstractComposite::type));
        // 为每个类型的组件组构建树形结构，并将根节点存入容器
        collect.forEach((k, v) -> {
            // 构建该类型的组件树
            AbstractComposite root = build(v);
            if (Objects.nonNull(root)) {
                // 存储根节点，key为类型
                allCompositeInterfaceMap.put(k, root);
            }
        });
    }

    /**
     * 执行指定类型的组件树的所有业务逻辑
     *
     * @param type  组件类型（对应allCompositeInterfaceMap的key）
     * @param param 传递给组件的参数（泛型T）
     */
    public void execute(String type, T param) {
        // 根据类型获取根节点，若不存在则抛出异常
        AbstractComposite compositeInterface = Optional.ofNullable(allCompositeInterfaceMap.get(type))
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.COMPOSITE_NOT_EXIST));
        // 调用根节点的allExecute方法，触发整颗树的层级执行
        compositeInterface.allExecute(param);
    }

    /**
     * 递归构建组件树的辅助方法
     * 按层级递进，将下一层级的组件挂载到当前层级的父组件上
     *
     * @param groupedByTier 按层级分组的组件映射（key：层级编号；value：该层级下按执行顺序分组的组件）
     * @param currentTier   当前处理的层级（从最小编号层级开始）
     */
    private static void buildTree(Map<Integer, Map<Integer, AbstractComposite>> groupedByTier, int currentTier) {
        // 当前层级的所有组件（按执行顺序存储）
        Map<Integer, AbstractComposite> currentLevelComponents = groupedByTier.get(currentTier);
        // 下一层级的所有组件（待挂载到当前层级的组件）
        Map<Integer, AbstractComposite> nextLevelComponents = groupedByTier.get(currentTier + 1);
        // 若当前层级无组件，终止递归
        if (currentLevelComponents == null) {
            return;
        }
        // 若存在下一层级组件，将其挂载到当前层级的父组件上
        if (nextLevelComponents != null) {
            for (AbstractComposite child : nextLevelComponents.values()) {
                // 子组件生命的父级执行顺序（即父组件的executeOrder()返回值）
                Integer parentOrder = child.executeParentOrder();
                // 父级顺序为null或0的组件无需挂载（根节点的子组件父级顺序应为根节点的order）
                if (parentOrder == null || parentOrder == 0) {
                    continue;
                }
                // 从当前层级找到父组件（父组件的executeOrder()等于子组件的parentOrder）
                AbstractComposite parent = currentLevelComponents.get(parentOrder);
                if (parent != null) {
                    // 将子组件添加到父组件的子列表中
                    parent.add(child);
                }
            }
        }
        // 递归处理下一层级
        buildTree(groupedByTier, currentTier + 1);
    }

    /**
     * 构建单棵组件树的入口方法
     * 按层级和执行顺序整理组件，递归构建树形结构，最终返回根节点
     *
     * @param components 同一类型的组件集合（需构建为一棵树）
     * @return 组件树的根节点（父级顺序为0的组件）
     */
    private static AbstractComposite build(Collection<AbstractComposite> components) {
        // 按层级（executeTier()）分组，同层级内按执行顺序（executeOrder()）存储
        // TreeMap确保层级按升序排列（从1开始），便于按层级顺序处理
        Map<Integer, Map<Integer, AbstractComposite>> groupedByTier = new TreeMap<>();
        for (AbstractComposite component : components) {
            // 按层级分组：key为层级编号，value为该层级下的组件（按执行顺序存储）
            groupedByTier.computeIfAbsent(component.executeTier(), k -> new HashMap<>(16))
                    .put(component.executeOrder(), component);  // 同层级内按executeOrder()作为key
        }
        // 找到最小编号的层级（树的起点，通常是1）
        Integer minTier = groupedByTier.keySet().stream().min(Integer::compare).orElse(null);
        // 若没有组件，返回null
        if (minTier == null) {
            return null;
        }
        // 从最小编号层级开始，递归构建树形结构
        buildTree(groupedByTier, minTier);
        // 返回根节点，根节点是最小编号层级中，父级顺序为0的组件（根节点无父级）
        return groupedByTier.get(minTier).values().stream()
                .filter(c -> c.executeParentOrder() == null || c.executeParentOrder() == 0)
                .findFirst()
                .orElse(null);
    }
}
