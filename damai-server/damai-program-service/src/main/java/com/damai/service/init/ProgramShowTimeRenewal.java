package com.damai.service.init;

import com.damai.core.SpringUtil;
import com.damai.initialize.base.AbstractApplicationPostConstructHandler;
import com.damai.service.ProgramService;
import com.damai.service.ProgramShowTimeService;
import com.damai.util.BusinessEsHandle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目演出时间更新初始化处理器
 * 继承自PostConstruct类型的抽象处理器，负责在应用启动后更新节目演出时间相关数据
 * 确保演出信息的时效性，同步更新缓存与搜索引擎数据
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramShowTimeRenewal extends AbstractApplicationPostConstructHandler {

    /**
     * 节目演出时间服务，用于处理演出时间的更新逻辑
     */
    @Autowired
    private ProgramShowTimeService programShowTimeService;

    /**
     * 节目服务，用于操作节目相关的缓存数据
     */
    @Autowired
    private ProgramService programService;

    /**
     * 业务ES处理器，用于操作Elasticsearch搜索引擎的索引数据
     */
    @Autowired
    private BusinessEsHandle businessEsHandle;

    /**
     * 定义当前初始化处理器的执行顺序
     * 返回值为2，表示在同类型处理器中优先级中等（数值越小优先级越高）
     * 确保在节目分类等基础数据初始化之后执行
     *
     * @return 执行顺序值2
     */
    @Override
    public Integer executeOrder() {
        return 2;
    }

    /**
     * 执行节目演出时间更新的初始化逻辑
     * 1. 更新演出信息并获取受影响的节目ID集合
     * 2. 若有受影响的节目，重建相关ES索引并清理缓存
     *
     * @param context Spring应用上下文（当前实现未直接使用）
     */
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        // 判断节目演出时间是否过期，如果过期了，则更新时间，并返回已经更新演出时间的节目id（也就是过期时间id集合）
        Set<Long> programIdSet = programShowTimeService.renewal();
        if (!programIdSet.isEmpty()) {
            // 如果更新了，将elasticsearch的整个索引和数据都删除
            businessEsHandle.deleteIndex(SpringUtil.getPrefixDistinctionName() + "-" +
                    ProgramDocumentParamName.INDEX_NAME);
            // 遍历过期时间id集合
            for (Long programId : programIdSet) {
                // 将redis中的数据删除
                programService.delRedisData(programId);
                // 将本地缓存数据也删除
                programService.delLocalCache(programId);
            }
        }
    }
}
