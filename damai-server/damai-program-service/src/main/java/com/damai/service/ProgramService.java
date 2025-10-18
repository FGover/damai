package com.damai.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.BusinessThreadPool;
import com.damai.RedisStreamPushHandler;
import com.damai.client.BaseDataClient;
import com.damai.client.OrderClient;
import com.damai.client.UserClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.dto.AccountOrderCountDto;
import com.damai.dto.AreaGetDto;
import com.damai.dto.AreaSelectDto;
import com.damai.dto.ProgramAddDto;
import com.damai.dto.ProgramGetDto;
import com.damai.dto.ProgramInvalidDto;
import com.damai.dto.ProgramListDto;
import com.damai.dto.ProgramOperateDataDto;
import com.damai.dto.ProgramPageListDto;
import com.damai.dto.ProgramRecommendListDto;
import com.damai.dto.ProgramResetExecuteDto;
import com.damai.dto.ProgramSearchDto;
import com.damai.dto.TicketCategoryCountDto;
import com.damai.dto.TicketUserListDto;
import com.damai.entity.Program;
import com.damai.entity.ProgramCategory;
import com.damai.entity.ProgramGroup;
import com.damai.entity.ProgramJoinShowTime;
import com.damai.entity.ProgramShowTime;
import com.damai.entity.Seat;
import com.damai.entity.TicketCategory;
import com.damai.entity.TicketCategoryAggregate;
import com.damai.enums.BaseCode;
import com.damai.enums.BusinessStatus;
import com.damai.enums.CompositeCheckType;
import com.damai.enums.SellStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.initialize.impl.composite.CompositeContainer;
import com.damai.mapper.ProgramCategoryMapper;
import com.damai.mapper.ProgramGroupMapper;
import com.damai.mapper.ProgramMapper;
import com.damai.mapper.ProgramShowTimeMapper;
import com.damai.mapper.SeatMapper;
import com.damai.mapper.TicketCategoryMapper;
import com.damai.page.PageUtil;
import com.damai.page.PageVo;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.damai.service.cache.local.LocalCacheProgram;
import com.damai.service.cache.local.LocalCacheProgramCategory;
import com.damai.service.cache.local.LocalCacheProgramGroup;
import com.damai.service.cache.local.LocalCacheProgramShowTime;
import com.damai.service.cache.local.LocalCacheTicketCategory;
import com.damai.service.constant.ProgramTimeType;
import com.damai.service.es.ProgramEs;
import com.damai.service.lua.ProgramDelCacheData;
import com.damai.service.tool.TokenExpireManager;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.threadlocal.BaseParameterHolder;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import com.damai.util.StringUtil;
import com.damai.vo.AccountOrderCountVo;
import com.damai.vo.AreaVo;
import com.damai.vo.ProgramGroupVo;
import com.damai.vo.ProgramHomeVo;
import com.damai.vo.ProgramListVo;
import com.damai.vo.ProgramSimpleInfoVo;
import com.damai.vo.ProgramVo;
import com.damai.vo.TicketCategoryVo;
import com.damai.vo.TicketUserVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.constant.Constant.CODE;
import static com.damai.constant.Constant.USER_ID;
import static com.damai.core.DistributedLockConstants.GET_PROGRAM_LOCK;
import static com.damai.core.DistributedLockConstants.PROGRAM_GROUP_LOCK;
import static com.damai.core.DistributedLockConstants.PROGRAM_LOCK;
import static com.damai.core.RepeatExecuteLimitConstants.CANCEL_PROGRAM_ORDER;
import static com.damai.util.DateUtils.FORMAT_DATE;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目核心服务类
 * 负责节目的相关操作
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class ProgramService extends ServiceImpl<ProgramMapper, Program> {

    /**
     * 分布式唯一ID生成器
     */
    @Autowired
    private UidGenerator uidGenerator;

    /**
     * 节目数据库操作Mapper
     */
    @Autowired
    private ProgramMapper programMapper;

    /**
     * 节目组数据库操作Mapper
     */
    @Autowired
    private ProgramGroupMapper programGroupMapper;

    /**
     * 节目演出时间数据库操作Mapper
     */
    @Autowired
    private ProgramShowTimeMapper programShowTimeMapper;

    /**
     * 节目分类数据库操作Mapper
     */
    @Autowired
    private ProgramCategoryMapper programCategoryMapper;

    /**
     * 票档数据库操作Mapper
     */
    @Autowired
    private TicketCategoryMapper ticketCategoryMapper;

    /**
     * 座位数据库操作Mapper
     */
    @Autowired
    private SeatMapper seatMapper;

    /**
     * 基础数据服务远程调用客户端
     */
    @Autowired
    private BaseDataClient baseDataClient;

    /**
     * 用户服务远程调用客户端
     */
    @Autowired
    private UserClient userClient;

    /**
     * 订单服务远程调用客户端
     */
    @Autowired
    private OrderClient orderClient;

    /**
     * Redis缓存操作工具
     */
    @Autowired
    private RedisCache redisCache;

    /**
     * 自身服务引用（用于内部方法调用）
     *
     * @Lazy 延迟注入，解决循环依赖
     */
    @Lazy
    @Autowired
    private ProgramService programService;

    /**
     * 节目演出时间服务
     */
    @Autowired
    private ProgramShowTimeService programShowTimeService;

    /**
     * 票档服务
     */
    @Autowired
    private TicketCategoryService ticketCategoryService;

    /**
     * 节目分类服务
     */
    @Autowired
    private ProgramCategoryService programCategoryService;

    /**
     * Elasticsearch节目搜索服务
     */
    @Autowired
    private ProgramEs programEs;

    /**
     * 分布式锁工具类
     */
    @Autowired
    private ServiceLockTool serviceLockTool;

    /**
     * RedisStream消息推送处理器（用于数据同步）
     */
    @Autowired
    private RedisStreamPushHandler redisStreamPushHandler;

    /**
     * 节目本地缓存（内存级缓存）
     */
    @Autowired
    private LocalCacheProgram localCacheProgram;

    /**
     * 节目组本地缓存（内存级缓存）
     */
    @Autowired
    private LocalCacheProgramGroup localCacheProgramGroup;

    /**
     * 节目分类本地缓存（内存级缓存）
     */
    @Autowired
    private LocalCacheProgramCategory localCacheProgramCategory;

    /**
     * 节目演出时间本地缓存（内存级缓存）
     */
    @Autowired
    private LocalCacheProgramShowTime localCacheProgramShowTime;

    /**
     * 票档本地缓存（内存级缓存）
     */
    @Autowired
    private LocalCacheTicketCategory localCacheTicketCategory;

    /**
     * 组合模式容器（用于执行校验链）
     */
    @Autowired
    private CompositeContainer compositeContainer;

    /**
     * Token过期时间管理工具
     */
    @Autowired
    private TokenExpireManager tokenExpireManager;

    /**
     * 节目缓存删除工具（基于Lua脚本批量清理）
     */
    @Autowired
    private ProgramDelCacheData programDelCacheData;

    /**
     * 添加节目
     *
     * @param programAddDto 添加节目数据的入参
     * @return 添加节目后的id
     */
    public Long add(ProgramAddDto programAddDto) {
        Program program = new Program();
        BeanUtil.copyProperties(programAddDto, program);
        program.setId(uidGenerator.getUid());
        programMapper.insert(program);
        return program.getId();
    }

    /**
     * 搜索
     *
     * @param programSearchDto 搜索节目数据的入参
     * @return 执行后的结果
     */
    public PageVo<ProgramListVo> search(ProgramSearchDto programSearchDto) {
        //将入参的参数进行具体的组装
        setQueryTime(programSearchDto);
        return programEs.search(programSearchDto);
    }

    /**
     * 查询主页节目列表信息
     *
     * @param programListDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public List<ProgramHomeVo> selectHomeList(ProgramListDto programListDto) {
        // 1.优先从Elasticsearch查询
        List<ProgramHomeVo> programHomeVoList = programEs.selectHomeList(programListDto);
        // 2.判断Elasticsearch查询结果是否不为空
        if (CollectionUtil.isNotEmpty(programHomeVoList)) {
            // 若有结果，直接返回Elasticsearch中的数据
            return programHomeVoList;
        }
        // 3. 若Elasticsearch无结果或查询失败，降级查询数据库获取数据
        return dbSelectHomeList(programListDto);
    }

    /**
     * 数据库查询主页节目列表（ES降级方案）
     *
     * @param programPageListDto 查询节目数据的入参
     * @return 执行后的结果
     */
    private List<ProgramHomeVo> dbSelectHomeList(ProgramListDto programPageListDto) {
        List<ProgramHomeVo> programHomeVoList = new ArrayList<>();
        // 1.查询节目分类映射（分类ID -> 分类名称）
        Map<Long, String> programCategoryMap = selectProgramCategoryMap(programPageListDto.getParentProgramCategoryIds());
        // 2.查询节目列表
        List<Program> programList = programMapper.selectHomeList(programPageListDto);
        // 查询结果是空
        if (CollectionUtil.isEmpty(programList)) {
            return programHomeVoList;
        }
        // 3.收集所有节目涉及的areaId，用于查询地区名称
        List<Long> areadIdList = programList.stream()
                .map(Program::getAreaId)
                .distinct()
                .toList();
        // 调用远程基础数据服务，查询areaId对应的名称
        Map<Long, String> areaMap = new HashMap<>(64);
        if (CollectionUtil.isNotEmpty(areadIdList)) {
            AreaSelectDto areaSelectDto = new AreaSelectDto();
            areaSelectDto.setIdList(areadIdList);
            ApiResponse<List<AreaVo>> areaResponse = baseDataClient.selectByIdList(areaSelectDto);
            if (Objects.equals(areaResponse.getCode(), ApiResponse.ok().getCode())
                    && CollectionUtil.isNotEmpty(areaResponse.getData())) {
                // 转换为areaId -> areaName的映射
                areaMap = areaResponse.getData().stream()
                        .collect(Collectors.toMap(AreaVo::getId, AreaVo::getName, (v1, v2) -> v2));
            } else {
                log.error("base-data selectByIdList rpc error areaResponse:{}", JSON.toJSONString(areaSelectDto));
            }
        }
        // 4.查询节目对应的演出时间（按节目ID分组）
        List<Long> programIdList = programList.stream().map(Program::getId).collect(Collectors.toList());
        LambdaQueryWrapper<ProgramShowTime> programShowTimeLambdaQueryWrapper = Wrappers.lambdaQuery(ProgramShowTime.class)
                .in(ProgramShowTime::getProgramId, programIdList);
        List<ProgramShowTime> programShowTimeList = programShowTimeMapper.selectList(programShowTimeLambdaQueryWrapper);
        Map<Long, List<ProgramShowTime>> programShowTimeMap =
                programShowTimeList.stream().collect(Collectors.groupingBy(ProgramShowTime::getProgramId));
        // 5.查询节目对应的票档（按节目ID分组）
        Map<Long, TicketCategoryAggregate> ticketCategorieMap = selectTicketCategorieMap(programIdList);
        // 6.按父分类ID分组，组装主页VO
        Map<Long, List<Program>> programMap = programList.stream()
                .collect(Collectors.groupingBy(Program::getParentProgramCategoryId));
        for (Entry<Long, List<Program>> programEntry : programMap.entrySet()) {
            // 父节目类型id
            Long key = programEntry.getKey();
            // 节目集合
            List<Program> value = programEntry.getValue();
            // 每个父分类ID下的节目列表
            List<ProgramListVo> programListVoList = new ArrayList<>();
            // 组装每个节目的VO
            for (Program program : value) {
                ProgramListVo programListVo = new ProgramListVo();
                BeanUtil.copyProperties(program, programListVo);
                // 设置地区名称（从远程调用查询的映射中获取）
                programListVo.setAreaName(areaMap.get(program.getAreaId()));
                // 设置演出时间（取第一个演出时间）
                programListVo.setShowTime(Optional.ofNullable(programShowTimeMap.get(program.getId()))
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.get(0))
                        .map(ProgramShowTime::getShowTime)
                        .orElse(null));
                // 设置演出时间（精确到天）
                programListVo.setShowDayTime(Optional.ofNullable(programShowTimeMap.get(program.getId()))
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.get(0))
                        .map(ProgramShowTime::getShowDayTime)
                        .orElse(null));
                // 设置演出时间（精确到周）
                programListVo.setShowWeekTime(Optional.ofNullable(programShowTimeMap.get(program.getId()))
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.get(0))
                        .map(ProgramShowTime::getShowWeekTime)
                        .orElse(null));
                // 设置票价范围
                programListVo.setMaxPrice(Optional.ofNullable(ticketCategorieMap.get(program.getId()))
                        .map(TicketCategoryAggregate::getMaxPrice).orElse(null));
                programListVo.setMinPrice(Optional.ofNullable(ticketCategorieMap.get(program.getId()))
                        .map(TicketCategoryAggregate::getMinPrice).orElse(null));
                // 添加到节目列表
                programListVoList.add(programListVo);
            }
            // 组装分类对应的节目列表VO
            ProgramHomeVo programHomeVo = new ProgramHomeVo();
            programHomeVo.setCategoryName(programCategoryMap.get(key));
            programHomeVo.setCategoryId(key);
            programHomeVo.setProgramListVoList(programListVoList);
            programHomeVoList.add(programHomeVo);
        }
        log.info("查询主页节目列表结果：{}", programHomeVoList);
        return programHomeVoList;
    }

    /**
     * 组装节目的时间参数
     * 根据时间类型（今天、明天、本周、本月、自定义）设置对应的开始时间和结束时间
     * 用于后续查询节目数据时的时间范围过滤
     *
     * @param programPageListDto 节目数据的入参
     */
    public void setQueryTime(ProgramPageListDto programPageListDto) {
        // 根据时间类型枚举选择对应的时间范围计算逻辑
        switch (programPageListDto.getTimeType()) {
            // "今天"：开始时间和结束时间都设置为当前日期
            case ProgramTimeType.TODAY:
                programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
                programPageListDto.setEndDateTime(DateUtils.now(FORMAT_DATE));
                break;
            // "明天"：开始时间设置为当前日期，结束时间设置为当前日期加 + 1天
            case ProgramTimeType.TOMORROW:
                programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
                programPageListDto.setEndDateTime(DateUtils.addDay(DateUtils.now(FORMAT_DATE), 1));
                break;
            // "本周"：开始时间设置为当前日期，结束时间设置为当前日期 + 1周
            case ProgramTimeType.WEEK:
                programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
                programPageListDto.setEndDateTime(DateUtils.addWeek(DateUtils.now(FORMAT_DATE), 1));
                break;
            // "本月"：开始时间设置为当前日期，结束时间设置为当前日期 + 1月
            case ProgramTimeType.MONTH:
                programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
                programPageListDto.setEndDateTime(DateUtils.addMonth(DateUtils.now(FORMAT_DATE), 1));
                break;
            // "自定义"：根据用户输入的开始时间和结束时间设置
            case ProgramTimeType.CALENDAR:
                if (Objects.isNull(programPageListDto.getStartDateTime())) {
                    throw new DaMaiFrameException(BaseCode.START_DATE_TIME_NOT_EXIST);
                }
                if (Objects.isNull(programPageListDto.getEndDateTime())) {
                    throw new DaMaiFrameException(BaseCode.END_DATE_TIME_NOT_EXIST);
                }
                break;
            // 其他情况：不设置时间范围
            default:
                programPageListDto.setStartDateTime(null);
                programPageListDto.setEndDateTime(null);
                break;
        }
    }

    /**
     * 查询分类列表（数据库查询）
     *
     * @param programPageListDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public PageVo<ProgramListVo> selectPage(ProgramPageListDto programPageListDto) {
        // 处理时间范围参数
        setQueryTime(programPageListDto);
        // 使用es查询
        PageVo<ProgramListVo> pageVo = programEs.selectPage(programPageListDto);
        // 如果es查询结果不为空，直接返回
        if (CollectionUtil.isNotEmpty(pageVo.getList())) {
            return pageVo;
        }
        // 如果es查询结果为空，则使用数据库查询
        return dbSelectPage(programPageListDto);
    }

    /**
     * 推荐列表
     *
     * @param programRecommendListDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public List<ProgramListVo> recommendList(ProgramRecommendListDto programRecommendListDto) {
        compositeContainer.execute(CompositeCheckType.PROGRAM_RECOMMEND_CHECK.getValue(), programRecommendListDto);
        return programEs.recommendList(programRecommendListDto);
    }

    /**
     * 根据条件分页查询节目列表（数据库查询）
     *
     * @param programPageListDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public PageVo<ProgramListVo> dbSelectPage(ProgramPageListDto programPageListDto) {
        // 1. 调用Mapper层进行数据库分页查询，获取节目与演出时间关联数据
        // PageUtil.getPageParams()用于将入参的分页参数（页码、页大小）转换为MyBatis-Plus的IPage对象
        IPage<ProgramJoinShowTime> iPage =
                programMapper.selectPage(PageUtil.getPageParams(programPageListDto), programPageListDto);
        // 2. 如果查询结果为空，则直接返回空结果
        if (CollectionUtil.isEmpty(iPage.getRecords())) {
            return new PageVo<>(iPage.getCurrent(), iPage.getSize(), iPage.getTotal(), new ArrayList<>());
        }
        // 3.批量查询节目分类信息，用于后续转换分类ID为分类名称
        // 提取所有节目记录中的分类ID，去重后作为查询条件
        Set<Long> programCategoryIdList =
                iPage.getRecords().stream().map(Program::getProgramCategoryId).collect(Collectors.toSet());
        // 查询分类ID -> 分类名称的映射（key:分类ID，value:分类名称）
        Map<Long, String> programCategoryMap = selectProgramCategoryMap(programCategoryIdList);
        // 4.批量查询节目票档信息，用于获取每个节目的最低/最高票价
        // 提取所有节目记录的ID，作为查询条件
        List<Long> programIdList = iPage.getRecords().stream().map(Program::getId).collect(Collectors.toList());
        // 查询节目ID -> 票档信息的映射（key:节目ID，value:票档信息）
        Map<Long, TicketCategoryAggregate> ticketCategorieMap = selectTicketCategorieMap(programIdList);
        // 5.远程调用基础数据服务，批量查询地区名称（通过地区ID转换）
        Map<Long, String> tempAreaMap = new HashMap<>(64);
        AreaSelectDto areaSelectDto = new AreaSelectDto();
        // 提取所有节目记录中的地区ID，去重后作为查询条件
        areaSelectDto.setIdList(iPage.getRecords().stream()
                .map(Program::getAreaId).distinct().collect(Collectors.toList()));
        // 调用Feign客户端查询地区信息
        ApiResponse<List<AreaVo>> areaResponse = baseDataClient.selectByIdList(areaSelectDto);
        // 处理地区查询响应结果
        if (Objects.equals(areaResponse.getCode(), ApiResponse.ok().getCode())) {
            // 若查询成功且有数据，转换为地区ID到名称的映射
            if (CollectionUtil.isNotEmpty(areaResponse.getData())) {
                tempAreaMap = areaResponse.getData().stream()
                        .collect(Collectors.toMap(AreaVo::getId, AreaVo::getName, (v1, v2) -> v2));
            }
        } else {
            // 若远程调用失败，记录错误日志
            log.error("base-data selectByIdList rpc error areaResponse:{}", JSON.toJSONString(areaResponse));
        }
        // 最终的地区映射（可能为空）
        Map<Long, String> areaMap = tempAreaMap;
        // 6.将数据库查询结果转换为前端所需的视图对象（ProgramListVo）
        // 利用PageUtil工具类进行分页数据转换，通过lambda表达式处理每条记录
        return PageUtil.convertPage(iPage, programJoinShowTime -> {
            ProgramListVo programListVo = new ProgramListVo();
            // 复制基本属性（如节目ID、名称、时间等）
            BeanUtil.copyProperties(programJoinShowTime, programListVo);
            // 补充关联信息：地区名称（从地区映射中获取）
            programListVo.setAreaName(areaMap.get(programJoinShowTime.getAreaId()));
            // 补充关联信息：分类名称（从分类映射中获取）
            programListVo.setProgramCategoryName(programCategoryMap.get(programJoinShowTime.getProgramCategoryId()));
            // 补充关联信息：最低/最高票价（从票档映射中获取）
            programListVo.setMinPrice(Optional.ofNullable(ticketCategorieMap.get(programJoinShowTime.getId()))
                    .map(TicketCategoryAggregate::getMinPrice).orElse(null));
            programListVo.setMaxPrice(Optional.ofNullable(ticketCategorieMap.get(programJoinShowTime.getId()))
                    .map(TicketCategoryAggregate::getMaxPrice).orElse(null));
            return programListVo;
        });
    }

    /**
     * 查询节目详情
     *
     * @param programGetDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public ProgramVo detail(ProgramGetDto programGetDto) {
        compositeContainer.execute(CompositeCheckType.PROGRAM_DETAIL_CHECK.getValue(), programGetDto);
        return getDetail(programGetDto);
    }

    /**
     * 查询节目详情V1
     *
     * @param programGetDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public ProgramVo detailV1(ProgramGetDto programGetDto) {
        compositeContainer.execute(CompositeCheckType.PROGRAM_DETAIL_CHECK.getValue(), programGetDto);
        return getDetail(programGetDto);
    }

    /**
     * 查询节目详情V2
     *
     * @param programGetDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public ProgramVo detailV2(ProgramGetDto programGetDto) {
        compositeContainer.execute(CompositeCheckType.PROGRAM_DETAIL_CHECK.getValue(), programGetDto);
        return getDetailV2(programGetDto);
    }

    /**
     * 查询节目详情执行
     * 通过节目ID查询并组装完整的节目详情信息，包括基础信息、演出时间、票档等
     *
     * @param programGetDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public ProgramVo getDetail(ProgramGetDto programGetDto) {
        // 1.查询节目对应的演出时间信息
        ProgramShowTime programShowTime = programShowTimeService.selectProgramShowTimeByProgramId(programGetDto.getId());
        // 2.查询节目基础信息，并计算当前时间到演出开始的倒计时（秒数）
        // 倒计时用于后续判断票档状态（如是否可售、即将停售等）
        ProgramVo programVo = programService.getById(
                programGetDto.getId(),
                // 计算当前时间与演出时间的秒数差（倒计时）
                DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()),
                TimeUnit.SECONDS
        );
        // 3.设置节目演出时间相关信息到返回对象
        programVo.setShowTime(programShowTime.getShowTime());   // 完整演出时间
        programVo.setShowDayTime(programShowTime.getShowDayTime());   // 演出日期（如：2023-10-01）
        programVo.setShowWeekTime(programShowTime.getShowWeekTime());  // 演出星期（如：周六）
        // 4.查询并设置节目所属的节目组信息
        ProgramGroupVo programGroupVo = programService.getProgramGroup(programVo.getProgramGroupId());
        programVo.setProgramGroupVo(programGroupVo);
        // 5.预加载用户购票人
        preloadTicketUserList(programVo.getHighHeat());
        // 6.预先加载用户下节目订单数量
        preloadAccountOrderCount(programVo.getId());
        // 7.查询并设置节目所属分类名称
        ProgramCategory programCategory = getProgramCategory(programVo.getProgramCategoryId());
        if (Objects.nonNull(programCategory)) {
            programVo.setProgramCategoryName(programCategory.getName());
        }
        // 8.查询并设置节目所属父分类名称
        ProgramCategory parentProgramCategory = getProgramCategory(programVo.getParentProgramCategoryId());
        if (Objects.nonNull(parentProgramCategory)) {
            programVo.setParentProgramCategoryName(parentProgramCategory.getName());
        }
        // 9.查询并设置节目对应的票档信息列表
        List<TicketCategoryVo> ticketCategoryVoList = ticketCategoryService.selectTicketCategoryListByProgramId(
                programVo.getId(),
                DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()),
                TimeUnit.SECONDS
        );
        programVo.setTicketCategoryVoList(ticketCategoryVoList);
        // 返回组装完成的节目详情对象
        return programVo;
    }

    /**
     * 查询节目详情V2执行
     * 采用多级缓存策略提升性能
     *
     * @param programGetDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public ProgramVo getDetailV2(ProgramGetDto programGetDto) {
        // 查询节目演出时间
        ProgramShowTime programShowTime =
                programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programGetDto.getId());
        // 从节目表获取数据以及区域信息（参数传演出时间是为了设置本地缓存的过期时间）
        ProgramVo programVo = programService.getByIdMultipleCache(programGetDto.getId(), programShowTime.getShowTime());
        // 设置演出时间
        programVo.setShowTime(programShowTime.getShowTime());
        programVo.setShowDayTime(programShowTime.getShowDayTime());
        programVo.setShowWeekTime(programShowTime.getShowWeekTime());
        // 从节目分组表获取数据
        ProgramGroupVo programGroupVo = programService.getProgramGroupMultipleCache(programVo.getProgramGroupId());
        programVo.setProgramGroupVo(programGroupVo);
        // 预加载用户购票人
        preloadTicketUserList(programVo.getHighHeat());
        // 预先加载用户下节目订单数量
        preloadAccountOrderCount(programVo.getId());
        // 设置节目类型相关信息
        ProgramCategory programCategory = getProgramCategoryMultipleCache(programVo.getProgramCategoryId());
        if (Objects.nonNull(programCategory)) {
            programVo.setProgramCategoryName(programCategory.getName());
        }
        ProgramCategory parentProgramCategory = getProgramCategoryMultipleCache(programVo.getParentProgramCategoryId());
        if (Objects.nonNull(parentProgramCategory)) {
            programVo.setParentProgramCategoryName(parentProgramCategory.getName());
        }
        // 查询节目票档信息
        List<TicketCategoryVo> ticketCategoryVoList = ticketCategoryService
                .selectTicketCategoryListByProgramIdMultipleCache(programVo.getId(), programShowTime.getShowTime());
        programVo.setTicketCategoryVoList(ticketCategoryVoList);
        // 返回节目详情VO
        return programVo;
    }

    /**
     * 查询节目表详情执行（多级）
     * 优先从本地缓存获取，未命中则通过加载函数查询分布式缓存或数据库，并自动更新本地缓存
     *
     * @param programId 节目id
     * @param showTime  节目演出时间
     * @return 执行后的结果
     */
    public ProgramVo getByIdMultipleCache(Long programId, Date showTime) {
        return localCacheProgram.getCache(
                RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId).getRelKey(),
                key -> {
                    log.info("查询节目详情从本地缓存没有查询到 节目id : {}", programId);
                    ProgramVo programVo = getById(programId, DateUtils.countBetweenSecond(DateUtils.now(), showTime),
                            TimeUnit.SECONDS);
                    programVo.setShowTime(showTime);
                    return programVo;
                }
        );
    }

    public ProgramVo simpleGetByIdMultipleCache(Long programId) {
        ProgramVo programVoCache = localCacheProgram.getCache(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM,
                programId).getRelKey());
        if (Objects.nonNull(programVoCache)) {
            return programVoCache;
        }
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId), ProgramVo.class);
    }

    /**
     * 从多级缓存获取节目信息和演出时间
     *
     * @param programId
     * @return
     */
    public ProgramVo simpleGetProgramAndShowMultipleCache(Long programId) {
        ProgramShowTime programShowTime =
                programShowTimeService.simpleSelectProgramShowTimeByProgramIdMultipleCache(programId);
        if (Objects.isNull(programShowTime)) {
            throw new DaMaiFrameException(BaseCode.PROGRAM_SHOW_TIME_NOT_EXIST);
        }

        ProgramVo programVo = simpleGetByIdMultipleCache(programId);
        if (Objects.isNull(programVo)) {
            throw new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST);
        }

        programVo.setShowTime(programShowTime.getShowTime());
        programVo.setShowDayTime(programShowTime.getShowDayTime());
        programVo.setShowWeekTime(programShowTime.getShowWeekTime());

        return programVo;
    }

    /**
     * 根据节目ID查询节目基础信息
     *
     * @param programId  节目ID
     * @param expireTime 缓存过期时间（数值）
     * @param timeUnit   缓存过期时间的单位（如秒、分钟等）
     * @return 封装了节目详情信息的ProgramVo对象
     */
    @ServiceLock(lockType = LockType.Read, name = PROGRAM_LOCK, keys = {"#programId"})
    public ProgramVo getById(Long programId, Long expireTime, TimeUnit timeUnit) {
        // 1.尝试从Redis缓存中查询节目信息
        ProgramVo programVo =
                redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId), ProgramVo.class);
        // 若缓存命中，直接返回结果
        if (Objects.nonNull(programVo)) {
            return programVo;
        }
        log.info("查询节目详情 从Redis缓存没有查询到 节目id : {}", programId);
        // 3.缓存未命，查询数据库
        // 获取分布式可重入锁，防止缓存击穿
        RLock lock = serviceLockTool.getLock(
                LockType.Reentrant,
                GET_PROGRAM_LOCK,
                new String[]{String.valueOf(programId)}
        );
        // 加锁
        lock.lock();
        try {
            // 再次从Redis缓存中查询节目信息，双重检查
            return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId)
                    , ProgramVo.class,
                    () -> createProgramVo(programId)   // 数据库查询逻辑（函数式接口）
                    , expireTime,    // 缓存过期时间数值
                    timeUnit);    // 缓存过期时间单位
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 基于多级缓存查询节目组详情
     * 优先从本地缓存获取数据，未命中则通过加载函数查询分布式缓存或数据库，并自动更新本地缓存
     *
     * @param programGroupId 节目组ID
     * @return 封装了节目组详情的ProgramGroupVo对象
     */
    public ProgramGroupVo getProgramGroupMultipleCache(Long programGroupId) {
        return localCacheProgramGroup.getCache(
                RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId).getRelKey(),
                key -> getProgramGroup(programGroupId)
        );
    }

    /**
     * 根据节目组ID查询节目组信息
     *
     * @param programGroupId 　节目组ID
     * @return　 封装了节目组详情的ProgramGroupVo对象
     */
    @ServiceLock(lockType = LockType.Read, name = PROGRAM_GROUP_LOCK, keys = {"#programGroupId"})
    public ProgramGroupVo getProgramGroup(Long programGroupId) {
        // 1.尝试从Redis缓存中查询节目组信息
        ProgramGroupVo programGroupVo =
                redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId), ProgramGroupVo.class);
        // 若缓存命中，直接返回结果
        if (Objects.nonNull(programGroupVo)) {
            return programGroupVo;
        }
        // 3.缓存未命中，查询数据库
        // 获取分布式可重入锁，防止缓存击穿
        RLock lock = serviceLockTool.getLock(
                LockType.Reentrant,
                GET_PROGRAM_LOCK,
                new String[]{String.valueOf(programGroupId)}
        );
        // 加锁
        lock.lock();
        try {
            // 再次从Redis缓存中查询节目组信息，双重检查
            programGroupVo = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId),
                    ProgramGroupVo.class);
            // 若二次检查缓存仍未命中，则查询数据库并更新缓存
            if (Objects.isNull(programGroupVo)) {
                // 从数据库查询并构建节目组详情对象
                programGroupVo = createProgramGroupVo(programGroupId);
                // 将数据库查询结果写入Redis缓存，并设置过期时间
                redisCache.set(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId),
                        programGroupVo,
                        DateUtils.countBetweenSecond(DateUtils.now(), programGroupVo.getRecentShowTime()),
                        TimeUnit.SECONDS
                );
            }
            return programGroupVo;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 查询节目分类映射（分类ID -> 分类名称）
     *
     * @param programCategoryIdList
     * @return
     */
    public Map<Long, String> selectProgramCategoryMap(Collection<Long> programCategoryIdList) {
        LambdaQueryWrapper<ProgramCategory> pcLambdaQueryWrapper = Wrappers.lambdaQuery(ProgramCategory.class)
                .in(ProgramCategory::getId, programCategoryIdList);
        List<ProgramCategory> programCategoryList = programCategoryMapper.selectList(pcLambdaQueryWrapper);
        return programCategoryList
                .stream()
                .collect(Collectors.toMap(ProgramCategory::getId, ProgramCategory::getName, (v1, v2) -> v2));
    }

    /**
     * 查询节目票档信息（节目ID -> 最高/最低票价）
     *
     * @param programIdList
     * @return
     */
    public Map<Long, TicketCategoryAggregate> selectTicketCategorieMap(List<Long> programIdList) {
        List<TicketCategoryAggregate> ticketCategorieList = ticketCategoryMapper.selectAggregateList(programIdList);
        return ticketCategorieList.stream().collect(
                Collectors.toMap(TicketCategoryAggregate::getProgramId,
                        ticketCategory -> ticketCategory,
                        (v1, v2) -> v2)
        );
    }

    /**
     * 操作节目相关数据（核心为更新座位销售状态及票类剩余数量）
     * 用于购票、锁定座位等场景，确保座位状态与票类库存的一致性
     *
     * @param programOperateDataDto 节目操作数据DTO
     */
    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER, keys = {"#programOperateDataDto.programId", "#programOperateDataDto.seatIdList"})
    @Transactional(rollbackFor = Exception.class)
    public void operateProgramData(ProgramOperateDataDto programOperateDataDto) {
        // 从DTO中提取票档数量信息列表（（用于更新票档剩余库存））
        List<TicketCategoryCountDto> ticketCategoryCountDtoList = programOperateDataDto.getTicketCategoryCountDtoList();
        // 从DTO中提取需要操作的座位ID列表
        List<Long> seatIdList = programOperateDataDto.getSeatIdList();
        // 根据节目ID和座位ID列表查询对应的座位信息
        LambdaQueryWrapper<Seat> seatLambdaQueryWrapper =
                Wrappers.lambdaQuery(Seat.class)
                        .eq(Seat::getProgramId, programOperateDataDto.getProgramId())
                        .in(Seat::getId, seatIdList);
        List<Seat> seatList = seatMapper.selectList(seatLambdaQueryWrapper);
        // 校验座位存在性：若查询结果为空，说明传入的座位ID不存在
        if (CollectionUtil.isEmpty(seatList)) {
            throw new DaMaiFrameException(BaseCode.SEAT_NOT_EXIST);
        }
        // 校验座位数量一致性：查询到的座位数量与传入的ID数量不一致（可能部分座位ID无效）
        if (seatList.size() != seatIdList.size()) {
            throw new DaMaiFrameException(BaseCode.SEAT_UPDATE_REL_COUNT_NOT_EQUAL_PRESET_COUNT);
        }
        // 校验座位状态：确保所有座位均未售出（防止重复售票）
        for (Seat seat : seatList) {
            if (Objects.equals(seat.getSellStatus(), SellStatus.SOLD.getCode())) {
                throw new DaMaiFrameException(BaseCode.SEAT_SOLD);
            }
        }
        // 更新数据库座位销售状态为“已售出”
        LambdaUpdateWrapper<Seat> seatLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(Seat.class)
                        .eq(Seat::getProgramId, programOperateDataDto.getProgramId())
                        .in(Seat::getId, seatIdList);
        Seat updateSeat = new Seat();
        updateSeat.setSellStatus(SellStatus.SOLD.getCode());
        seatMapper.update(updateSeat, seatLambdaUpdateWrapper);
        // 批量更新票档的余票库存
        int updateRemainNumberCount = ticketCategoryMapper
                .batchUpdateRemainNumber(ticketCategoryCountDtoList, programOperateDataDto.getProgramId());
        // 校验票档更新数量是否与传入的票档数量一致（防止部分票档更新失败）
        if (updateRemainNumberCount != ticketCategoryCountDtoList.size()) {
            throw new DaMaiFrameException(BaseCode.UPDATE_TICKET_CATEGORY_COUNT_NOT_CORRECT);
        }
    }

    /**
     * 创建节目基础信息VO对象（ProgramVo）
     * 从数据库查询节目核心数据，并通过RPC调用补充地区名称，为后续详情组装提供基础
     *
     * @param programId 节目ID，用于查询对应的节目信息
     * @return 包含节目基础信息和地区名称的ProgramVo对象
     */
    private ProgramVo createProgramVo(Long programId) {
        // 初始化节目VO对象，用于封装查询结果
        ProgramVo programVo = new ProgramVo();
        // 从数据库中查询节目基础信息
        Program program = Optional.ofNullable(programMapper.selectById(programId))
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST));
        // 将节目实体的属性拷贝到VO中
        BeanUtil.copyProperties(program, programVo);
        // 补充地区名称（通过Feign远程调用基础数据服务）
        // 构建地区查询DTO，设置需要查询的地区ID（取自节目实体的areaId）
        AreaGetDto areaGetDto = new AreaGetDto();
        areaGetDto.setId(program.getAreaId());
        // 调用基础数据服务的getById方法，获取地区详情信息
        ApiResponse<AreaVo> areaResponse = baseDataClient.getById(areaGetDto);
        // 检查是否调用成功
        if (Objects.equals(areaResponse.getCode(), ApiResponse.ok().getCode())) {
            // 若调用成功且返回数据不为空，设置地区名称到VO中
            if (Objects.nonNull(areaResponse.getData())) {
                programVo.setAreaName(areaResponse.getData().getName());
            }
        } else {
            // 远程调用失败
            log.error("base-data rpc getById error areaResponse:{}", JSON.toJSONString(areaResponse));
        }
        // 返回包含基础信息和地区名称的VO对象
        return programVo;
    }

    /**
     * 创建节目分组信息VO对象（ProgramGroupVo）
     *
     * @param programGroupId
     * @return
     */
    private ProgramGroupVo createProgramGroupVo(Long programGroupId) {
        // 初始化节目组详情VO对象（用于封装返回给前端的数据）
        ProgramGroupVo programGroupVo = new ProgramGroupVo();
        // 从数据库查询节目组基础信息
        ProgramGroup programGroup =
                Optional.ofNullable(programGroupMapper.selectById(programGroupId))
                        .orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_GROUP_NOT_EXIST));
        // 设置节目组ID
        programGroupVo.setId(programGroup.getId());
        // 将数据库中存储的节目JSON字符串解析为ProgramSimpleInfoVo列表
        programGroupVo.setProgramSimpleInfoVoList(JSON.parseArray(programGroup.getProgramJson(), ProgramSimpleInfoVo.class));
        // 设置节目组最近的演出时间
        programGroupVo.setRecentShowTime(programGroup.getRecentShowTime());
        return programGroupVo;
    }

    /**
     * 获取所有有效节目ID列表
     * 用于获取系统中状态为“有效”的所有节目ID，支持布隆过滤器初始化、全量数据同步等场景
     *
     * @return　有效节目ID的列表（Long类型），若没有有效节目则返回空列表
     */
    public List<Long> getAllProgramIdList() {
        // 构建查询条件：筛选状态为“1”的节目，仅查询id字段
        LambdaQueryWrapper<Program> programLambdaQueryWrapper =
                Wrappers.lambdaQuery(Program.class).eq(Program::getProgramStatus, BusinessStatus.YES.getCode())
                        .select(Program::getId);
        // 执行查询，获取所有有效节目的ID列表
        List<Program> programs = programMapper.selectList(programLambdaQueryWrapper);
        // 将节目实体列表转换为ID列表（提取每个实体的ID字段）
        return programs.stream()
                .map(Program::getId)  // 提取ID字段
                .collect(Collectors.toList());  // 收集为List<Long>
    }

    /**
     * 从数据库查询节目完整详情信息并封装为ProgramVo对象
     * 用于获取节目基础信息、分类信息及演出时间信息，支持后续数据同步（如ES索引构建）
     *
     * @param programId 节目ID，用于唯一标识查询的节目
     * @return 封装了节目完整详情的ProgramVo对象
     */
    public ProgramVo getDetailFromDb(Long programId) {
        // 创建ProgramVo对象
        ProgramVo programVo = createProgramVo(programId);
        // 查询节目所属分类信息并补充到VO中
        // 根据节目分类ID查询分类对象
        ProgramCategory programCategory = getProgramCategory(programVo.getProgramCategoryId());
        if (Objects.nonNull(programCategory)) {
            // 若分类存在，设置分类名称（用于前端展示或搜索筛选）
            programVo.setProgramCategoryName(programCategory.getName());
        }
        // 查询节目所属父分类信息并补充到VO中
        // 根据父分类ID查询父分类对象（支持多级分类展示）
        ProgramCategory parentProgramCategory = getProgramCategory(programVo.getParentProgramCategoryId());
        if (Objects.nonNull(parentProgramCategory)) {
            // 若父分类存在，设置父分类名称
            programVo.setParentProgramCategoryName(parentProgramCategory.getName());
        }
        // 构建查询条件：根据节目ID查询对应的演出时间记录
        LambdaQueryWrapper<ProgramShowTime> programShowTimeLambdaQueryWrapper =
                Wrappers.lambdaQuery(ProgramShowTime.class).eq(ProgramShowTime::getProgramId, programId);
        // 执行查询，若未查询到演出时间记录则抛出异常（演出时间为节目核心信息，必须存在）
        ProgramShowTime programShowTime = Optional.ofNullable(
                programShowTimeMapper.selectOne(programShowTimeLambdaQueryWrapper)).orElseThrow(
                () -> new DaMaiFrameException(BaseCode.PROGRAM_SHOW_TIME_NOT_EXIST));
        // 将演出时间信息补充到VO中
        programVo.setShowTime(programShowTime.getShowTime());
        programVo.setShowDayTime(programShowTime.getShowDayTime());
        programVo.setShowWeekTime(programShowTime.getShowWeekTime());
        // 返回完整的节目详情Vo对象
        return programVo;
    }

    /**
     * 预热加载当前登录用户的购票人列表到缓存
     * 仅在节目为高热度且用户已登录时执行，通过异步线程处理避免阻塞主流程
     *
     * @param highHeat
     */
    private void preloadTicketUserList(Integer highHeat) {
        // 1.判断节目是否为高热度：若非高热度，直接返回，不执行预热
        if (Objects.equals(highHeat, BusinessStatus.NO.getCode())) {
            return;
        }
        // 2.从上下文获取用户ID和身份标识code
        String userId = BaseParameterHolder.getParameter(USER_ID);
        String code = BaseParameterHolder.getParameter(CODE);
        // 3.若用户ID或code为空（可能为匿名访问），直接返回，无需加载
        if (StringUtil.isEmpty(userId) || StringUtil.isEmpty(code)) {
            return;
        }
        // 4.验证用户是否已登录：检查Redis中是否存在登录状态的缓存
        Boolean userLogin =
                redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, code, userId));
        if (!userLogin) {
            return;
        }
        // 5.通过线程池异步执行购票人列表的预热加载
        BusinessThreadPool.execute(() -> {
            try {
                // 6.检查Redis中是否已缓存购票人列表，若已缓存则无需重复加载
                if (!redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.TICKET_USER_LIST, userId))) {
                    // 7.构建查询参数，调用用户服务接口获取购票人列表
                    TicketUserListDto ticketUserListDto = new TicketUserListDto();
                    ticketUserListDto.setUserId(Long.parseLong(userId));
                    ApiResponse<List<TicketUserVo>> apiResponse = userClient.list(ticketUserListDto);
                    // 8.若接口调用成功且返回数据不为空，将购票人列表存入Redis缓存
                    if (Objects.equals(apiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                        Optional.ofNullable(apiResponse.getData())
                                .filter(CollectionUtil::isNotEmpty)   // 过滤空列表
                                .ifPresent(ticketUserVoList -> redisCache.set(RedisKeyBuild.createRedisKey(
                                        RedisKeyManage.TICKET_USER_LIST, userId), ticketUserVoList));
                    } else {
                        log.warn("userClient.select 调用失败 apiResponse : {}", JSON.toJSONString(apiResponse));
                    }
                }
            } catch (Exception e) {
                log.error("预热加载购票人列表失败", e);
            }
        });
    }

    /**
     * 预热加载当前登录用户对指定节目的订单数量到缓存
     * 仅在用户已登录时执行，通过异步线程处理避免阻塞主流程
     *
     * @param programId 节目id
     */
    private void preloadAccountOrderCount(Long programId) {
        // 1.从上下文获取用户ID和身份标识code
        String userId = BaseParameterHolder.getParameter(USER_ID);
        String code = BaseParameterHolder.getParameter(CODE);
        // 2.若用户ID或code为空（可能为匿名访问），直接返回，无需加载
        if (StringUtil.isEmpty(userId) || StringUtil.isEmpty(code)) {
            return;
        }
        // 3.验证用户是否已登录：检查Redis中是否存在登录状态的缓存
        Boolean userLogin =
                redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, code, userId));
        if (!userLogin) {
            return;
        }
        // 4.通过线程池异步执行订单数量的预热加载
        BusinessThreadPool.execute(() -> {
            try {
                // 5.检查缓存中是否已存在该用户对该节目的订单数量：若已存在，无需重复加载
                if (!redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT, userId, programId))) {
                    // 6.构建查询参数，调用订单服务接口获取用户对该节目的订单数量
                    AccountOrderCountDto accountOrderCountDto = new AccountOrderCountDto();
                    accountOrderCountDto.setUserId(Long.parseLong(userId));
                    accountOrderCountDto.setProgramId(programId);
                    ApiResponse<AccountOrderCountVo> apiResponse = orderClient.accountOrderCount(accountOrderCountDto);
                    // 7.若接口调用成功且返回数据不为空，将订单数量存入Redis缓存
                    if (Objects.equals(apiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                        Optional.ofNullable(apiResponse.getData())
                                .ifPresent(accountOrderCountVo -> redisCache.set(
                                        RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT, userId, programId),
                                        accountOrderCountVo.getCount(),  // 订单数量
                                        // 缓存过期时间：token有效期+1分钟，确保与用户登录状态同步
                                        tokenExpireManager.getTokenExpireTime() + 1,
                                        TimeUnit.MINUTES)
                                );
                    } else {
                        log.warn("orderClient.accountOrderCount 调用失败 apiResponse : {}", JSON.toJSONString(apiResponse));
                    }
                }
            } catch (Exception e) {
                log.error("预热加载账户订单数量失败", e);
            }
        });
    }

    /**
     * 基于多级缓存查询节目组详情
     * 优先从本地缓存获取数据，未命中则通过加载函数查询分布式缓存或数据库，并自动更新本地缓存
     *
     * @param programCategoryId
     * @return
     */
    public ProgramCategory getProgramCategoryMultipleCache(Long programCategoryId) {
        return localCacheProgramCategory.get(String.valueOf(programCategoryId),
                key -> getProgramCategory(programCategoryId)
        );
    }

    /**
     * 获取节目分类信息
     *
     * @param programCategoryId
     * @return
     */
    public ProgramCategory getProgramCategory(Long programCategoryId) {
        return programCategoryService.getProgramCategory(programCategoryId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean resetExecute(ProgramResetExecuteDto programResetExecuteDto) {
        Long programId = programResetExecuteDto.getProgramId();
        LambdaQueryWrapper<Seat> seatQueryWrapper =
                Wrappers.lambdaQuery(Seat.class).eq(Seat::getProgramId, programId)
                        .in(Seat::getSellStatus, SellStatus.LOCK.getCode(), SellStatus.SOLD.getCode());
        List<Seat> seatList = seatMapper.selectList(seatQueryWrapper);
        if (CollectionUtil.isNotEmpty(seatList)) {
            LambdaUpdateWrapper<Seat> seatUpdateWrapper =
                    Wrappers.lambdaUpdate(Seat.class).eq(Seat::getProgramId, programId);
            Seat seatUpdate = new Seat();
            seatUpdate.setSellStatus(SellStatus.NO_SOLD.getCode());
            seatMapper.update(seatUpdate, seatUpdateWrapper);
        }
        LambdaQueryWrapper<TicketCategory> ticketCategoryQueryWrapper =
                Wrappers.lambdaQuery(TicketCategory.class).eq(TicketCategory::getProgramId, programId);
        List<TicketCategory> ticketCategories = ticketCategoryMapper.selectList(ticketCategoryQueryWrapper);
        if (CollectionUtil.isNotEmpty(ticketCategories)) {
            for (TicketCategory ticketCategory : ticketCategories) {
                Long remainNumber = ticketCategory.getRemainNumber();
                Long totalNumber = ticketCategory.getTotalNumber();
                if (!(remainNumber.equals(totalNumber))) {
                    TicketCategory ticketCategoryUpdate = new TicketCategory();
                    ticketCategoryUpdate.setRemainNumber(totalNumber);

                    LambdaUpdateWrapper<TicketCategory> ticketCategoryUpdateWrapper =
                            Wrappers.lambdaUpdate(TicketCategory.class)
                                    .eq(TicketCategory::getProgramId, programId)
                                    .eq(TicketCategory::getId, ticketCategory.getId());
                    ticketCategoryMapper.update(ticketCategoryUpdate, ticketCategoryUpdateWrapper);
                }
            }
        }
        delRedisData(programId);
        delLocalCache(programId);
        return true;
    }

    /**
     * 删除节目相关的redis缓存
     * 用于在节目信息发生变更（如演出时间更新、票档调整等）后，清理旧缓存，避免数据不一致
     *
     * @param programId 节目ID，用于定位需要删除的缓存键
     */
    public void delRedisData(Long programId) {
        // 先查询节目信息，若不存在则抛出异常
        Program program = Optional.ofNullable(programMapper.selectById(programId))
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST));
        // 收集所有需要删除的Redis缓存键
        List<String> keys = new ArrayList<>();
        // 节目基本信息缓存键（如节目详情）
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId).getRelKey());
        // 节目所属组信息缓存键（节目组级别的聚合数据）
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, program.getProgramGroupId()).getRelKey());
        // 节目演出时间缓存键（演出场次、时间等信息）
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME, programId).getRelKey());
        // 节目未售出座位的分辨率缓存键（带通配符*，匹配该节目下所有相关分辨率的未售座位缓存）
        keys.add(RedisKeyBuild.createRedisKey(
                RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, "*").getRelKey());
        // 节目锁定座位的分辨率缓存键（带通配符，清理所有锁定状态的座位缓存）
        keys.add(RedisKeyBuild.createRedisKey(
                RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, "*").getRelKey());
        // 节目已售出座位的分辨率缓存键（带通配符，清理所有已售座位缓存）
        keys.add(RedisKeyBuild.createRedisKey(
                RedisKeyManage.PROGRAM_SEAT_SOLD_RESOLUTION_HASH, programId, "*").getRelKey());
        // 节目票档列表缓存键（如该节目下的所有票价类别）
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_CATEGORY_LIST, programId).getRelKey());
        // 节目剩余票数的哈希缓存键（带通配符，清理所有票档的剩余票数缓存）
        keys.add(RedisKeyBuild.createRedisKey(
                RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, "*").getRelKey());
        // 调用缓存删除工具类，批量删除收集的缓存键
        // 第二个参数为额外的删除条件（此处为空，表示直接删除所有收集的键）
        programDelCacheData.del(keys, new String[]{});
    }

    /**
     * 根据节目ID失效节目（将节目状态设置为无效）
     *
     * @param programInvalidDto
     * @return
     */
    public Boolean invalid(final ProgramInvalidDto programInvalidDto) {
        Program program = new Program();
        program.setId(programInvalidDto.getId());  // 设置目标节目ID
        program.setProgramStatus(BusinessStatus.NO.getCode());  // 将节目状态设为"无效"（如0表示无效）
        // 执行数据库更新操作（根据ID更新节目状态）
        int result = programMapper.updateById(program);
        // 若数据库更新成功（影响行数>0），执行后续清理和通知操作
        if (result > 0) {
            // 清理Redis中该节目的缓存数据（避免缓存和数据库不一致）
            delRedisData(programInvalidDto.getId());
            // 向Redis Stream发送消息（通知其他服务节目已失效，如更新搜索索引、缓存等）
            redisStreamPushHandler.push(String.valueOf(programInvalidDto.getId()));
            // 删除Elasticsearch中该节目的索引数据
            programEs.deleteByProgramId(programInvalidDto.getId());
            return true;
        } else {
            return false;
        }
    }

    public ProgramVo localDetail(final ProgramGetDto programGetDto) {
        return localCacheProgram.getCache(String.valueOf(programGetDto.getId()));
    }

    /**
     * 删除节目相关的本地缓存数据
     * 用于在节目信息变更后，同步清理应用本地内存中的缓存，确保本地缓存与数据库/Redis数据一致
     * 避免因本地缓存未及时更新导致的业务逻辑错误（如展示旧的演出时间、票价等）
     *
     * @param programId
     */
    public void delLocalCache(Long programId) {
        log.info("删除本地缓存 programId : {}", programId);
        // 删除本地缓存中的节目基本信息
        localCacheProgram.del(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId).getRelKey());
        // 删除本地缓存中的节目组信息（通过节目ID关联的节目组缓存）
        localCacheProgramGroup.del(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programId).getRelKey());
        // 删除本地缓存中的节目演出时间
        localCacheProgramShowTime.del(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME, programId).getRelKey());
        // 删除本地缓存中的节目票档列表
        localCacheTicketCategory.del(programId);
    }
}

