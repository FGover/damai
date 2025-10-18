package com.damai.service.strategy.impl;

import com.damai.core.RepeatExecuteLimitConstants;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.enums.CompositeCheckType;
import com.damai.enums.ProgramOrderVersion;
import com.damai.initialize.base.AbstractApplicationCommandLineRunnerHandler;
import com.damai.initialize.impl.composite.CompositeContainer;
import com.damai.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.damai.service.ProgramOrderService;
import com.damai.service.strategy.ProgramOrderContext;
import com.damai.service.strategy.ProgramOrderStrategy;
import com.damai.servicelock.annotion.ServiceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import static com.damai.core.DistributedLockConstants.PROGRAM_ORDER_CREATE_V1;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目订单v1版本策略实现类
 * 负责处理V1版本的节目订单创建逻辑，包含订单创建前的校验、分布式锁控制和重复提交限制
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramOrderV1Strategy extends AbstractApplicationCommandLineRunnerHandler implements ProgramOrderStrategy {

    @Autowired
    private ProgramOrderService programOrderService;

    @Autowired
    private CompositeContainer compositeContainer;

    /**
     * 创建节目订单的核心方法（V1版本实现）
     * 包含重复提交限制、分布式锁和前置校验逻辑
     *
     * @param programOrderCreateDto 节目订单创建请求数据传输对象，包含订单所需的所有参数
     * @return 创建成功的订单标识（如订单号）
     */
    @RepeatExecuteLimit(
            name = RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER,
            keys = {"#programOrderCreateDto.userId", "#programOrderCreateDto.programId"})
    @ServiceLock(name = PROGRAM_ORDER_CREATE_V1, keys = {"#programOrderCreateDto.programId"})
    @Override
    public String createOrder(final ProgramOrderCreateDto programOrderCreateDto) {
        compositeContainer.execute(CompositeCheckType.PROGRAM_ORDER_CREATE_CHECK.getValue(), programOrderCreateDto);
        return programOrderService.create(programOrderCreateDto);
    }

    @Override
    public Integer executeOrder() {
        return 1;
    }

    /**
     * 应用启动时的初始化方法
     * 将当前V1版本策略注册到策略上下文中，供后续根据版本号获取
     *
     * @param context 应用上下文对象
     */
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        ProgramOrderContext.add(ProgramOrderVersion.V1_VERSION.getVersion(), this);
    }
}
