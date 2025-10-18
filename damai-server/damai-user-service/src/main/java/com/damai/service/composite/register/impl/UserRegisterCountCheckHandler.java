package com.damai.service.composite.register.impl;

import com.damai.dto.UserRegisterDto;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.service.composite.register.AbstractUserRegisterCheckHandler;
import com.damai.service.tool.RequestCounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 用户注册请求数检查组件，负责限制单位时间内的注册请求次数
 * 基于组合模式，通过继承抽象类融入注册校验流程，防止恶意批量注册
 * @author: 阿星不是程序员
 **/
@Component
public class UserRegisterCountCheckHandler extends AbstractUserRegisterCheckHandler {

    /**
     * 请求计数器，用于统计和限制单位时间内的注册请求次数
     */
    @Autowired
    private RequestCounter requestCounter;

    /**
     * 执行注册请求次数检查逻辑
     * 调用请求计数器判断当前请求是否超过频率限制，若超过则抛出异常终止注册
     *
     * @param param 泛型参数，用于传递业务执行所需的数据（如订单DTO、请求参数等）
     */
    @Override
    protected void execute(final UserRegisterDto param) {
        // 调用计数器计数器的onRequest()方法，判断当前请求是否触发频率限制
        // result为true表示超过限制，false表示正常
        boolean result = requestCounter.onRequest();
        if (result) {
            // 超过频率限制时抛出异常，提示"注册过于频繁"
            throw new DaMaiFrameException(BaseCode.USER_REGISTER_FREQUENCY);
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
