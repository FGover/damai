package com.damai.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RedisKeyManage;
import com.damai.dto.ProgramShowTimeAddDto;
import com.damai.entity.Program;
import com.damai.entity.ProgramGroup;
import com.damai.entity.ProgramShowTime;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.ProgramGroupMapper;
import com.damai.mapper.ProgramMapper;
import com.damai.mapper.ProgramShowTimeMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.cache.local.LocalCacheProgramShowTime;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.damai.core.DistributedLockConstants.GET_PROGRAM_SHOW_TIME_LOCK;
import static com.damai.core.DistributedLockConstants.PROGRAM_SHOW_TIME_LOCK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目演出时间 service
 * @author: 阿星不是程序员
 **/
@Service
public class ProgramShowTimeService extends ServiceImpl<ProgramShowTimeMapper, ProgramShowTime> {

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ProgramMapper programMapper;

    @Autowired
    private ProgramShowTimeMapper programShowTimeMapper;

    @Autowired
    private ProgramGroupMapper programGroupMapper;

    @Autowired
    private ServiceLockTool serviceLockTool;

    @Autowired
    private LocalCacheProgramShowTime localCacheProgramShowTime;


    @Transactional(rollbackFor = Exception.class)
    public Long add(ProgramShowTimeAddDto programShowTimeAddDto) {
        ProgramShowTime programShowTime = new ProgramShowTime();
        BeanUtil.copyProperties(programShowTimeAddDto, programShowTime);
        programShowTime.setId(uidGenerator.getUid());
        programShowTimeMapper.insert(programShowTime);
        return programShowTime.getId();
    }

