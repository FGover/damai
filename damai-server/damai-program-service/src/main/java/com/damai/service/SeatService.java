package com.damai.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RedisKeyManage;
import com.damai.dto.ProgramGetDto;
import com.damai.dto.SeatAddDto;
import com.damai.dto.SeatBatchAddDto;
import com.damai.dto.SeatBatchRelateInfoAddDto;
import com.damai.dto.SeatListDto;
import com.damai.entity.ProgramShowTime;
import com.damai.entity.Seat;
import com.damai.enums.BaseCode;
import com.damai.enums.BusinessStatus;
import com.damai.enums.SeatType;
import com.damai.enums.SellStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.SeatMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.lua.ProgramSeatCacheData;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import com.damai.vo.ProgramVo;
import com.damai.vo.SeatRelateInfoVo;
import com.damai.vo.SeatVo;
import com.damai.vo.TicketCategoryVo;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.core.DistributedLockConstants.GET_SEAT_LOCK;
import static com.damai.core.DistributedLockConstants.SEAT_LOCK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 座位 service
 * @author: 阿星不是程序员
 **/
@Service
public class SeatService extends ServiceImpl<SeatMapper, Seat> {

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private SeatMapper seatMapper;

    @Autowired
    private ProgramService programService;

    @Autowired
    private ProgramShowTimeService programShowTimeService;

    @Autowired
    private ServiceLockTool serviceLockTool;

    @Autowired
    private TicketCategoryService ticketCategoryService;

    @Autowired
    private ProgramSeatCacheData programSeatCacheData;

    /**
     * 添加座位
     */
    public Long add(SeatAddDto seatAddDto) {
        LambdaQueryWrapper<Seat> seatLambdaQueryWrapper = Wrappers.lambdaQuery(Seat.class)
                .eq(Seat::getProgramId, seatAddDto.getProgramId())
                .eq(Seat::getRowCode, seatAddDto.getRowCode())
                .eq(Seat::getColCode, seatAddDto.getColCode());
        Seat seat = seatMapper.selectOne(seatLambdaQueryWrapper);
        if (Objects.nonNull(seat)) {
            throw new DaMaiFrameException(BaseCode.SEAT_IS_EXIST);
        }
        seat = new Seat();
        BeanUtil.copyProperties(seatAddDto, seat);
        seat.setId(uidGenerator.getUid());
        seatMapper.insert(seat);
        return seat.getId();
    }

