package com.damai.service.init;

import com.damai.BusinessThreadPool;
import com.damai.initialize.base.AbstractApplicationPostConstructHandler;
import com.damai.service.ProgramCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目种类缓存初始化处理器
 * 继承自PostConstruct类型的抽象处理器，负责在应用启动阶段初始化节目分类相关的缓存数据
 * 确保系统启动后能快速访问节目分类信息，提升查询性能
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramCategoryInitData extends AbstractApplicationPostConstructHandler {

    /**
     * 节目分类服务
     */
    @Autowired
    private ProgramCategoryService programCategoryService;

    /**
     * 定义当前初始化处理器的执行顺序
     * 返回值为1，表示在同类型处理器中优先级较高（数值越小优先级越高）
     * 确保节目分类缓存优先于依赖它的其他初始化逻辑执行
     *
     * @return 执行顺序值1
     */
    @Override
    public Integer executeOrder() {
        return 1;
    }

    /**
     * 执行节目分类缓存初始化逻辑
     * 使用业务线程池异步执行，避免阻塞应用启动过程
     * 调用服务层方法将节目分类数据加载到Redis缓存中
     *
     * @param context Spring应用上下文（当前实现未直接使用）
     */
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        // 提交任务到业务线程池异步执行，提高应用启动速度
        BusinessThreadPool.execute(() -> {
            // 调用服务方法完成redis缓存初始化
            programCategoryService.programCategoryRedisDataInit();
        });
    }
}
