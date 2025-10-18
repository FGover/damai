package com.damai.util;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.damai.dto.EsDataQueryDto;
import com.damai.dto.EsDocumentMappingDto;
import com.damai.dto.EsGeoPointDto;
import com.damai.dto.EsGeoPointSortDto;
import com.github.pagehelper.PageInfo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.GeoDistanceQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: Elasticsearch业务操作工具类
 * 封装了Elasticsearch的核心操作，包括索引创建、索引检查、文档CRUD、条件查询（含地理坐标查询、排序、分页等）
 * 支持通过开关控制ES功能启用状态，兼容不同ES版本的索引类型（Type）处理
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
public class BusinessEsHandle {

    // Elasticsearch REST客户端，用于发送HTTP请求与ES交互
    private final RestClient restClient;
    // ES功能总开关：true启用ES操作，false则所有ES操作不执行
    private final Boolean esSwitch;
    // 索引类型开关：true表示使用ES旧版本的Type机制，false则使用7.x+的_doc类型
    private final Boolean esTypeSwitch;

    /**
     * 创建Elasticsearch索引
     * 用于初始化索引结构，包含字段映射（mapping）和索引设置（settings）
     *
     * @param indexName 索引名称（相当于数据库表名）
     * @param indexType 索引类型（ES 6.x及之前版本用于区分表内数据类别，7.x后逐渐废弃）
     * @param list      字段映射配置列表，包含字段名和字段类型（如text、long、date等）
     */
    public void createIndex(String indexName, String indexType, List<EsDocumentMappingDto> list) throws IOException {
        // 若ES功能未启用，直接返回
        if (!esSwitch) {
            return;
        }
        // 若字段映射配置为空，无需创建索引
        if (CollectionUtil.isEmpty(list)) {
            return;
        }
        // 构建索引请求
        IndexRequest indexRequest = new IndexRequest();
        // 开始构建JSON格式的索引配置：包含mappings（字段映射）和settings（分片/副本配置）
        XContentBuilder builder = JsonXContent.contentBuilder()
                .startObject()
                .startObject("mappings");  // 字段映射配置
        // 若启用Type机制，需包裹在类型节点下
        if (esTypeSwitch) {
            builder = builder.startObject(indexType);
        }
        // 配置字段属性
        builder = builder.startObject("properties");
        for (EsDocumentMappingDto esDocumentMappingDto : list) {
            String paramName = esDocumentMappingDto.getParamName();  // 字段名
            String paramType = esDocumentMappingDto.getParamType();  // 字段类型
            // 特殊处理text类型：默认分词，同时添加keyword子字段用于精确查询（不分词）
            if ("text".equals(paramType)) {
                // 构建text字段的子字段配置：keyword 类型，忽略长度超过256的字符
                Map<String, Map<String, Object>> fieldsMap = new HashMap<>(8);
                Map<String, Object> keywordMap = new HashMap<>(8);
                keywordMap.put("type", "keyword");
                keywordMap.put("ignore_above", 256);  // 超过256字符的内容不索引
                fieldsMap.put("keyword", keywordMap);
                builder = builder.startObject(paramName)
                        .field("type", "text")  // 主类型为text（支持分词查询）
                        .field("fields", fieldsMap)  // 附加keyword子字段（支持精确匹配）
                        .endObject();
            } else {
                // 非text类型（如long、date等）直接配置类型
                builder = builder.startObject(paramName).field("type", paramType).endObject();
            }
        }
        // 关闭Type节点（若启用）
        if (esTypeSwitch) {
            builder.endObject();
        }
        // 配置索引设置：分片数和副本数
        builder = builder.endObject()
                .endObject()    // 关闭properties节点
                .startObject("settings")   // 关闭mappings节点
                .field("number_of_shards", 3)   // 主分片数：3个（影响数据分布和并行查询能力）
                .field("number_of_replicas", 1)  // 副本数：1个（用于容错和负载均衡）
                .endObject()  // 关闭settings节点
                .endObject();  // 关闭根节点
        // 生成索引创建的JSON字符串并日志打印
        indexRequest.source(builder);
        String source = indexRequest.source().utf8ToString();
        log.info("create index execute dsl : {}", source);
        // 将JSON字符串封装成HTTP请求体（NStringEntity 表示基于字符串的请求体）
        HttpEntity entity = new NStringEntity(source, ContentType.APPLICATION_JSON);
        // 使用低级 RestClient 手工构造一个 HTTP PUT 请求，目标是创建指定名称的索引
        Request request = new Request("PUT", "/" + indexName);
        request.setEntity(entity);  // 设置请求体
        // 设置额外参数（这里为空）
        request.addParameters(Collections.emptyMap());
        // 发送 HTTP 请求执行索引创建操作
        restClient.performRequest(request);
    }

