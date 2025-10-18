package com.damai.service.strategy;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.dto.SeatDto;
import com.damai.locallock.LocalLockCache;
import com.damai.lock.LockTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 基础节目订单处理类，封装了节目订单创建过程中与本地锁相关的通用逻辑
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class BaseProgramOrder {

    @Autowired
    private LocalLockCache localLockCache;

    /**
     * 基于本地锁的订单创建方法，为订单创建过程提供本地锁保护，确保单实例内的并发安全
     *
     * @param lockKeyPrefix         锁键前缀，用于生成唯一锁标识
     * @param programOrderCreateDto 订单创建请求数据
     * @param lockTask              需要在锁保护下执行的任务（如实际创建订单的业务逻辑）
     * @return 订单号
     */
    public String localLockCreateOrder(String lockKeyPrefix, ProgramOrderCreateDto programOrderCreateDto,
                                       LockTask<String> lockTask) {
        // 获取订单中的座位信息列表
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        // 存储票档ID列表
        List<Long> ticketCategoryIdList = new ArrayList<>();
        // 判断是否存在选座信息
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            // 如果是自主选座，从座位列表中提取不重复的票档ID
            ticketCategoryIdList = seatDtoList.stream()
                    .map(SeatDto::getTicketCategoryId).distinct().collect(Collectors.toList());
        } else {
            // 自动分配座位，直接使用订单中的票档ID
            ticketCategoryIdList.add(programOrderCreateDto.getTicketCategoryId());
        }
        // 存储需要尝试获取的本地锁列表
        List<ReentrantLock> localLockList = new ArrayList<>(ticketCategoryIdList.size());
        // 存储成功获取的本地锁列表
        List<ReentrantLock> localLockSuccessList = new ArrayList<>(ticketCategoryIdList.size());
        // 遍历票档ID列表，为每个票档ID生成一个本地锁，并尝试获取锁
        for (Long ticketCategoryId : ticketCategoryIdList) {
            // 生成锁唯一标识：前缀 + 节目ID + 票档ID，确保锁的细粒度
            String lockKey = StrUtil.join("-", lockKeyPrefix,
                    programOrderCreateDto.getProgramId(), ticketCategoryId);
            // 从本地锁缓存中获取非公平锁
            ReentrantLock localLock = localLockCache.getLock(lockKey, false);
            // 将获取到的锁添加到尝试获取的锁列表中
            localLockList.add(localLock);
        }
        // 尝试获取所有本地锁
        for (ReentrantLock reentrantLock : localLockList) {
            try {
                // 加锁（阻塞式）
                reentrantLock.lock();
            } catch (Throwable t) {
                // 若获取某把锁失败，立即停止后续锁的获
                break;
            }
            // 将成功获取的锁添加到成功获取的锁列表中
            localLockSuccessList.add(reentrantLock);
        }
        try {
            // 执行受锁保护的任务（实际的订单逻辑）
            return lockTask.execute();
        } finally {
            // 释放所有成功获取的本地锁（逆序释放，与获取顺序相反，减少锁竞争）
            for (int i = localLockSuccessList.size() - 1; i >= 0; i--) {
                ReentrantLock reentrantLock = localLockSuccessList.get(i);
                try {
                    reentrantLock.unlock();
                } catch (Throwable t) {
                    log.error("local lock unlock error", t);
                }
            }
        }
    }
}
