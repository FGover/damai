package com.damai.service.composite.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.damai.client.OrderClient;
import com.damai.client.UserClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.dto.AccountOrderCountDto;
import com.damai.dto.ProgramGetDto;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.dto.TicketUserListDto;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.ProgramService;
import com.damai.service.composite.AbstractProgramCheckHandler;
import com.damai.service.tool.TokenExpireManager;
import com.damai.vo.AccountOrderCountVo;
import com.damai.vo.ProgramVo;
import com.damai.vo.TicketUserVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 用户检查处理器
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramUserExistCheckHandler extends AbstractProgramCheckHandler {

    @Autowired
    private UserClient userClient;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private OrderClient orderClient;

    @Autowired
    private ProgramService programService;

    @Autowired
    private TokenExpireManager tokenExpireManager;

    /**
     * 执行用户及购票人相关校验逻辑
     * 包括：购票人信息合法性校验、用户针对节目累计购票数量限制校验
     *
     * @param programOrderCreateDto 节目订单创建请求数据传输对象
     */
    @Override
    protected void execute(ProgramOrderCreateDto programOrderCreateDto) {
        // 1. 验证用户和购票人信息正确性
        // 1.1 先从Redis缓存中查询当前用户的购票人列表
        List<TicketUserVo> ticketUserVoList = redisCache.getValueIsList(RedisKeyBuild.createRedisKey(
                RedisKeyManage.TICKET_USER_LIST, programOrderCreateDto.getUserId()), TicketUserVo.class);
        // 1.2 若缓存中无数据，则调用用户服务远程查询
        if (CollectionUtil.isEmpty(ticketUserVoList)) {
            TicketUserListDto ticketUserListDto = new TicketUserListDto();
            ticketUserListDto.setUserId(programOrderCreateDto.getUserId());
            ApiResponse<List<TicketUserVo>> apiResponse = userClient.list(ticketUserListDto);
            // 处理远程调用结果：成功则获取数据，失败则记录日志并抛出异常
            if (Objects.equals(apiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                ticketUserVoList = apiResponse.getData();
            } else {
                log.error("user client rpc getUserAndTicketUserList select response : {}", JSON.toJSONString(apiResponse));
                throw new DaMaiFrameException(apiResponse);
            }
        }
        // 1.3 若查询结果为空（无可用购票人），抛出异常
        if (CollectionUtil.isEmpty(ticketUserVoList)) {
            throw new DaMaiFrameException(BaseCode.TICKET_USER_EMPTY);
        }
        // 1.4 将购票人列表转换为Map（ID为键），便于快速校验
        Map<Long, TicketUserVo> ticketUserVoMap = ticketUserVoList.stream()
                .collect(Collectors.toMap(TicketUserVo::getId, ticketUserVo -> ticketUserVo, (v1, v2) -> v2));
        // 1.5 校验订单中传入的购票人ID是否均存在于用户的购票人列表中
        for (Long ticketUserId : programOrderCreateDto.getTicketUserIdList()) {
            if (Objects.isNull(ticketUserVoMap.get(ticketUserId))) {
                throw new DaMaiFrameException(BaseCode.TICKET_USER_EMPTY);
            }
        }
        // 2. 查询节目信息（用于获取单用户购票数量限制）
        ProgramGetDto programGetDto = new ProgramGetDto();
        programGetDto.setId(programOrderCreateDto.getProgramId());
        ProgramVo programVo = programService.detailV2(programGetDto);
        if (Objects.isNull(programVo)) {
            throw new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST);
        }
        // 3. 校验用户针对当前节目累计购票数量是否超限
        // 3.1 从Redis查询用户已购当前节目的订单数量（优先缓存）
        Integer count = 0;
        if (redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT,
                programOrderCreateDto.getUserId(), programOrderCreateDto.getProgramId()))) {
            count = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT,
                    programOrderCreateDto.getUserId(), programOrderCreateDto.getProgramId()), Integer.class);
        } else {
            // 3.2 缓存未命中时，调用订单服务查询并更新缓存
            AccountOrderCountDto accountOrderCountDto = new AccountOrderCountDto();
            accountOrderCountDto.setUserId(programOrderCreateDto.getUserId());
            accountOrderCountDto.setProgramId(programOrderCreateDto.getProgramId());
            ApiResponse<AccountOrderCountVo> apiResponse = orderClient.accountOrderCount(accountOrderCountDto);
            if (Objects.equals(apiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                // 解析查询结果，默认值为0（避免空指针）
                count = Optional.ofNullable(apiResponse.getData()).map(AccountOrderCountVo::getCount).orElse(0);
                // 将查询结果存入Redis，并设置过期时间（令牌过期时间+1分钟，避免缓存提前失效）
                redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT,
                                programOrderCreateDto.getUserId(),
                                programOrderCreateDto.getProgramId()),
                        count, tokenExpireManager.getTokenExpireTime() + 1, TimeUnit.MINUTES);
            }
        }
        // 3.3 计算当前订单的购票数量（选座模式为座位数，非选座模式为票券数）
        // 选座模式：座位列表大小即购票数
        Integer seatCount = Optional.ofNullable(programOrderCreateDto.getSeatDtoList()).map(List::size).orElse(0);
        // 非选座模式：直接取票券数量
        Integer ticketCount = Optional.ofNullable(programOrderCreateDto.getTicketCount()).orElse(0);
        // 3.4 累加当前订单数量到历史数量
        if (seatCount != 0) {
            count = count + seatCount;
        } else if (ticketCount != 0) {
            count = count + ticketCount;
        }
        // 3.5 校验是否超过单用户节目购票限制
        if (count > programVo.getPerAccountLimitPurchaseCount()) {
            throw new DaMaiFrameException(BaseCode.PER_ACCOUNT_PURCHASE_COUNT_OVER_LIMIT);
        }
    }

    @Override
    public Integer executeParentOrder() {
        return 1;
    }

    @Override
    public Integer executeTier() {
        return 2;
    }

    @Override
    public Integer executeOrder() {
        return 2;
    }
}
