package com.damai.service.composite.impl;


import com.damai.dto.ProgramGetDto;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.enums.BaseCode;
import com.damai.enums.BusinessStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.service.ProgramService;
import com.damai.service.composite.AbstractProgramCheckHandler;
import com.damai.vo.ProgramVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目详情校验处理器
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramDetailCheckHandler extends AbstractProgramCheckHandler {

    @Autowired
    private ProgramService programService;

    /**
     * 执行节目详情相关校验逻辑
     * 主要包括：节目是否允许选座校验、购票数量是否超限校验
     *
     * @param programOrderCreateDto 节目订单创建请求数据传输对象
     */
    @Override
    protected void execute(final ProgramOrderCreateDto programOrderCreateDto) {
        // 构建节目查询参数对象
        ProgramGetDto programGetDto = new ProgramGetDto();
        programGetDto.setId(programOrderCreateDto.getProgramId());   // 设置要查询的节目ID
        // 调用服务查询节目详情
        ProgramVo programVo = programService.detailV2(programGetDto);
        // 校验是否允许选座
        // 如果节目不允许选座（permitChooseSeat为NO），但订单中包含座位信息，则抛出异常
        if (programVo.getPermitChooseSeat().equals(BusinessStatus.NO.getCode())) {
            if (Objects.nonNull(programOrderCreateDto.getSeatDtoList())) {
                throw new DaMaiFrameException(BaseCode.PROGRAM_NOT_ALLOW_CHOOSE_SEAT);
            }
        }
        // 计算实际购票数量（选座方式时为座位数量，非选座方式时为票券数量）
        Integer seatCount = Optional.ofNullable(programOrderCreateDto.getSeatDtoList())
                .map(List::size)  // 若存在座位列表则取其大小
                .orElse(0);    // 否则为0
        // 非选座方式的购票数量，为空时默认0
        Integer ticketCount = Optional.ofNullable(programOrderCreateDto.getTicketCount()).orElse(0);
        // 购票数量是否超出单订单限购额度
        // 无论是选座数量还是票券数量，只要有一个超过节目设置的单订单限购数，则抛出异常
        if (seatCount > programVo.getPerOrderLimitPurchaseCount() || ticketCount > programVo.getPerOrderLimitPurchaseCount()) {
            throw new DaMaiFrameException(BaseCode.PER_ORDER_PURCHASE_COUNT_OVER_LIMIT);
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
        return 1;
    }
}