    /**
     * 检查索引是否存在
     *
     * @param indexName 索引名称
     * @param indexType 索引类型
     * @return true表示索引存在，false表示不存在或查询失败
     */
    public boolean checkIndex(String indexName, String indexType) {
        // 若ES功能未启用，直接返回不存在
        if (!esSwitch) {
            return false;
        }
        try {
            // 根据Type开关构建查询路径：查询索引的映射信息
            String path;
            if (esTypeSwitch) {
                // 旧版本路径：包含Type和类型名称参数
                path = "/" + indexName + "/" + indexType + "/_mapping?include_type_name";
            } else {
                // 新版本路径：直接查询索引映射
                path = "/" + indexName + "/_mapping";
            }
            // 构建HTTP GET请求
            Request request = new Request("GET", path);
            // 设置额外参数（这里为空）
            request.addParameters(Collections.emptyMap());
            // 发送GET请求查询映射
            Response response = restClient.performRequest(request);
            // 解析响应：状态码为200且描述为OK表示索引存在
            String result = EntityUtils.toString(response.getEntity());
            System.out.println(JSON.toJSONString(result));
            return "OK".equals(response.getStatusLine().getReasonPhrase());
        } catch (Exception e) {
            // 处理"索引不存在"的异常（404状态）
            if (e instanceof ResponseException && ((ResponseException) e).getResponse()
                    .getStatusLine().getStatusCode() == RestStatus.NOT_FOUND.getStatus()) {
                log.warn("index not exist ! indexName:{}, indexType:{}", indexName, indexType);
            } else {
                // 其他异常（如连接失败）记录错误日志
                log.error("checkIndex error", e);
            }
            return false;
        }
    }

    /**
     * 删除索引
     *
     * @param indexName 索引名称
     */
    public void deleteIndex(String indexName) {
        // 若ES功能未启用，直接返回
        if (!esSwitch) {
            return;
        }
        try {
            // 发送DELETE请求删除索引
            Request request = new Request("DELETE", "/" + indexName);
            request.addParameters(Collections.emptyMap());
            Response response = restClient.performRequest(request);
            // 响应状态为OK表示删除成功
            response.getStatusLine().getReasonPhrase();
        } catch (Exception e) {
            log.error("deleteIndex error", e);
        }
    }

    /**
     * 清空索引下所有数据（通过删除索引实现，适用于全量数据重建场景）
     *
     * @param indexName 要清空的索引名称
     */
    public void deleteData(String indexName) {
        // 若ES功能未启用，直接返回
        if (!esSwitch) {
            return;
        }
        // 调用删除索引方法（删除后如需使用需重新创建）
        deleteIndex(indexName);
    }

    /**
     * 向索引添加文档（使用ES自动生成的文档ID）
     *
     * @param indexName 索引名字
     * @param indexType 索引类型
     * @param params    文档字段键值对（如{id:1, name:"演唱会"}）
     * @return true表示添加成功，false表示失败或未执行
     */
    public boolean add(String indexName, String indexType, Map<String, Object> params) {
        // 调用重载方法，文档ID传null（由ES自动生成）
        return add(indexName, indexType, params, null);
    }

