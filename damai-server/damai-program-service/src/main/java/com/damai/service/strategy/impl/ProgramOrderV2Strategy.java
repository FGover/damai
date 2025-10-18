package com.damai.service.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.damai.core.RepeatExecuteLimitConstants;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.dto.SeatDto;
import com.damai.enums.CompositeCheckType;
import com.damai.enums.ProgramOrderVersion;
import com.damai.initialize.base.AbstractApplicationCommandLineRunnerHandler;
import com.damai.initialize.impl.composite.CompositeContainer;
import com.damai.locallock.LocalLockCache;
import com.damai.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.damai.service.ProgramOrderService;
import com.damai.service.strategy.ProgramOrderContext;
import com.damai.service.strategy.ProgramOrderStrategy;
import com.damai.servicelock.LockType;
import com.damai.util.ServiceLockTool;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.damai.core.DistributedLockConstants.PROGRAM_ORDER_CREATE_V2;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目订单v2
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramOrderV2Strategy extends AbstractApplicationCommandLineRunnerHandler implements ProgramOrderStrategy {

    @Autowired
    private ProgramOrderService programOrderService;

    @Autowired
    private ServiceLockTool serviceLockTool;

    @Autowired
    private CompositeContainer compositeContainer;

    @Autowired
    private LocalLockCache localLockCache;

    /**
     * 创建节目订单的核心方法（V2版本实现）
     *
     * @param programOrderCreateDto 订单参数
     * @return 订单编号
     */
    @RepeatExecuteLimit(
            name = RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER,
            keys = {"#programOrderCreateDto.userId", "#programOrderCreateDto.programId"})
    @Override
    public String createOrder(ProgramOrderCreateDto programOrderCreateDto) {
        // 业务参数验证
        compositeContainer.execute(CompositeCheckType.PROGRAM_ORDER_CREATE_CHECK.getValue(), programOrderCreateDto);
        // 获取座位信息列表
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        // 获取票档ID的列表
        List<Long> ticketCategoryIdList = new ArrayList<>();
        // 如果座位信息列表不为空，则从座位信息列表中获取票档ID列表
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            ticketCategoryIdList = seatDtoList.stream()
                    .map(SeatDto::getTicketCategoryId).distinct().collect(Collectors.toList());
        } else {
            // 如果没有座位信息，直接使用订单参数中的票档ID
            ticketCategoryIdList.add(programOrderCreateDto.getTicketCategoryId());
        }
        // 初始化锁相关的列表
        // 本地锁列表：存储所有需要尝试获取的本地锁
        List<ReentrantLock> localLockList = new ArrayList<>(ticketCategoryIdList.size());
        // 分布式锁列表：存储所有需要尝试获取的分布式锁
        List<RLock> serviceLockList = new ArrayList<>(ticketCategoryIdList.size());
        // 成功获取的本地锁列表
        List<ReentrantLock> localLockSuccessList = new ArrayList<>(ticketCategoryIdList.size());
        // 成功获取的分布式锁列表
        List<RLock> serviceLockSuccessList = new ArrayList<>(ticketCategoryIdList.size());
        // 遍历票档ID列表，为每个票档ID生成对应的本地锁和分布式锁
        for (Long ticketCategoryId : ticketCategoryIdList) {
            // 生成锁的唯一标识：拼接前缀、节目ID和票档ID
            String lockKey = StrUtil.join("-", PROGRAM_ORDER_CREATE_V2,
                    programOrderCreateDto.getProgramId(), ticketCategoryId);
            // 获取本地锁（非公平锁）
            ReentrantLock localLock = localLockCache.getLock(lockKey, false);
            // 获取分布式锁（可重入锁）
            RLock serviceLock = serviceLockTool.getLock(LockType.Reentrant, lockKey);
            // 将本地锁和分布式锁添加到对应的列表中
            localLockList.add(localLock);
            serviceLockList.add(serviceLock);
        }
        // 尝试获取所有本地锁
        for (ReentrantLock reentrantLock : localLockList) {
            try {
                // 加锁（阻塞式，直到获取锁为止）
                reentrantLock.lock();
            } catch (Throwable t) {
                // 如果获取锁失败，中断循环，不再尝试获取后续锁
                break;
            }
            // 记录成功获取的本地锁
            localLockSuccessList.add(reentrantLock);
        }
        // 尝试获取所有分布式锁
        for (RLock rLock : serviceLockList) {
            try {
                // 加锁（阻塞式，直到获取锁为止）
                rLock.lock();
            } catch (Throwable t) {
                // 如果获取锁失败，中断循环，不再尝试获取后续锁
                break;
            }
            // 记录成功获取的分布式锁
            serviceLockSuccessList.add(rLock);
        }
        try {
            // 调用订单创建服务，执行业务逻辑
            return programOrderService.create(programOrderCreateDto);
        } finally {
            // 释放所有成功获取的分布式锁（逆序）
            for (int i = serviceLockSuccessList.size() - 1; i >= 0; i--) {
                RLock rLock = serviceLockSuccessList.get(i);
                try {
                    rLock.unlock();
                } catch (Throwable t) {
                    log.error("service lock unlock error", t);
                }
            }
            // 释放所有成功获取的本地锁（逆序）
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

    @Override
    public Integer executeOrder() {
        return 2;
    }

    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        ProgramOrderContext.add(ProgramOrderVersion.V2_VERSION.getVersion(), this);
    }
}
