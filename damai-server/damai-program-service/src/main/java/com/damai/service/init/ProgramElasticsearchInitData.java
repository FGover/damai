package com.damai.service.init;

import com.damai.BusinessThreadPool;
import com.damai.core.SpringUtil;
import com.damai.dto.EsDocumentMappingDto;
import com.damai.entity.TicketCategoryAggregate;
import com.damai.initialize.base.AbstractApplicationPostConstructHandler;
import com.damai.service.ProgramService;
import com.damai.util.BusinessEsHandle;
import com.damai.vo.ProgramVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目es缓存初始化处理器
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramElasticsearchInitData extends AbstractApplicationPostConstructHandler {

    /**
     * 业务ES处理器，用于执行Elasticsearch的索引创建、数据添加等操作
     */
    @Autowired
    private BusinessEsHandle businessEsHandle;

    /**
     * 节目服务，用于获取节目相关数据（ID列表、详情、票档信息等）
     */
    @Autowired
    private ProgramService programService;

    /**
     * 定义当前初始化处理器的执行顺序
     * 返回值为3，表示在同类型处理器中优先级较低（数值越小优先级越高）
     * 确保在节目分类缓存（order=1）和演出时间更新（order=2）之后执行，避免依赖数据未准备完成
     *
     * @return 执行顺序值3
     */
    @Override
    public Integer executeOrder() {
        return 3;
    }

    /**
     * 执行顺序第3执行
     * 项目启动后，异步将节目数据同步到Elasticsearch中
     * 注意：此实现仅适用于开发/测试环境，生产环境下数据量大时存在性能问题
     * 生产环境通常采用：
     * 1. 数据库变更时实时同步（如通过Binlog监听、消息队列）
     * 2. 定时任务增量更新（避免全量同步的性能开销）
     */
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        // 提交任务到业务线程池异步执行，避免阻塞应用启动
        BusinessThreadPool.execute(() -> {
            try {
                // 执行ES数据初始化核心逻辑
                initElasticsearchData();
            } catch (Exception e) {
                log.error("executeInit error", e);
            }
        });
    }

    /**
     * 初始化节目数据到Elasticsearch的核心方法
     * 流程：检查并创建索引 -> 加载节目数据 -> 转换数据格式 -> 批量添加数据到ES
     */
    public void initElasticsearchData() {
        // 检查索引是否存在，若已存在则跳过初始化（避免重复同步）
        if (!indexAdd()) {
            return;
        }
        // 所有有效节目id集合
        List<Long> allProgramIdList = programService.getAllProgramIdList();
        // 节目票档信息
        Map<Long, TicketCategoryAggregate> ticketCategorieMap = programService.selectTicketCategorieMap(allProgramIdList);
        for (Long programId : allProgramIdList) {
            ProgramVo programVo = programService.getDetailFromDb(programId);
            Map<String, Object> map = new HashMap<>(32);
            map.put(ProgramDocumentParamName.ID, programVo.getId());
            map.put(ProgramDocumentParamName.PROGRAM_GROUP_ID, programVo.getProgramGroupId());
            map.put(ProgramDocumentParamName.PRIME, programVo.getPrime());
            map.put(ProgramDocumentParamName.TITLE, programVo.getTitle());
            map.put(ProgramDocumentParamName.ACTOR, programVo.getActor());
            map.put(ProgramDocumentParamName.PLACE, programVo.getPlace());
            map.put(ProgramDocumentParamName.ITEM_PICTURE, programVo.getItemPicture());
            map.put(ProgramDocumentParamName.AREA_ID, programVo.getAreaId());
            map.put(ProgramDocumentParamName.AREA_NAME, programVo.getAreaName());
            map.put(ProgramDocumentParamName.PROGRAM_CATEGORY_ID, programVo.getProgramCategoryId());
            map.put(ProgramDocumentParamName.PROGRAM_CATEGORY_NAME, programVo.getProgramCategoryName());
            map.put(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID, programVo.getParentProgramCategoryId());
            map.put(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_NAME, programVo.getParentProgramCategoryName());
            map.put(ProgramDocumentParamName.HIGH_HEAT, programVo.getHighHeat());
            map.put(ProgramDocumentParamName.ISSUE_TIME, programVo.getIssueTime());
            map.put(ProgramDocumentParamName.SHOW_TIME, programVo.getShowTime());
            map.put(ProgramDocumentParamName.SHOW_DAY_TIME, programVo.getShowDayTime());
            map.put(ProgramDocumentParamName.SHOW_WEEK_TIME, programVo.getShowWeekTime());
            map.put(ProgramDocumentParamName.MIN_PRICE,
                    Optional.ofNullable(ticketCategorieMap.get(programVo.getId()))
                            .map(TicketCategoryAggregate::getMinPrice).orElse(null));
            map.put(ProgramDocumentParamName.MAX_PRICE,
                    Optional.ofNullable(ticketCategorieMap.get(programVo.getId()))
                            .map(TicketCategoryAggregate::getMaxPrice).orElse(null));
            businessEsHandle.add(SpringUtil.getPrefixDistinctionName() + "-" +
                    ProgramDocumentParamName.INDEX_NAME, ProgramDocumentParamName.INDEX_TYPE, map);
        }
    }

    /**
     * 检查并创建Elasticsearch索引
     *
     * @return 若索引创建成功（或无需创建），返回true，否则返回false
     */
    public boolean indexAdd() {
        // 检查索引是否存在
        boolean indexExists = businessEsHandle.checkIndex(SpringUtil.getPrefixDistinctionName() + "-" +
                ProgramDocumentParamName.INDEX_NAME, ProgramDocumentParamName.INDEX_TYPE);
        // 索引已存在，无需重复初始化
        if (indexExists) {
            // TODO
            businessEsHandle.deleteIndex(SpringUtil.getPrefixDistinctionName() + "-" +
                    ProgramDocumentParamName.INDEX_NAME);
        }
        try {
            // 索引不存在，创建索引并设置mapping（字段映射）
            businessEsHandle.createIndex(SpringUtil.getPrefixDistinctionName() + "-" +
                    ProgramDocumentParamName.INDEX_NAME, ProgramDocumentParamName.INDEX_TYPE, getEsMapping());
            return true;
        } catch (Exception e) {
            // 记录索引创建失败的异常
            log.error("createIndex error", e);
            return false;
        }
    }

    /**
     * 组装Elasticsearch索引的mapping结构（相当于数据库表结构定义）
     * 定义每个字段的类型，用于ES的分词、排序、过滤等功能
     *
     * @return 字段映射列表
     */
    public List<EsDocumentMappingDto> getEsMapping() {
        List<EsDocumentMappingDto> mappingList = new ArrayList<>();
        // 节目ID（精确匹配，用于唯一标识）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.ID, "long"));
        // 节目组ID（用于分组查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.PROGRAM_GROUP_ID, "integer"));
        // 优先级（用于排序）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.PRIME, "long"));
        // 节目标题（text类型支持分词查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.TITLE, "text"));
        // 演员（text类型支持分词查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.ACTOR, "text"));
        // 演出地点（text类型支持分词查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.PLACE, "text"));
        // 节目图片（存储URL，无需分词）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.ITEM_PICTURE, "text"));
        // 地区ID（精确匹配，用于地区筛选）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.AREA_ID, "long"));
        // 地区名称（text类型支持分词查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.AREA_NAME, "text"));
        // 分类ID（精确匹配，用于分类筛选）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.PROGRAM_CATEGORY_ID, "long"));
        // 分类名称（text类型支持分词查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.PROGRAM_CATEGORY_NAME, "text"));
        // 父分类ID（用于多级分类查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID, "long"));
        // 父分类名称（支持分词查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_NAME, "text"));
        // 热度值（用于排序）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.HIGH_HEAT, "integer"));
        // 发布时间（date类型支持时间范围查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.ISSUE_TIME, "date"));
        // 演出时间（date类型支持时间范围查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.SHOW_TIME, "date"));
        // 演出日期（date类型，仅精确到天）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.SHOW_DAY_TIME, "date"));
        // 演出星期（如"周一"，支持文本筛选）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.SHOW_WEEK_TIME, "text"));
        // 最低票价（integer类型支持范围查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.MIN_PRICE, "integer"));
        // 最高票价（integer类型支持范围查询）
        mappingList.add(new EsDocumentMappingDto(ProgramDocumentParamName.MAX_PRICE, "integer"));
        return mappingList;
    }
}
