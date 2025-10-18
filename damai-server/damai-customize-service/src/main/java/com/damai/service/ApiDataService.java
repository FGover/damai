package com.damai.service;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RepeatExecuteLimitConstants;
import com.damai.util.StringUtil;
import com.damai.dto.ApiDataDto;
import com.damai.entity.ApiData;
import com.damai.mapper.ApiDataMapper;
import com.damai.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.damai.vo.ApiDataVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: api调用记录 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class ApiDataService extends ServiceImpl<ApiDataMapper, ApiData> {

    @Autowired
    private ApiDataMapper apiDataMapper;

    /**
     * 保存接口数据到数据库，带有防重复重复幂等性处理
     * 防止因消息重复消费导致的数据重复存储
     *
     * @param apiData 接口数据实体（包含限流日志等信息）
     */
    // @RepeatExecuteLimit注解：自定义的重复执行限制注解，用于实现方法级别的幂等性控制
    // name：标识当前限流场景（此处为消费接口数据消息的场景）
    // keys：用于生成唯一键的参数（此处使用apiData的id，确保同一id的记录只被保存一次）
    @RepeatExecuteLimit(name = RepeatExecuteLimitConstants.CONSUMER_API_DATA_MESSAGE, keys = {"#apiData.id"})
    public void saveApiData(ApiData apiData) {
        // 1. 先查询数据库中是否已存在该id的记录
        ApiData dbApiData = apiDataMapper.selectById(apiData.getId());
        // 2. 若不存在，则执行插入操作；若已存在，则忽略（避免重复存储）
        // 组件只能防止并发重复，并不能防止业务重复
        if (Objects.isNull(dbApiData)) {
            log.info("saveApiData apiData:{}", JSON.toJSONString(apiData));
            apiDataMapper.insert(apiData);
        }
    }

    public Page<ApiDataVo> pageList(final ApiDataDto dto) {
        Page<ApiData> page = Page.of(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<ApiData> queryWrapper = Wrappers.lambdaQuery(ApiData.class)
                .eq(StringUtil.isNotEmpty(dto.getApiAddress()), ApiData::getApiAddress, dto.getApiAddress())
                .eq(StringUtil.isNotEmpty(dto.getApiUrl()), ApiData::getApiUrl, dto.getApiUrl())
                .ge(Objects.nonNull(dto.getStartDate()), ApiData::getCreateTime, dto.getStartDate())
                .le(Objects.nonNull(dto.getEndDate()), ApiData::getCreateTime, dto.getEndDate());
        Page<ApiData> apiDataPage = apiDataMapper.selectPage(page, queryWrapper);
        List<ApiData> apiDataList = apiDataPage.getRecords();
        Page<ApiDataVo> apiDataPageVo = new Page<>();
        BeanUtils.copyProperties(apiDataPage, apiDataPageVo);
        List<ApiDataVo> apiDataVoList = new ArrayList<>();
        if (CollUtil.isNotEmpty(apiDataList)) {
            apiDataVoList = apiDataList.stream().map(apiData -> {
                ApiDataVo apiDataVo = new ApiDataVo();
                BeanUtils.copyProperties(apiData, apiDataVo);
                return apiDataVo;
            }).collect(Collectors.toList());
        }
        apiDataPageVo.setRecords(apiDataVoList);
        return apiDataPageVo;
    }
}
