package com.damai.service.composite.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.dto.SeatDto;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.service.composite.AbstractProgramCheckHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目订单参数检查处理器
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramOrderCreateParamCheckHandler extends AbstractProgramCheckHandler {

    /**
     * 执行订单创建参数校验的核心逻辑
     *
     * @param programOrderCreateDto 泛型参数，用于传递业务执行所需的数据（如订单DTO、请求参数等）
     */
    @Override
    protected void execute(final ProgramOrderCreateDto programOrderCreateDto) {
        // 获取座位列表
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        // 获取购票人用户ID列表
        List<Long> ticketUserIdList = programOrderCreateDto.getTicketUserIdList();
        // 校验用户ID是否存在重复
        // 将用户ID列表按ID分组，统计每个ID出现的次数
        Map<Long, List<Long>> ticketUserIdMap = ticketUserIdList.stream()
                .collect(Collectors.groupingBy(ticketUserId -> ticketUserId));
        // 遍历分组后的结果，检查是否有ID出现多次的用户，则抛出异常
        for (List<Long> value : ticketUserIdMap.values()) {
            if (value.size() > 1) {
                throw new DaMaiFrameException(BaseCode.TICKET_USER_ID_REPEAT);
            }
        }
        // 如果座位列表不为空，则进行参数校验
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            // 校验座位数量和购票人用户ID数量是否相等（一个人一张票）
            if (seatDtoList.size() != programOrderCreateDto.getTicketUserIdList().size()) {
                throw new DaMaiFrameException(BaseCode.TICKET_USER_COUNT_UNEQUAL_SEAT_COUNT);
            }
            // 校验座位参数是否为空
            for (SeatDto seatDto : seatDtoList) {
                if (Objects.isNull(seatDto.getId())) {
                    throw new DaMaiFrameException(BaseCode.SEAT_ID_EMPTY);
                }
                if (Objects.isNull(seatDto.getTicketCategoryId())) {
                    throw new DaMaiFrameException(BaseCode.SEAT_TICKET_CATEGORY_ID_EMPTY);
                }
                if (Objects.isNull(seatDto.getRowCode())) {
                    throw new DaMaiFrameException(BaseCode.SEAT_ROW_CODE_EMPTY);
                }
                if (Objects.isNull(seatDto.getColCode())) {
                    throw new DaMaiFrameException(BaseCode.SEAT_COL_CODE_EMPTY);
                }
                if (Objects.isNull(seatDto.getPrice())) {
                    throw new DaMaiFrameException(BaseCode.SEAT_PRICE_EMPTY);
                }
            }
        } else {
            // 如果座位列表为空，则校验票档ID和数量参数
            if (Objects.isNull(programOrderCreateDto.getTicketCategoryId())) {
                throw new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST);
            }
            if (Objects.isNull(programOrderCreateDto.getTicketCount())) {
                throw new DaMaiFrameException(BaseCode.TICKET_COUNT_NOT_EXIST);
            }
            if (programOrderCreateDto.getTicketCount() <= 0) {
                throw new DaMaiFrameException(BaseCode.TICKET_COUNT_ERROR);
            }
        }
    }

    @Override
    public Integer executeParentOrder() {
        return 0;
    }

    @Override
    public Integer executeTier() {
        return 1;
    }

    @Override
    public Integer executeOrder() {
        return 1;
    }
}