    /**
     * 查询节目票档对应的座位解析信息
     *
     * @param programId        节目ID
     * @param ticketCategoryId 票档ID
     * @param expireTime       缓存过期时间
     * @param timeUnit         时间单位
     * @return 座位信息列表
     */
    @ServiceLock(lockType = LockType.Read, name = SEAT_LOCK, keys = {"#programId", "#ticketCategoryId"})
    public List<SeatVo> selectSeatResolution(Long programId, Long ticketCategoryId, Long expireTime, TimeUnit timeUnit) {
        // 从Redis缓存中获取座位信息
        List<SeatVo> seatVoList = getSeatVoListByCacheResolution(programId, ticketCategoryId);
        // 如果缓存中存在，直接返回
        if (CollectionUtil.isNotEmpty(seatVoList)) {
            return seatVoList;
        }
        // 未命中则获取分布式锁去查询数据库
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, GET_SEAT_LOCK, new String[]{String.valueOf(programId),
                String.valueOf(ticketCategoryId)});
        // 加锁
        lock.lock();
        try {
            // 再次从缓存中获取座位信息
            seatVoList = getSeatVoListByCacheResolution(programId, ticketCategoryId);
            // 如果缓存中存在，直接返回
            if (CollectionUtil.isNotEmpty(seatVoList)) {
                return seatVoList;
            }
            // 从数据库中获取座位信息
            LambdaQueryWrapper<Seat> seatLambdaQueryWrapper =
                    Wrappers.lambdaQuery(Seat.class).eq(Seat::getProgramId, programId)
                            .eq(Seat::getTicketCategoryId, ticketCategoryId);
            List<Seat> seats = seatMapper.selectList(seatLambdaQueryWrapper);
            // 将实体类转换为VO类
            for (Seat seat : seats) {
                SeatVo seatVo = new SeatVo();
                BeanUtil.copyProperties(seat, seatVo);
                seatVo.setSeatTypeName(SeatType.getMsg(seat.getSeatType())); // 补充座位类型名
                seatVoList.add(seatVo);
            }
            // 将座位信息按照销售状态分组
            Map<Integer, List<SeatVo>> seatMap = seatVoList.stream()
                    .collect(Collectors.groupingBy(SeatVo::getSellStatus));
            // 未售座位
            List<SeatVo> noSoldSeatVoList = seatMap.get(SellStatus.NO_SOLD.getCode());
            // 锁定座位
            List<SeatVo> lockSeatVoList = seatMap.get(SellStatus.LOCK.getCode());
            // 已售座位
            List<SeatVo> soldSeatVoList = seatMap.get(SellStatus.SOLD.getCode());
            // 将未售座位信息存入Redis缓存
            // 哈希值：座位ID -> 座位信息（防止重复存储）
            if (CollectionUtil.isNotEmpty(noSoldSeatVoList)) {
                redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH,
                                programId, ticketCategoryId), noSoldSeatVoList.stream()
                                .collect(Collectors.toMap(s -> String.valueOf(s.getId()), s -> s, (v1, v2) -> v2))
                        , expireTime, timeUnit);
            }
            // 锁定座位
            if (CollectionUtil.isNotEmpty(lockSeatVoList)) {
                redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH,
                                programId, ticketCategoryId), lockSeatVoList.stream()
                                .collect(Collectors.toMap(s -> String.valueOf(s.getId()), s -> s, (v1, v2) -> v2))
                        , expireTime, timeUnit);
            }
            // 已售座位
            if (CollectionUtil.isNotEmpty(soldSeatVoList)) {
                redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_SOLD_RESOLUTION_HASH,
                                programId, ticketCategoryId)
                        , soldSeatVoList.stream()
                                .collect(Collectors.toMap(s -> String.valueOf(s.getId()), s -> s, (v1, v2) -> v2))
                        , expireTime, timeUnit);
            }
            // 排序座位列表（按行号升序，行号相同则按列号升序）
            seatVoList = seatVoList.stream().
                    sorted(Comparator.comparingInt(SeatVo::getRowCode)
                            .thenComparingInt(SeatVo::getColCode))
                    .collect(Collectors.toList());
            return seatVoList;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 从Redis缓存中获取节目票档对应的所有座位信息
     *
     * @param programId        节目ID
     * @param ticketCategoryId 票档ID
     * @return 座位信息列表
     */
    public List<SeatVo> getSeatVoListByCacheResolution(Long programId, Long ticketCategoryId) {
        // 构建三类座位状态的缓存键（未售、锁定、已售）
        List<String> keys = new ArrayList<>(4);
        // 添加"未售座位"的Redis哈希键
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH,
                programId, ticketCategoryId).getRelKey());
        // 添加"锁定座位"的Redis哈希键
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH,
                programId, ticketCategoryId).getRelKey());
        // 添加"已售座位"的Redis哈希键
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_SOLD_RESOLUTION_HASH,
                programId, ticketCategoryId).getRelKey());
        // 从Redis缓存中获取座位信息
        return programSeatCacheData.getData(keys, new String[]{});
    }

    /**
     * 获取节目座位相关信息
     *
     * @param seatListDto 包含节目ID的查询参数
     * @return SeatRelateInfoVo 座位相关信息封装对象，包含节目基本信息、座位列表、价格列表等
     */
    public SeatRelateInfoVo relateInfo(SeatListDto seatListDto) {
        // 初始化座位相关信息返回对象
        SeatRelateInfoVo seatRelateInfoVo = new SeatRelateInfoVo();
        // 从Redis缓存中查询节目基本信息
        ProgramVo programVo = redisCache.get(RedisKeyBuild.createRedisKey(
                RedisKeyManage.PROGRAM, seatListDto.getProgramId()), ProgramVo.class);
        // 如果缓存中没有找到节目信息，则从数据库中查询
        if (Objects.isNull(programVo)) {
            ProgramGetDto programGetDto = new ProgramGetDto();
            programGetDto.setId(seatListDto.getProgramId());  // 设置查询的节目ID
            programVo = programService.detail(programGetDto);  // 调用服务查询节目详情
        }
        // 检验节目是否允许选座
        if (programVo.getPermitChooseSeat().equals(BusinessStatus.NO.getCode())) {
            throw new DaMaiFrameException(BaseCode.PROGRAM_NOT_ALLOW_CHOOSE_SEAT);
        }
        // 查询节目演出时间信息
        ProgramShowTime programShowTime = programShowTimeService.selectProgramShowTimeByProgramId(seatListDto.getProgramId());
        // 查询该节目所有票档信息
        List<TicketCategoryVo> ticketCategoryVoList = ticketCategoryService
                .selectTicketCategoryListByProgramIdMultipleCache(programVo.getId(), programShowTime.getShowTime());
        // 汇总所有票档对应的可售座位信息
        List<SeatVo> seatVos = new ArrayList<>();
        // 遍历每个票档，查询对应座位
        for (TicketCategoryVo ticketCategoryVo : ticketCategoryVoList) {
            // 查询该票档下的座位列表，并添加到总列表中
            List<SeatVo> ticketSeats = selectSeatResolution(
                    seatListDto.getProgramId(),  // 节目ID
                    ticketCategoryVo.getId(),    // 票档ID
                    // 计算当前时间到演出时间的秒数差（用于判断座位状态，如临近演出可能关闭售票）
                    DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()),  // 时间差
                    TimeUnit.SECONDS             // 时间单位
            );
            seatVos.addAll(ticketSeats);
        }
        // 按价格分组，以价格为键，座位列表为值
        Map<String, List<SeatVo>> seatVoMap =
                seatVos.stream().collect(Collectors.groupingBy(seatVo -> seatVo.getPrice().toString()));
        seatRelateInfoVo.setProgramId(programVo.getId());  // 设置节目ID
        seatRelateInfoVo.setPlace(programVo.getPlace());   // 设置演出地点
        seatRelateInfoVo.setShowTime(programShowTime.getShowTime());  // 设置演出时间
        seatRelateInfoVo.setShowWeekTime(programShowTime.getShowWeekTime());  // 设置演出时间所在的星期
        // 提取所有价格并排序
        seatRelateInfoVo.setPriceList(seatVoMap.keySet().stream().sorted().collect(Collectors.toList()));
        seatRelateInfoVo.setSeatVoMap(seatVoMap); // 设置座位信息
        return seatRelateInfoVo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean batchAdd(SeatBatchAddDto seatBatchAddDto) {
        Long programId = seatBatchAddDto.getProgramId();
        List<SeatBatchRelateInfoAddDto> seatBatchRelateInfoAddDtoList = seatBatchAddDto.getSeatBatchRelateInfoAddDtoList();


        int rowIndex = 0;
        for (SeatBatchRelateInfoAddDto seatBatchRelateInfoAddDto : seatBatchRelateInfoAddDtoList) {
            Long ticketCategoryId = seatBatchRelateInfoAddDto.getTicketCategoryId();
            BigDecimal price = seatBatchRelateInfoAddDto.getPrice();
            Integer count = seatBatchRelateInfoAddDto.getCount();

            int colCount = 10;
            int rowCount = count / colCount;

            for (int i = 1; i <= rowCount; i++) {
                rowIndex++;
                for (int j = 1; j <= colCount; j++) {
                    Seat seat = new Seat();
                    seat.setProgramId(programId);
                    seat.setTicketCategoryId(ticketCategoryId);
                    seat.setRowCode(rowIndex);
                    seat.setColCode(j);
                    seat.setSeatType(1);
                    seat.setPrice(price);
                    seat.setSellStatus(SellStatus.NO_SOLD.getCode());
                    seatMapper.insert(seat);
                }
            }
        }

        return true;
    }
}