    /**
     * 向索引添加文档（指定文档ID）
     *
     * @param indexName 索引名字
     * @param indexType 索引类型
     * @param params    文档字段键值对
     * @param id        文档id 如果为空，则使用es默认id
     * @return true表示添加成功，false表示失败或未执行
     */
    public boolean add(String indexName, String indexType, Map<String, Object> params, String id) {
        // 若ES功能未启用或参数为空，直接返回false
        if (!esSwitch || (CollectionUtil.isEmpty(params))) {
            return false;
        }
        try {
            // 将文档参数转换为JSON字符串
            String jsonString = JSON.toJSONString(params);
            // 构建请求路径：根据Type开关和是否指定ID拼接
            HttpEntity entity = new NStringEntity(jsonString, ContentType.APPLICATION_JSON);
            String endpoint;
            if (esTypeSwitch) {
                // 旧版本路径：/索引/类型
                endpoint = "/" + indexName + "/" + indexType;
            } else {
                // 新版本路径：/索引/_doc
                endpoint = "/" + indexName + "/_doc";
            }
            // 若指定了文档ID，路径追加ID（如/索引/_doc/1）
            if (StringUtil.isNotEmpty(id)) {
                endpoint = endpoint + "/" + id;
            }
            log.info("add dsl : {}", jsonString);
            // 发送POST请求添加文档（若指定ID则相当于PUT更新）
            Request request = new Request("POST", endpoint);
            request.setEntity(entity);
            request.addParameters(Collections.emptyMap());
            Response indexResponse = restClient.performRequest(request);
            // 响应状态为created（新文档）或 ok（更新文档）表示添加成功
            String reasonPhrase = indexResponse.getStatusLine().getReasonPhrase();
            return "created".equalsIgnoreCase(reasonPhrase) || "ok".equalsIgnoreCase(reasonPhrase);
        } catch (Exception e) {
            log.error("add error", e);
            return false;
        }
    }

    /**
     * 查询基础方法（无地理条件、无排序）
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esDataQueryDtoList 查询条件列表（如字段等值、范围查询）
     * @param clazz              返回结果的目标类型（用于JSON反序列化）
     * @return 符合条件的文档列表（空列表表示无结果或未执行）
     */
    public <T> List<T> query(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList,
                             Class<T> clazz) throws IOException {
        if (!esSwitch) {
            return new ArrayList<>();
        }
        // 调用全参查询方法，传递空值忽略地理条件和排序
        return query(indexName, indexType, null, esDataQueryDtoList, null,
                null, null, null, null, clazz);
    }

    /**
     * 带地理坐标条件的查询方法
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esGeoPointDto      经纬度查询参数
     * @param esDataQueryDtoList 普通查询条件列表
     * @param clazz              返回的类型
     * @return 符合条件的文档列表
     */
    public <T> List<T> query(String indexName, String indexType, EsGeoPointDto esGeoPointDto,
                             List<EsDataQueryDto> esDataQueryDtoList, Class<T> clazz) throws IOException {
        if (!esSwitch) {
            return new ArrayList<>();
        }
        // 调用全参查询方法，忽略排序条件
        return query(indexName, indexType, esGeoPointDto, esDataQueryDtoList, null,
                null, null, null, null, clazz);
    }

    /**
     * 带普通字段排序的查询方法
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esDataQueryDtoList 普通查询条件列表
     * @param sortParam          普通参数排序 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder          排序方向（ASC升序/DESC降序，null默认降序）
     * @param clazz              返回结果的目标类型
     * @return 符合条件的文档列表（按指定字段排序）
     */
    public <T> List<T> query(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList,
                             String sortParam, SortOrder sortOrder, Class<T> clazz) throws IOException {
        if (!esSwitch) {
            return new ArrayList<>();
        }
        // 调用全参查询方法，忽略地理条件和地理排序
        return query(indexName, indexType, null, esDataQueryDtoList, sortParam,
                null, sortOrder, null, null, clazz);
    }

