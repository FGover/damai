package com.damai.service.composite.register.impl;

import com.damai.dto.UserRegisterDto;
import com.damai.service.UserService;
import com.damai.service.composite.register.AbstractUserRegisterCheckHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 检查用户是否存在的组件
 * 属于注册校验组件树中的叶子节点，负责验证手机号是否已被注册
 * 基于组合模式融入注册流程，确保注册用户的唯一性
 * @author: 阿星不是程序员
 **/
@Component
public class UserExistCheckHandler extends AbstractUserRegisterCheckHandler {

    @Autowired
    private UserService userService;

    /**
     * 执行用户存在性校验逻辑
     * 调用用户服务检查手机号是否已注册，若已存在则抛出异常终止注册
     *
     * @param userRegisterDto 泛型参数，用于传递业务执行所需的数据（如订单DTO、请求参数等）
     */
    @Override
    public void execute(final UserRegisterDto userRegisterDto) {
        userService.doExist(userRegisterDto.getMobile());
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
