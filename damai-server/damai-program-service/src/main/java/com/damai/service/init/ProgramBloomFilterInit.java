package com.damai.service.init;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.handler.BloomFilterHandler;
import com.damai.initialize.base.AbstractApplicationPostConstructHandler;
import com.damai.service.ProgramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目id布隆过滤器初始化处理器
 * 继承自PostConstruct类型的抽象处理器，负责在应用启动时初始化节目ID的布隆过滤器
 * 通过布隆过滤器快速判断节目ID是否存在，减少无效数据库查询，提升系统性能
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramBloomFilterInit extends AbstractApplicationPostConstructHandler {

    /**
     * 节目服务，用于获取所有节目ID列表
     */
    @Autowired
    private ProgramService programService;

    /**
     * 布隆过滤器处理器，用于执行布隆过滤器的添加、查询等操作
     */
    @Autowired
    private BloomFilterHandler bloomFilterHandler;

    /**
     * 定义当前初始化处理器的执行顺序
     * 返回值为4，表示在同类型处理器中优先级较低（数值越小优先级越高）
     * 确保在节目数据初始化（如ES同步）完成后执行，避免因ID缺失导致布隆过滤器初始化不完整
     *
     * @return 执行顺序值4
     */
    @Override
    public Integer executeOrder() {
        return 4;
    }

    /**
     * 执行节目ID布隆过滤器的初始化逻辑
     * 1. 获取所有节目ID列表
     * 2. 将ID批量添加到布隆过滤器中，用于后续快速存在性判断
     *
     * @param context Spring应用上下文（当前实现未直接使用）
     */
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        // 从节目服务获取系统中所有节目ID的列表
        List<Long> allProgramIdList = programService.getAllProgramIdList();
        // 若节目ID列表为空，直接返回（无需初始化）
        if (CollectionUtil.isEmpty(allProgramIdList)) {
            return;
        }
        // 遍历所有节目ID，将其转换为字符串添加到布隆过滤器
        // 布隆过滤器会通过哈希算法标记这些ID，支持后续O(1)时间复杂度的存在性判断
        allProgramIdList.forEach(programId -> bloomFilterHandler.add(String.valueOf(programId)));
    }
}