    /**
     * 带地理距离排序的查询方法
     *
     * @param indexName            索引名字
     * @param indexType            索引类型
     * @param esDataQueryDtoList   普通查询条件列表
     * @param geoPointDtoSortParam 经纬度參數排序 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder            升序还是降序，为空则降序
     * @param clazz                返回的类型
     * @return 符合条件的文档列表（按与目标点的距离排序）
     */
    public <T> List<T> query(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList, EsGeoPointSortDto geoPointDtoSortParam, SortOrder sortOrder, Class<T> clazz) throws IOException {
        if (!esSwitch) {
            return new ArrayList<>();
        }
        // 调用全参查询方法，忽略普通排序和地理查询条件
        return query(indexName, indexType, null, esDataQueryDtoList, null, geoPointDtoSortParam, sortOrder, null, null, clazz);
    }


    /**
     * 全参数查询方法（支持地理查询、多条件筛选、多类型排序、分页游标）
     * 是所有查询方法的底层实现，支持复杂查询场景
     *
     * @param indexName            索引名字
     * @param indexType            索引类型
     * @param esGeoPointDto        经纬度查询参数
     * @param esDataQueryDtoList   普通查询条件列表
     * @param sortParam            普通參數排序 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param geoPointDtoSortParam 经纬度參數排序 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder            排序方向（默认DESC）
     * @param pageSize             searchAfterSort搜索的页大小
     * @param searchAfterSort      上一页最后一条数据的排序值（用于游标分页，避免深度分页问题）
     * @param clazz                返回的类型
     * @return List
     */
    public <T> List<T> query(String indexName, String indexType, EsGeoPointDto esGeoPointDto, List<EsDataQueryDto> esDataQueryDtoList, String sortParam, EsGeoPointSortDto geoPointDtoSortParam, SortOrder sortOrder, Integer pageSize, Object[] searchAfterSort, Class<T> clazz) throws IOException {
        List<T> list = new ArrayList<>();
        // 若ES功能未启用，返回空列表
        if (!esSwitch) {
            return list;
        }
        // 构建查询条件（包含筛选、排序）
        SearchSourceBuilder sourceBuilder = getSearchSourceBuilder(esGeoPointDto, esDataQueryDtoList,
                sortParam, geoPointDtoSortParam, sortOrder);
        // 执行查询并处理结果
        executeQuery(indexName, indexType, list, null, clazz, sourceBuilder, null);
        return list;
    }


    /**
     * 基础分页查询方法（无地理条件、无排序）
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esDataQueryDtoList 普通查询条件列表
     * @param pageNo             页码
     * @param pageSize           页大小
     * @param clazz              返回的类型
     * @return 分页结果对象，包含当前页数据、总条数、总页数等
     */
    public <T> PageInfo<T> queryPage(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList,
                                     Integer pageNo, Integer pageSize, Class<T> clazz) throws IOException {
        return queryPage(indexName, indexType, esDataQueryDtoList, null, null, pageNo, pageSize, clazz);
    }

    /**
     * 带普通排序的分页查询方法
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esDataQueryDtoList 普通查询条件列表
     * @param sortParam          排序参数 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder          升序还是降序，为空则降序
     * @param pageNo             页码
     * @param pageSize           页大小
     * @param clazz              返回的类型
     * @return PageInfo
     */
    public <T> PageInfo<T> queryPage(
            String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList, String sortParam,
            SortOrder sortOrder, Integer pageNo, Integer pageSize, Class<T> clazz) throws IOException {
        return queryPage(indexName, indexType, null, esDataQueryDtoList, sortParam,
                null, sortOrder, pageNo, pageSize, clazz);
    }

    /**
     * 带地理条件的分页查询方法（无排序）
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esGeoPointDto      经纬度查询参数
     * @param esDataQueryDtoList 普通查询条件列表
     * @param pageNo             页码
     * @param pageSize           页大小
     * @param clazz              返回的类型
     * @return PageInfo
     */
    public <T> PageInfo<T> queryPage(
            String indexName, String indexType, EsGeoPointDto esGeoPointDto, List<EsDataQueryDto> esDataQueryDtoList,
            Integer pageNo, Integer pageSize, Class<T> clazz) throws IOException {
        return queryPage(indexName, indexType, esGeoPointDto, esDataQueryDtoList, null,
                null, null, pageNo, pageSize, clazz);
    }