    /**
     * 基于多级缓存查询节目演出时间信息
     * 优先从本地缓存获取，本地缓存未命中时查询分布式缓存，最终兜底查询数据库
     * 减少数据库访问压力，提升高频查询场景的响应速度
     *
     * @param programId 节目ID
     * @return 节目演出时间信息（包含具体时间、日期、星期等）
     */
    public ProgramShowTime selectProgramShowTimeByProgramIdMultipleCache(Long programId) {
        // 查询本地缓存数据
        return localCacheProgramShowTime.getCache(
                RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME, programId).getRelKey(),
                key -> selectProgramShowTimeByProgramId(programId)  // 缓存未命中时的加载逻辑
        );
    }

    /**
     * 本地缓存查询节目演出时间，如果没有就查redis
     *
     * @param programId
     * @return
     */
    public ProgramShowTime simpleSelectProgramShowTimeByProgramIdMultipleCache(Long programId) {
        ProgramShowTime programShowTimeCache = localCacheProgramShowTime.getCache(RedisKeyBuild.createRedisKey(
                RedisKeyManage.PROGRAM_SHOW_TIME, programId).getRelKey());
        if (Objects.nonNull(programShowTimeCache)) {
            return programShowTimeCache;
        }
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME,
                programId), ProgramShowTime.class);
    }

    /**
     * 根据节目ID查询演出时间信息
     * 采用缓存+数据库的双层查询机制，并通过分布式锁防止缓存击穿
     *
     * @param programId 节目ID
     * @return 节目对应的演出时间信息对象ProgramShowTime
     */
    @ServiceLock(lockType = LockType.Read, name = PROGRAM_SHOW_TIME_LOCK, keys = {"#programId"})
    public ProgramShowTime selectProgramShowTimeByProgramId(Long programId) {
        // 1.先从Redis缓存中查询演出时间信息
        // 构建缓存key
        ProgramShowTime programShowTime = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME,
                programId), ProgramShowTime.class);
        // 2.如果缓存中存在，直接返回
        if (Objects.nonNull(programShowTime)) {
            return programShowTime;
        }
        // 3.缓存未命中，获取分布式可重入锁，防止高并发下缓存击穿问题
        // 锁的键值为节目ID，确保同一节目同时只有一个请求能执行数据库查询
        RLock lock = serviceLockTool.getLock(
                LockType.Reentrant,
                GET_PROGRAM_SHOW_TIME_LOCK,
                new String[]{String.valueOf(programId)}
        );
        // 加锁
        lock.lock();
        try {
            // 4.加锁后再次查询Redis缓存（双重检查），防止锁等待期间已有其他线程更新了缓存
            programShowTime = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME,
                    programId), ProgramShowTime.class);
            // 5.若缓存仍未命中，则查询数据库
            if (Objects.isNull(programShowTime)) {
                // 构建查询条件：根据节目ID查询唯一的演出时间记录
                LambdaQueryWrapper<ProgramShowTime> programShowTimeLambdaQueryWrapper =
                        Wrappers.lambdaQuery(ProgramShowTime.class).eq(ProgramShowTime::getProgramId, programId);
                // 执行数据库查询，若查询结果为空则抛出异常
                programShowTime = Optional.ofNullable(programShowTimeMapper.selectOne(programShowTimeLambdaQueryWrapper))
                        .orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_SHOW_TIME_NOT_EXIST));
                // 6.将数据库查询结果写入Redis缓存
                // 过期时间设置为：从当前时间到演出时间的秒数（演出结束后自动失效，无需手动清理）
                redisCache.set(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME, programId),
                        programShowTime,
                        DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()),
                        TimeUnit.SECONDS
                );
            }
            return programShowTime;
        } finally {
            // 7.释放锁
            lock.unlock();
        }
    }

    /**
     * 更新节目演出事件，将即将到期的演出时间自动续期，并同步更新节目组的最近演出时间
     * 采用事务管理，确保所有更新操作的原子性，发生异常时回滚所有变更
     *
     * @return 被更新的节目ID集合，用于后续同步缓存和索引
     */
    @Transactional(rollbackFor = Exception.class)
    public Set<Long> renewal() {
        // 存储被更新的节目ID，用于后续通知缓存和索引更新
        Set<Long> programIdSet = new HashSet<>();
        // 构建查询条件：查询演出时间小于等于明天的节目演出记录（即找出即将在24小时内到期的演出安排）
        // TODO
        LambdaQueryWrapper<ProgramShowTime> programShowTimeLambdaQueryWrapper =
                Wrappers.lambdaQuery(ProgramShowTime.class).
                        le(ProgramShowTime::getShowTime, DateUtils.addDay(DateUtils.now(), 2));
        // 执行查询，获取需要续期的演出时间记录
        List<ProgramShowTime> programShowTimes = programShowTimeMapper.selectList(programShowTimeLambdaQueryWrapper);
        // 存储更新后的演出时间记录，用于后续计算节目组的最近演出时间
        List<ProgramShowTime> newProgramShowTimes = new ArrayList<>(programShowTimes.size());
        // 遍历每条需要续期的记录，执行演出时间更新操作
        for (ProgramShowTime programShowTime : programShowTimes) {
            // 记录当前被更新的节目ID
            programIdSet.add(programShowTime.getProgramId());
            // 获取原演出时间
            Date oldShowTime = programShowTime.getShowTime();
            // 初始计算：在原时间基础上加一个月作为新时间
            Date newShowTime = DateUtils.addMonth(oldShowTime, 1);
            // 获取当前系统时间，用于校验新时间有效性
            Date nowDateTime = DateUtils.now();
            // 循环调整：若新时间仍在当前时间之前，继续累加一个月，直到新时间在当前时间之后
            while (newShowTime.before(nowDateTime)) {
                newShowTime = DateUtils.addMonth(newShowTime, 1);
            }
            // 构建新的演出日期（仅保留日期部分，时间设为00:00:00）
            Date newShowDayTime = DateUtils.parseDateTime(DateUtils.formatDate(newShowTime) + " 00:00:00");
            // 准备更新的演出时间数据
            ProgramShowTime updateProgramShowTime = new ProgramShowTime();
            updateProgramShowTime.setShowTime(newShowTime);   // 更新具体演出时间
            updateProgramShowTime.setShowDayTime(newShowDayTime);  // 更新演出日期（仅日期部分）
            updateProgramShowTime.setShowWeekTime(DateUtils.getWeekStr(newShowTime));  // 更新星期信息
            // 构造更新条件：通过节目ID和记录ID精准定位（防止同节目多条记录更新冲突）
            LambdaUpdateWrapper<ProgramShowTime> programShowTimeLambdaUpdateWrapper =
                    Wrappers.lambdaUpdate(ProgramShowTime.class)
                            .eq(ProgramShowTime::getProgramId, programShowTime.getProgramId())
                            .eq(ProgramShowTime::getId, programShowTime.getId());
            // 执行数据库更新操作
            programShowTimeMapper.update(updateProgramShowTime, programShowTimeLambdaUpdateWrapper);
            // TODO
            // 保存更新后的节目ID和新演出时间，用于后续更新节目组信息
            ProgramShowTime newProgramShowTime = new ProgramShowTime();
            newProgramShowTime.setProgramId(programShowTime.getProgramId());
            newProgramShowTime.setShowTime(newShowTime);
            newProgramShowTimes.add(newProgramShowTime);
        }
        // 存储节目组ID与改组最近演出时间的映射（key：节目组ID，value：该组最早的演出时间）
        Map<Long, Date> programGroupMap = new HashMap<>(newProgramShowTimes.size());
        // 遍历更新后的演出时间记录，计算节目组的最近演出时间（取该组内最早的演出时间）
        for (ProgramShowTime newProgramShowTime : newProgramShowTimes) {
            // 根据节目ID查询对应的节目信息
            Program program = programMapper.selectById(newProgramShowTime.getProgramId());
            // 若节目不存在，跳过处理
            if (Objects.isNull(program)) {
                continue;
            }
            // 获取节目所属的节目组ID
            Long programGroupId = program.getProgramGroupId();
            // 从映射中获取该节目组当前记录的最近演出时间
            Date showTime = programGroupMap.get(programGroupId);
            // 若该节目组尚未记录时间，直接存入当前演出时间
            if (Objects.isNull(showTime)) {
                programGroupMap.put(programGroupId, newProgramShowTime.getShowTime());
            } else {
                // 若有记录，比较时间：取更早的演出时间作为该组的最近演出时间
                if (DateUtil.compare(newProgramShowTime.getShowTime(), showTime) < 0) {
                    programGroupMap.put(programGroupId, newProgramShowTime.getShowTime());
                }
            }
        }
        // 若存在需要更新的节目组信息，进行批量更新
        if (CollectionUtil.isNotEmpty(programGroupMap)) {
            programGroupMap.forEach((programGroupId, recentShowTime) -> {
                // 准备节目组更新数据：设置最新的最近演出时间
                ProgramGroup programGroup = new ProgramGroup();
                programGroup.setRecentShowTime(recentShowTime);
                // 构建节目组更新条件：通过节目组ID精准定位
                LambdaUpdateWrapper<ProgramGroup> programGroupLambdaUpdateWrapper =
                        Wrappers.lambdaUpdate(ProgramGroup.class)
                                .eq(ProgramGroup::getId, programGroupId);
                // 执行节目组信息更新数据库
                programGroupMapper.update(programGroup, programGroupLambdaUpdateWrapper);
            });
        }
        // 返回所有被更新的节目ID集合，用于触发后续缓存和索引同步
        return programIdSet;
    }
}
