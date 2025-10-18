package com.damai.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RedisKeyManage;
import com.damai.dto.TicketCategoryAddDto;
import com.damai.dto.TicketCategoryDto;
import com.damai.dto.TicketCategoryListByProgramDto;
import com.damai.entity.TicketCategory;
import com.damai.mapper.TicketCategoryMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.cache.local.LocalCacheTicketCategory;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import com.damai.vo.TicketCategoryDetailVo;
import com.damai.vo.TicketCategoryVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.core.DistributedLockConstants.GET_REMAIN_NUMBER_LOCK;
import static com.damai.core.DistributedLockConstants.GET_TICKET_CATEGORY_LOCK;
import static com.damai.core.DistributedLockConstants.REMAIN_NUMBER_LOCK;
import static com.damai.core.DistributedLockConstants.TICKET_CATEGORY_LOCK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 票档 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class TicketCategoryService extends ServiceImpl<TicketCategoryMapper, TicketCategory> {

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private TicketCategoryMapper ticketCategoryMapper;

    @Autowired
    private ServiceLockTool serviceLockTool;

    @Autowired
    private LocalCacheTicketCategory localCacheTicketCategory;

    @Transactional(rollbackFor = Exception.class)
    public Long add(TicketCategoryAddDto ticketCategoryAddDto) {
        TicketCategory ticketCategory = new TicketCategory();
        BeanUtil.copyProperties(ticketCategoryAddDto, ticketCategory);
        ticketCategory.setId(uidGenerator.getUid());
        ticketCategoryMapper.insert(ticketCategory);
        return ticketCategory.getId();
    }

    /**
     * 基于多级缓存查询节目票档信息
     *
     * @param programId
     * @param showTime
     * @return
     */
    public List<TicketCategoryVo> selectTicketCategoryListByProgramIdMultipleCache(Long programId, Date showTime) {
        return localCacheTicketCategory.getCache(
                programId,
                key -> selectTicketCategoryListByProgramId(programId,
                        DateUtils.countBetweenSecond(DateUtils.now(), showTime), TimeUnit.SECONDS)
        );
    }

    /**
     * 根据节目ID查询票档列表信息
     *
     * @param programId  　节目ID
     * @param expireTime 　缓存过期时间（数值）
     * @param timeUnit   　缓存过期时间单位（如秒、分钟等）
     * @return　票档信息列表（TicketCategoryVo）
     */
    @ServiceLock(lockType = LockType.Read, name = TICKET_CATEGORY_LOCK, keys = {"#programId"})
    public List<TicketCategoryVo> selectTicketCategoryListByProgramId(Long programId, Long expireTime, TimeUnit timeUnit) {
        // 1.从Redis缓存中查询票档列表
        List<TicketCategoryVo> ticketCategoryVoList = redisCache.getValueIsList(
                RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_CATEGORY_LIST, programId),
                TicketCategoryVo.class
        );
        // 2.若缓存中存在非空票档列表，直接返回缓存结果，减少数据库访问
        if (CollectionUtil.isNotEmpty(ticketCategoryVoList)) {
            return ticketCategoryVoList;
        }
        // 3.缓存未命中，获取分布式锁去查询数据库
        RLock lock = serviceLockTool.getLock(
                LockType.Reentrant, GET_TICKET_CATEGORY_LOCK,
                new String[]{String.valueOf(programId)}
        );
        // 加锁
        lock.lock();
        try {
            // 二次检查Redis缓存
            return redisCache.getValueIsList(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_CATEGORY_LIST, programId),
                    TicketCategoryVo.class,
                    () -> {
                        // 4.根据节目ID构建查询条件
                        LambdaQueryWrapper<TicketCategory> ticketCategoryLambdaQueryWrapper =
                                Wrappers.lambdaQuery(TicketCategory.class).eq(TicketCategory::getProgramId, programId);
                        // 从数据库中查询票档列表
                        List<TicketCategory> ticketCategoryList =
                                ticketCategoryMapper.selectList(ticketCategoryLambdaQueryWrapper);
                        return ticketCategoryList.stream().map(ticketCategory -> {
                            // 清除冗余字段（剩余票数可能实时变动，不在缓存中存储）
                            ticketCategory.setRemainNumber(null);
                            TicketCategoryVo ticketCategoryVo = new TicketCategoryVo();
                            // 复制属性：将实体类字段映射到VO类
                            BeanUtil.copyProperties(ticketCategory, ticketCategoryVo);
                            return ticketCategoryVo;
                        }).collect(Collectors.toList());
                    }, expireTime, timeUnit);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 获取票档剩余数量
     *
     * @param programId        节目ID
     * @param ticketCategoryId 票档ID
     * @return 票档剩余数量映射表（key：票档ID字符串，value：剩余数量）
     */
    @ServiceLock(lockType = LockType.Read, name = REMAIN_NUMBER_LOCK, keys = {"#programId", "#ticketCategoryId"})
    public Map<String, Long> getRedisRemainNumberResolution(Long programId, Long ticketCategoryId) {
        // 1.从Redis缓存中查询票档余票信息
        Map<String, Long> ticketCategoryRemainNumber = redisCache.getAllMapForHash(
                RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION,
                        programId, ticketCategoryId), Long.class);
        // 2.若缓存中存在非空票档余票信息，直接返回缓存结果，减少数据库访问
        if (CollectionUtil.isNotEmpty(ticketCategoryRemainNumber)) {
            return ticketCategoryRemainNumber;
        }
        // 3.缓存未命中，获取分布式可重入锁去查询数据库，防止高并发下的"缓存击穿"（大量请求同时穿透到数据库）
        RLock lock = serviceLockTool.getLock(
                LockType.Reentrant,
                GET_REMAIN_NUMBER_LOCK,
                new String[]{String.valueOf(programId), String.valueOf(ticketCategoryId)}
        );
        // 加锁
        lock.lock();
        try {
            // 4.二次检查Redis缓存，防止其他线程已更新缓存，避免重复查询数据库
            ticketCategoryRemainNumber = redisCache.getAllMapForHash(RedisKeyBuild.createRedisKey(
                            RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId),
                    Long.class);
            // 如果缓存命中，直接返回
            if (CollectionUtil.isNotEmpty(ticketCategoryRemainNumber)) {
                return ticketCategoryRemainNumber;
            }
            // 5.缓存仍未命中，从数据库查询票档信息
            // 根据节目ID和票档ID构建查询条件
            LambdaQueryWrapper<TicketCategory> ticketCategoryLambdaQueryWrapper =
                    Wrappers.lambdaQuery(TicketCategory.class)
                            .eq(TicketCategory::getProgramId, programId)
                            .eq(TicketCategory::getId, ticketCategoryId);
            // 从数据库中查询票档列表
            List<TicketCategory> ticketCategoryList = ticketCategoryMapper.selectList(ticketCategoryLambdaQueryWrapper);
            // 6.将查询结果转换为Map（key：票档ID字符串，value：剩余数量）
            Map<String, Long> map = ticketCategoryList.stream()
                    .collect(Collectors.toMap(
                            t -> String.valueOf(t.getId()),  // 票档ID作为键
                            TicketCategory::getRemainNumber,  // 余票数量作为值
                            (v1, v2) -> v2)   // 若有重复ID，保留后者（理论上不会出现）
                    );
            // 7.将查询结果写入Redis缓存
            redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION,
                    programId, ticketCategoryId), map);
            return map;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    public TicketCategoryDetailVo detail(TicketCategoryDto ticketCategoryDto) {
        TicketCategory ticketCategory = ticketCategoryMapper.selectById(ticketCategoryDto.getId());
        TicketCategoryDetailVo ticketCategoryDetailVo = new TicketCategoryDetailVo();
        BeanUtil.copyProperties(ticketCategory, ticketCategoryDetailVo);
        return ticketCategoryDetailVo;
    }

    public List<TicketCategoryDetailVo> selectListByProgram(TicketCategoryListByProgramDto ticketCategoryListByProgramDto) {
        List<TicketCategory> ticketCategorieList = ticketCategoryMapper.selectList(Wrappers.lambdaQuery(TicketCategory.class)
                .eq(TicketCategory::getProgramId, ticketCategoryListByProgramDto.getProgramId()));
        return ticketCategorieList.stream().map(ticketCategory -> {
            TicketCategoryDetailVo ticketCategoryDetailVo = new TicketCategoryDetailVo();
            BeanUtil.copyProperties(ticketCategory, ticketCategoryDetailVo);
            return ticketCategoryDetailVo;
        }).collect(Collectors.toList());
    }
}
