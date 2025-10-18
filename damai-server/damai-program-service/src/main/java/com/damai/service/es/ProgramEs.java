package com.damai.service.es;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.core.SpringUtil;
import com.damai.dto.EsDataQueryDto;
import com.damai.dto.ProgramListDto;
import com.damai.dto.ProgramPageListDto;
import com.damai.dto.ProgramRecommendListDto;
import com.damai.dto.ProgramSearchDto;
import com.damai.enums.BusinessStatus;
import com.damai.page.PageUtil;
import com.damai.page.PageVo;
import com.damai.service.init.ProgramDocumentParamName;
import com.damai.service.tool.ProgramPageOrder;
import com.damai.util.BusinessEsHandle;
import com.damai.util.StringUtil;
import com.damai.vo.ProgramHomeVo;
import com.damai.vo.ProgramListVo;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: ES节目查询服务类
 * 封装与节目相关的Elasticsearch查询操作，主要用于主页节目列表数据的查询
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramEs {

    @Autowired
    private BusinessEsHandle businessEsHandle;

    /**
     * 查询主页节目列表数据
     * 根据前端传入的筛选条间（区域ID、父节目类型ID集合），从ES查询对应节目并封装返回
     * 主页通常展示四个父节目类型的内容，因此会按父类型ID循环查询
     *
     * @param programListDto
     * @return 主页节目列表VO集合，每个元素包含一个父类型的节目数据（分类名称、ID、节目列表）
     */
    public List<ProgramHomeVo> selectHomeList(ProgramListDto programListDto) {
        // 初始化返回结果：存储多个父类型的节目数据
        List<ProgramHomeVo> programHomeVoList = new ArrayList<>();
        try {
            // 循环遍历父节目类型ID集合，每个父类型单独查询，最终汇总到结果列表
            for (Long parentProgramCategoryId : programListDto.getParentProgramCategoryIds()) {
                // 构建当前父类型的查询条件列表
                List<EsDataQueryDto> programEsQueryDto = new ArrayList<>();
                // 1.处理区域筛选条件
                if (Objects.nonNull(programListDto.getAreaId())) {
                    // 若传入了区域ID，则按区域ID筛选节目
                    EsDataQueryDto areaIdQueryDto = new EsDataQueryDto();
                    areaIdQueryDto.setParamName(ProgramDocumentParamName.AREA_ID);  // 字段名：区域ID
                    areaIdQueryDto.setParamValue(programListDto.getAreaId());  // 字段值：前端传入的区域ID
                    programEsQueryDto.add(areaIdQueryDto);
                } else {
                    // 若未传入区域ID，则默认查询"精选"节目（PRIME=YES）
                    EsDataQueryDto primeQueryDto = new EsDataQueryDto();
                    primeQueryDto.setParamName(ProgramDocumentParamName.PRIME);  // 字段名：精选标识
                    primeQueryDto.setParamValue(BusinessStatus.YES.getCode()); // 字段值：是（1，根据业务定义）
                    programEsQueryDto.add(primeQueryDto);
                }
                // 2.添加父节目类型筛选条件（当前循环的父类型ID）
                EsDataQueryDto parentProgramCategoryIdQueryDto = new EsDataQueryDto();
                // 字段名：父节目类型ID
                parentProgramCategoryIdQueryDto.setParamName(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID);
                // 字段值：当前循环的父类型ID
                parentProgramCategoryIdQueryDto.setParamValue(parentProgramCategoryId);
                programEsQueryDto.add(parentProgramCategoryIdQueryDto);
                // 3.调用ES工具类查询分页数据：第一页，每页7条
                // 索引名格式：前缀区分名 + 节目索引名（如“prefix-program”）
                // 索引类型：节目对应的ES类型（ProgramDocumentParamName中定义）
                PageInfo<ProgramListVo> pageInfo = businessEsHandle.queryPage(
                        SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
                        ProgramDocumentParamName.INDEX_TYPE,
                        programEsQueryDto,
                        1,
                        7,
                        ProgramListVo.class
                );
                // 4.若查询到数据，封装为ProgramHomeVo（包含分类信息和节目列表）
                if (!pageInfo.getList().isEmpty()) {
                    ProgramHomeVo programHomeVo = new ProgramHomeVo();
                    // 从查询结果中取第一个节目，获取父类型名称和ID（同一父类型的节目该值相同）
                    programHomeVo.setCategoryName(pageInfo.getList().get(0).getParentProgramCategoryName());
                    programHomeVo.setCategoryId(pageInfo.getList().get(0).getParentProgramCategoryId());
                    // 设置当前父类型的节目列表
                    programHomeVo.setProgramListVoList(pageInfo.getList());
                    // 添加到结果集合
                    programHomeVoList.add(programHomeVo);
                }
            }
        } catch (Exception e) {
            log.error("businessEsHandle.queryPage error", e);
        }
        // 返回封装后的主页节目列表
        return programHomeVoList;
    }

    /**
     * 获取节目推荐列表
     * 根据传入的查询参数（区域、分类、排除节目等）从ES中查询并返回推荐节目
     *
     * @param programRecommendListDto 推荐查询参数DTO，包含区域ID、父分类ID、排除的节目ID
     * @return List<ProgramListVo> 推荐的节目列表（映射ES查询结果）
     */
    public List<ProgramListVo> recommendList(ProgramRecommendListDto programRecommendListDto) {
        // 初始化结果列表
        List<ProgramListVo> programListVoList = new ArrayList<>();
        try {
            // 标记是否为全量查询（无任何筛选条件时为true）
            boolean allQueryFlag = true;
            // 全量匹配查询构建器（查询所有文档）
            MatchAllQueryBuilder matchAllQueryBuilder = QueryBuilders.matchAllQuery();
            // 布尔查询构建器（用于组合多个筛选条件）
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            // 1.若传入区域id，则添加区域id条件
            if (Objects.nonNull(programRecommendListDto.getAreaId())) {
                allQueryFlag = false;
                QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.AREA_ID,
                        programRecommendListDto.getAreaId());
                boolQuery.must(builds);
            }
            // 2.若传入父分类id，则添加父分类id条件
            if (Objects.nonNull(programRecommendListDto.getParentProgramCategoryId())) {
                allQueryFlag = false;
                QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID,
                        programRecommendListDto.getParentProgramCategoryId());
                boolQuery.must(builds);
            }
            // 3.若传入排除的节目id，则添加排除条件
            if (Objects.nonNull(programRecommendListDto.getProgramId())) {
                allQueryFlag = false;
                QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.ID,
                        programRecommendListDto.getProgramId());
                boolQuery.mustNot(builds);  // 排除
            }
            // 构建ES查询源
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            // 设置查询条件：全量查询或组合查询
            searchSourceBuilder.query(allQueryFlag ? matchAllQueryBuilder : boolQuery);
            // 设置查询结果追踪总数
            searchSourceBuilder.trackTotalHits(true);
            // 设置分页参数：从第1页开始，每页10条
            searchSourceBuilder.from(1);
            searchSourceBuilder.size(10);
            // 设置随机推荐排序
            // 构建随机排序脚本（生成0-1的随机数）
            Script script = new Script("Math.random()");
            // 基于脚本结果排序（按随机数升序）
            ScriptSortBuilder scriptSortBuilder = new ScriptSortBuilder(script, ScriptSortBuilder.ScriptSortType.NUMBER);
            scriptSortBuilder.order(SortOrder.ASC);
            searchSourceBuilder.sort(scriptSortBuilder);
            // 执行ES查询并将结果映射到programListVoList中
            businessEsHandle.executeQuery(
                    // ES目标索引名
                    SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
                    ProgramDocumentParamName.INDEX_TYPE,  // 文档类型（ES 7+可省略）
                    programListVoList,  // 结果存储列表
                    null,   // 高亮配置
                    ProgramListVo.class,   // 结果映射的实体类
                    searchSourceBuilder,  // 构建好的查询条件
                    null     // 聚合配置
            );
        } catch (Exception e) {
            log.error("recommendList error", e);
        }
        return programListVoList;
    }

    /**
     * es根据条件分页查询节目列表
     *
     * @param programPageListDto 节目分页查询入参对象
     * @return
     */
    public PageVo<ProgramListVo> selectPage(ProgramPageListDto programPageListDto) {
        // 初始化分页结果对象，用于封装返回数据
        PageVo<ProgramListVo> pageVo = new PageVo<>();
        try {
            // 构建ES查询条件列表，用于组装多条件查询参数
            List<EsDataQueryDto> esDataQueryDtoList = new ArrayList<>();
            // 处理地区ID查询条件
            if (Objects.nonNull(programPageListDto.getAreaId())) {
                // 若传入地区ID，则添加地区精确匹配条件
                EsDataQueryDto areaIdQueryDto = new EsDataQueryDto();
                // 设置ES文档中的地区ID字段名
                areaIdQueryDto.setParamName(ProgramDocumentParamName.AREA_ID);
                // 设置查询的地区ID值
                areaIdQueryDto.setParamValue(programPageListDto.getAreaId());
                esDataQueryDtoList.add(areaIdQueryDto);
            } else {
                // 若未传入地区ID，则默认查询热门精选节目
                EsDataQueryDto primeQueryDto = new EsDataQueryDto();
                // 设置ES文档中的热门标识字段名
                primeQueryDto.setParamName(ProgramDocumentParamName.PRIME);
                // 设置为"是"的标识值
                primeQueryDto.setParamValue(BusinessStatus.YES.getCode());
                esDataQueryDtoList.add(primeQueryDto);
            }
            // 处理父节目分类ID查询条件
            if (Objects.nonNull(programPageListDto.getParentProgramCategoryId())) {
                // 若传入父节目分类ID，则添加父节目分类ID精确匹配条件
                EsDataQueryDto parentProgramCategoryIdQueryDto = new EsDataQueryDto();
                // 父分类字段名
                parentProgramCategoryIdQueryDto.setParamName(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID);
                // 父分类ID值
                parentProgramCategoryIdQueryDto.setParamValue(programPageListDto.getParentProgramCategoryId());
                esDataQueryDtoList.add(parentProgramCategoryIdQueryDto);
            }
            // 处理节目分类ID查询条件
            if (Objects.nonNull(programPageListDto.getProgramCategoryId())) {
                // 若传入子分类ID，则添加子分类精确匹配条件
                EsDataQueryDto programCategoryIdQueryDto = new EsDataQueryDto();
                // 子分类字段名
                programCategoryIdQueryDto.setParamName(ProgramDocumentParamName.PROGRAM_CATEGORY_ID);
                // 子分类ID值
                programCategoryIdQueryDto.setParamValue(programPageListDto.getProgramCategoryId());
                esDataQueryDtoList.add(programCategoryIdQueryDto);
            }
            // 处理时间范围查询条件
            if (Objects.nonNull(programPageListDto.getStartDateTime()) &&
                    Objects.nonNull(programPageListDto.getEndDateTime())) {
                // 若同时传入开始时间和结束时间，则添加时间范围查询条件
                EsDataQueryDto showDayTimeQueryDto = new EsDataQueryDto();
                // 演出时间字段名
                showDayTimeQueryDto.setParamName(ProgramDocumentParamName.SHOW_DAY_TIME);
                // 时间范围起始值
                showDayTimeQueryDto.setStartTime(programPageListDto.getStartDateTime());
                // 时间范围结束值
                showDayTimeQueryDto.setEndTime(programPageListDto.getEndDateTime());
                esDataQueryDtoList.add(showDayTimeQueryDto);
            }
            // 获取排序参数（排序字段和排序方向）
            ProgramPageOrder programPageOrder = getProgramPageOrder(programPageListDto);
            // 调用ES处理工具执行分页查询
            PageInfo<ProgramListVo> programListVoPageInfo = businessEsHandle.queryPage(
                    // 构建ES索引名（包含前缀和基础索引名）
                    SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
                    ProgramDocumentParamName.INDEX_TYPE,  // ES文档类型
                    esDataQueryDtoList,   // 查询条件列表
                    programPageOrder.sortParam,   // 排序字段
                    programPageOrder.sortOrder,  // 排序方向（升序/降序）
                    programPageListDto.getPageNumber(),  // 页码
                    programPageListDto.getPageSize(),  // 每页条数
                    ProgramListVo.class  // 返回结果的类型
            );
            // 将查询结果转换为统一的分页VO对象
            pageVo = PageUtil.convertPage(programListVoPageInfo, programListVo -> programListVo);
        } catch (Exception e) {
            log.error("selectPage error", e);
        }
        // 返回分页结果
        return pageVo;
    }

    /**
     * 根据节目查询条件中的类型，获取对应的分页排序参数
     * 该方法通过判断入参中的类型值，确定节目列表的排序字段和排序方向，
     *
     * @param programPageListDto
     * @return
     */
    public ProgramPageOrder getProgramPageOrder(ProgramPageListDto programPageListDto) {
        // 初始化排序参数对象
        ProgramPageOrder programPageOrder = new ProgramPageOrder();
        // 根据类型type确定排序规则
        switch (programPageListDto.getType()) {
            // 类型为2：推荐排序，按节目热度（HIGH_HEAT）降序排列（热度高的在前）
            case 2:
                // 排序字段：热度
                programPageOrder.sortParam = ProgramDocumentParamName.HIGH_HEAT;
                // 排序方向：降序
                programPageOrder.sortOrder = SortOrder.DESC;
                break;
            // 类型为3：最近开场，按节目开场时间（SHOW_TIME）升序排列（时间早的在前）
            case 3:
                // 排序字段：开场时间
                programPageOrder.sortParam = ProgramDocumentParamName.SHOW_TIME;
                // 排序方向：升序
                programPageOrder.sortOrder = SortOrder.ASC;
                break;
            // 类型为4：最新上架，按节目发布时间（ISSUE_TIME）降序排列（最新发布的在前）
            case 4:
                // 排序字段：发布时间
                programPageOrder.sortParam = ProgramDocumentParamName.ISSUE_TIME;
                // 排序方向：降序
                programPageOrder.sortOrder = SortOrder.DESC;
                break;
            // 默认情况：相关度排序，不指定具体排序字段和方向（使用ES默认的相关度排序）
            default:
                programPageOrder.sortParam = null;
                programPageOrder.sortOrder = null;
        }
        return programPageOrder;
    }

    /**
     * ES节目搜索功能
     * 根据传入的搜索条件构建ES查询，并对节目标题和演员进行全文检索，同时支持排序和分页，最终返回格式化的分页结果。
     *
     * @param programSearchDto
     * @return
     */
    public PageVo<ProgramListVo> search(ProgramSearchDto programSearchDto) {
        // 初始化分页结果对象
        PageVo<ProgramListVo> pageVo = new PageVo<>();
        try {
            // 1.构建ES的布尔查询（BoolQuery），用于组合多条件查询
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            // 1.1 按地区ID筛选（精确匹配）
            if (Objects.nonNull(programSearchDto.getAreaId())) {
                QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.AREA_ID,
                        programSearchDto.getAreaId());
                boolQuery.must(builds);  // must表示"必须满足该条件"
            }
            // 1.2 按父节目分类ID筛选（精确匹配）
            if (Objects.nonNull(programSearchDto.getParentProgramCategoryId())) {
                QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID,
                        programSearchDto.getParentProgramCategoryId());
                boolQuery.must(builds);
            }
            // 1.3 按时间范围筛选（节目播出日期）
            if (Objects.nonNull(programSearchDto.getStartDateTime()) &&
                    Objects.nonNull(programSearchDto.getEndDateTime())) {
                // 构建范围查询，包含起始时间（includeLower=true）
                QueryBuilder builds = QueryBuilders.rangeQuery(ProgramDocumentParamName.SHOW_DAY_TIME)
                        .from(programSearchDto.getStartDateTime())
                        .to(programSearchDto.getEndDateTime())
                        .includeLower(true);
                boolQuery.must(builds);
            }
            // 1.4 全文检索（标题和演员字段）
            if (StringUtil.isNotEmpty(programSearchDto.getContent())) {
                // 内部布尔查询，用于组合"或"条件
                BoolQueryBuilder innerBoolQuery = QueryBuilders.boolQuery();
                // should表示"满足任一条件即可"：匹配标题 或 匹配演员
                innerBoolQuery.should(QueryBuilders.matchQuery(ProgramDocumentParamName.TITLE, programSearchDto.getContent()));
                innerBoolQuery.should(QueryBuilders.matchQuery(ProgramDocumentParamName.ACTOR, programSearchDto.getContent()));
                innerBoolQuery.minimumShouldMatch(1);  // 至少满足一个should条件
                boolQuery.must(innerBoolQuery);  // 将内部查询作为必须满足的条件
            }
            // 2. 构建ES搜索源（SearchSourceBuilder），封装查询条件、排序、分页等
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            // 2.1 设置排序参数（通过getProgramPageOrder方法获取排序字段和方向）
            ProgramPageOrder programPageOrder = getProgramPageOrder(programSearchDto);
            if (Objects.nonNull(programPageOrder.sortParam) && Objects.nonNull(programPageOrder.sortOrder)) {
                FieldSortBuilder sort = SortBuilders.fieldSort(programPageOrder.sortParam);
                sort.order(programPageOrder.sortOrder);  // 设置排序方向（升序/降序）
                searchSourceBuilder.sort(sort);
            }
            // 2.2 配置查询条件
            searchSourceBuilder.query(boolQuery);
            // 2.3 开启总命中数追踪（确保获取准确的总条数，ES默认超过1万条可能不准确）
            searchSourceBuilder.trackTotalHits(true);
            // 2.4 设置分页参数（from：起始位置，size：每页条数）
            searchSourceBuilder.from((programSearchDto.getPageNumber() - 1) * programSearchDto.getPageSize());
            searchSourceBuilder.size(programSearchDto.getPageSize());
            // 2.5 配置高亮显示（对匹配的标题和演员字段添加高亮标记）
            searchSourceBuilder.highlighter(getHighlightBuilder(Arrays.asList(
                    ProgramDocumentParamName.TITLE,
                    ProgramDocumentParamName.ACTOR
            )));
            // 3. 执行ES查询并处理结果
            List<ProgramListVo> list = new ArrayList<>();  // 用于存储查询结果
            PageInfo<ProgramListVo> pageInfo = new PageInfo<>(list);  // 分页信息载体
            pageInfo.setPageNum(programSearchDto.getPageNumber());  // 设置当前页码
            pageInfo.setPageSize(programSearchDto.getPageSize());   // 设置每页条数
            // 调用ES处理工具执行查询，将结果填充到list和pageInfo中
            businessEsHandle.executeQuery(
                    // 构建ES索引名（包含前缀和基础索引名）
                    SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
                    ProgramDocumentParamName.INDEX_TYPE,   // 文档类型
                    list,    // 用于接收查询结果的列表
                    pageInfo,  // 用于接收分页信息（总条数等）
                    ProgramListVo.class,   // 结果转换的目标类型
                    searchSourceBuilder,  // 构建好的搜索条件
                    Arrays.asList(ProgramDocumentParamName.TITLE, ProgramDocumentParamName.ACTOR)  // 需要高亮的字段
            );
            // 4. 将PageInfo转换为统一的分页VO对象
            pageVo = PageUtil.convertPage(pageInfo, programListVo -> programListVo);
        } catch (Exception e) {
            log.error("search error", e);
        }
        // 返回分页结果（异常时返回空分页对象）
        return pageVo;
    }

    public HighlightBuilder getHighlightBuilder(List<String> fieldNameList) {
        // 创建一个HighlightBuilder
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        for (String fieldName : fieldNameList) {
            // 为特定字段添加高亮设置
            HighlightBuilder.Field highlightTitle = new HighlightBuilder.Field(fieldName);
            highlightTitle.preTags("<em>");
            highlightTitle.postTags("</em>");
            highlightBuilder.field(highlightTitle);
        }
        return highlightBuilder;
    }

    /**
     * 根据节目ID从ES中删除无效节目的相关文档
     *
     * @param programId 节目ID，用于定位需要删除的ES文档
     */
    public void deleteByProgramId(Long programId) {
        try {
            // 1. 构建查询条件：根据节目ID查询对应的ES文档
            List<EsDataQueryDto> esDataQueryDtoList = new ArrayList<>();
            EsDataQueryDto programIdDto = new EsDataQueryDto();
            programIdDto.setParamName(ProgramDocumentParamName.ID); // 查询字段：节目ID
            programIdDto.setParamValue(programId);  // 查询值：目标节目ID
            esDataQueryDtoList.add(programIdDto);
            // 2. 拼接ES索引名称（包含前缀和区分符，如"prefix-program_index"）
            // 3. 执行查询：从ES中查询该节目ID对应的文档列表
            List<ProgramListVo> programListVos =
                    businessEsHandle.query(
                            // ES索引名
                            SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
                            ProgramDocumentParamName.INDEX_TYPE,   // 文档类型（ES 7+后可省略）
                            esDataQueryDtoList,   // 查询条件
                            ProgramListVo.class);   // 结果映射的实体类
            // 4. 若查询到文档，循环删除
            if (CollectionUtil.isNotEmpty(programListVos)) {
                for (ProgramListVo programListVo : programListVos) {
                    // 根据文档ID删除ES中的记录
                    businessEsHandle.deleteByDocumentId(
                            // 目标索引
                            SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
                            programListVo.getEsId());  // 文档唯一ID（从查询结果中获取）
                }
            }
        } catch (Exception e) {
            log.error("deleteByProgramId error", e);
        }
    }
}