    /**
     * 全参数分页查询方法（支持地理条件、多类型排序）
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esGeoPointDto      经纬度查询参数
     * @param esDataQueryDtoList 普通查询条件列表
     * @param sortParam          普通排序字段
     * @param sortOrder          升序还是降序，为空则降序
     * @param pageNo             页码
     * @param pageSize           页大小
     * @param clazz              返回的类型
     * @return
     * @throws IOException
     */
    public <T> PageInfo<T> queryPage(String indexName, String indexType, EsGeoPointDto esGeoPointDto,
                                     List<EsDataQueryDto> esDataQueryDtoList, String sortParam,
                                     EsGeoPointSortDto geoPointDtoSortParam, SortOrder sortOrder, Integer pageNo,
                                     Integer pageSize, Class<T> clazz) throws IOException {
        List<T> list = new ArrayList<>();
        PageInfo<T> pageInfo = new PageInfo<>(list);
        pageInfo.setPageNum(pageNo);  // 设置当前页码
        pageInfo.setPageSize(pageSize);  // 设置每页条数
        // 若ES功能未启用，返回空分页
        if (!esSwitch) {
            return pageInfo;
        }
        // 构建查询条件
        SearchSourceBuilder sourceBuilder = getSearchSourceBuilder(esGeoPointDto, esDataQueryDtoList, sortParam,
                geoPointDtoSortParam, sortOrder);
        // 设置分页参数：from（起始位置）=（页码 - 1）* 每页条数，
        sourceBuilder.from((pageNo - 1) * pageSize);
        sourceBuilder.size(pageSize);
        // 执行查询并填充分页数据
        executeQuery(indexName, indexType, list, pageInfo, clazz, sourceBuilder, null);
        return pageInfo;
    }

    /**
     * 构建查询条件构建器（SearchSourceBuilder）
     *
     * @param esGeoPointDto        地理坐标查询条件
     * @param esDataQueryDtoList   普通查询条件列表
     * @param sortParam            普通排序字段
     * @param geoPointDtoSortParam 地理排序参数
     * @param sortOrder            排序方向（默认DESC）
     * @return 构建好的查询条件对象
     */
    private SearchSourceBuilder getSearchSourceBuilder(EsGeoPointDto esGeoPointDto,
                                                       List<EsDataQueryDto> esDataQueryDtoList,
                                                       String sortParam,
                                                       EsGeoPointSortDto geoPointDtoSortParam,
                                                       SortOrder sortOrder) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        // 排序默认设为降序
        if (Objects.isNull(sortOrder)) {
            sortOrder = SortOrder.DESC;
        }
        // 如果排序字段不为空
        if (StringUtil.isNotEmpty(sortParam)) {
            // 添加普通字段排序
            FieldSortBuilder sort = SortBuilders.fieldSort(sortParam);
            sort.order(sortOrder);
            sourceBuilder.sort(sort);
        }
        // 如果地理坐标排序参数不为空
        if (Objects.nonNull(geoPointDtoSortParam)) {
            // 按"geoPoint"字段与目标经纬度的距离排序，单位为米
            GeoDistanceSortBuilder sort = SortBuilders.geoDistanceSort(
                    "geoPoint",   // 存储经纬度的字段名
                    geoPointDtoSortParam.getLatitude().doubleValue(),  // 目标纬度
                    geoPointDtoSortParam.getLongitude().doubleValue()  // 目标经度
            );
            sort.unit(DistanceUnit.METERS);  // 距离单位：米
            // 添加地理距离排序
            sort.order(sortOrder);
            sourceBuilder.sort(sort);
        }
        // 添加地理坐标筛选条件（如筛选用户周边10公里内的节目）
        if (Objects.nonNull(esGeoPointDto)) {
            // 构建地理距离查询：字段名、距离（这里设为最大Long值表示不限制距离，实际应通过业务参数控制）
            QueryBuilder geoQuery = new GeoDistanceQueryBuilder(esGeoPointDto.getParamName())
                    .distance(Long.MAX_VALUE, DistanceUnit.KILOMETERS)  // 距离：最大Long值（公里）
                    .point(esGeoPointDto.getLatitude().doubleValue(), esGeoPointDto.getLongitude().doubleValue()) // 中心点经纬度
                    .geoDistance(GeoDistance.PLANE);   // 距离计算方式：平面（快速但精度略低）
            sourceBuilder.query(geoQuery);
        }
        // 构建布尔查询处理普通筛选条件
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        for (EsDataQueryDto esDataQueryDto : esDataQueryDtoList) {
            String paramName = esDataQueryDto.getParamName();  // 字段名
            Object paramValue = esDataQueryDto.getParamValue();  // 字段值
            Date startTime = esDataQueryDto.getStartTime();   // 范围查询-开始时间
            Date endTime = esDataQueryDto.getEndTime();   // 范围查询-结束时间
            boolean analyse = esDataQueryDto.isAnalyse();  // 是否分词查询（true=match，false=term）
            // 处理等值/集合查询
            if (Objects.nonNull(paramValue)) {
                // 集合查询（如in查询）
                if (paramValue instanceof Collection<?> collection) {
                    if (analyse) {
                        // 分词集合查询：使用should组合多个match查询（或逻辑）
                        BoolQueryBuilder builds = QueryBuilders.boolQuery();
                        for (Object value : collection) {
                            builds.should(QueryBuilders.matchQuery(paramName, value));
                        }
                        boolQuery.must(builds);   // 必须满足其中一个条件
                    } else {
                        // 精确集合查询：terms查询（等于任意一个值）
                        QueryBuilder builds = QueryBuilders.termsQuery(paramName, (Collection<?>) paramValue);
                        boolQuery.must(builds);
                    }
                } else {
                    // 单值查询
                    QueryBuilder builds;
                    if (analyse) {
                        // 分词查询：match（适用于text字段，会对查询词分词）
                        builds = QueryBuilders.matchQuery(paramName, paramValue);
                    } else {
                        // 精确查询：term（适用于keyword/数字字段，不会对查询词分词）
                        builds = QueryBuilders.termQuery(paramName, paramValue);
                    }
                    boolQuery.must(builds);
                }
            }
            // 处理范围查询
            if (Objects.nonNull(startTime) || Objects.nonNull(endTime)) {
                QueryBuilder builds = QueryBuilders.rangeQuery(paramName)
                        .from(startTime)  // 起始值（包含）
                        .to(endTime)  // 结束值（包含）
                        .includeLower(true);  // 包含下界
                boolQuery.must(builds);
            }
        }
        // 启用总命中数计算（用于分页总条数）
        sourceBuilder.trackTotalHits(true);
        // 设置查询条件为布尔查询
        sourceBuilder.query(boolQuery);
        return sourceBuilder;
    }

    /**
     * 执行查询并处理响应结果
     * 将ES返回的JSON响应转换为目标实体类，并填充到结果列表或分页对象中
     *
     * @param indexName              索引名称
     * @param indexType              索引类型
     * @param resultList             存储查询结果的列表
     * @param pageInfo               分页对象（可为null，非分页查询时不用）
     * @param clazz                  目标实体类类型
     * @param sourceBuilder          查询条件构建器
     * @param highLightFieldNameList 高亮字段列表（用于关键字高亮显示）
     * @param <T>
     * @throws IOException
     */
    public <T> void executeQuery(String indexName, String indexType, List<T> resultList, PageInfo<T> pageInfo,
                                 Class<T> clazz, SearchSourceBuilder sourceBuilder,
                                 List<String> highLightFieldNameList) throws IOException {
        // 构建查询DSL字符串
        String queryDsl = sourceBuilder.toString();
        log.info("query execute query dsl : {}", queryDsl);
        // 构建请求路径
        StringBuilder endpointStringBuilder = new StringBuilder("/" + indexName);
        if (esTypeSwitch) {
            // 旧版本路径：/索引/类型/_search
            endpointStringBuilder.append("/").append(indexType).append("/_search");
        } else {
            // 新版本路径：/索引/_search
            endpointStringBuilder.append("/_search");
        }
        String endpoint = endpointStringBuilder.toString();
        // 发送POST请求执行查询
        HttpEntity entity = new NStringEntity(queryDsl, ContentType.APPLICATION_JSON);
        Request request = new Request("POST", endpoint);
        request.setEntity(entity);
        request.addParameters(Collections.emptyMap());
        Response response = restClient.performRequest(request);
        // 解析响应结果
        String responseJson = EntityUtils.toString(response.getEntity());
        if (StringUtil.isEmpty(responseJson)) {
            return;
        }
        JSONObject resultObj = JSONObject.parseObject(responseJson);
        if (Objects.isNull(resultObj)) {
            return;
        }
        // 提取命中数据（hits）
        JSONObject hitsObj = resultObj.getJSONObject("hits");
        if (Objects.isNull(hitsObj)) {
            return;
        }
        // 处理总条数（用于分页）
        if (Objects.nonNull(pageInfo)) {
            Long total;
            if (esTypeSwitch) {
                // 旧版本总条数直接在 hits.total
                total = hitsObj.getLong("total");
            } else {
                // 新版本总条数在 hits.total.value
                JSONObject totalObj = hitsObj.getJSONObject("total");
                total = Objects.nonNull(totalObj) ? totalObj.getLong("value") : 0L;
            }
            // 设置总条数
            pageInfo.setTotal(total);
            // 计算总页数
            pageInfo.setPages((int) (total % pageInfo.getPageSize() == 0 ?
                    total / pageInfo.getPageSize() : total / pageInfo.getPageSize() + 1));
        }
        // 解析命中的文档列表
        JSONArray hitsArray = hitsObj.getJSONArray("hits");
        if (Objects.isNull(hitsArray) || hitsArray.isEmpty()) {
            return;
        }
        // 遍历文档，转换为目标实体类
        for (int i = 0, size = hitsArray.size(); i < size; i++) {
            JSONObject data = hitsArray.getJSONObject(i);
            if (Objects.isNull(data)) {
                continue;
            }
            // 文档原始数据（_source）
            JSONObject sourceObj = data.getJSONObject("_source");
            // 文档ID（_id）
            String esId = data.getString("_id");
            // 排序值（用于游标分页）
            JSONArray sortArray = data.getJSONArray("sort");
            // 高亮字段（如搜索关键字标红）
            JSONObject highlight = data.getJSONObject("highlight");
            // 填充排序值到结果中
            if (Objects.nonNull(sortArray) && !sortArray.isEmpty()) {
                // 取第一个排序值（通常只有一个排序字段）
                Long sort = sortArray.getLong(0);
                sourceObj.put("sort", sort);
            }
            // 填充高亮字段（覆盖原始字段值）
            if (Objects.nonNull(highlight) && Objects.nonNull(highLightFieldNameList)) {
                for (String highLightFieldName : highLightFieldNameList) {
                    JSONArray highLightFieldValue = highlight.getJSONArray(highLightFieldName);
                    if (Objects.nonNull(highLightFieldValue) && !highLightFieldValue.isEmpty()) {
                        // 用高亮值替换原始值（如"<em>演唱会</em>"替换"演唱会"）
                        sourceObj.put(highLightFieldName, highLightFieldValue.get(0));
                    }
                }
            }
            // 填充文档ID到结果中
            if (StringUtil.isNotEmpty(esId)) {
                sourceObj.put("esId", esId);
            }
            // 转换为目标实体类并添加到结果列表中
            resultList.add(JSONObject.parseObject(sourceObj.toJSONString(), clazz));
        }
    }

    /**
     * 根据文档ID删除指定文档
     *
     * @param index      索引名称
     * @param documentId 文档ID（_id）
     */
    public void deleteByDocumentId(String index, String documentId) {
        // 若ES功能未启用，直接返回
        if (!esSwitch) {
            return;
        }
        try {
            // 发送DELETE请求删除文档：路径为/索引/_doc/文档ID
            Request request = new Request("DELETE", "/" + index + "/_doc/" + documentId);
            request.addParameters(Collections.emptyMap());
            Response response = restClient.performRequest(request);
            log.info("deleteByDocumentId result : {}", response.getStatusLine().getReasonPhrase());
        } catch (Exception e) {
            log.error("deleteData error", e);
        }
    }
}
