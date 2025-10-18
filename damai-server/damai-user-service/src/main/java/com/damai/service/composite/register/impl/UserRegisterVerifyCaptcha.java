package com.damai.service.composite.register.impl;

import com.damai.captcha.model.common.ResponseModel;
import com.damai.captcha.model.vo.CaptchaVO;
import com.damai.core.RedisKeyManage;
import com.damai.util.StringUtil;
import com.damai.dto.UserRegisterDto;
import com.damai.enums.BaseCode;
import com.damai.enums.VerifyCaptcha;
import com.damai.exception.DaMaiFrameException;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.CaptchaHandle;
import com.damai.service.composite.register.AbstractUserRegisterCheckHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 用户注册流程中的验证码验证组件
 * 属于注册校验组件树中的一个叶子节点，负责验证用户注册时的密码一致性和验证码有效性
 * 基于组合模式，通过继承AbstractUserRegisterCheckHandler融入注册校验流程
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class UserRegisterVerifyCaptcha extends AbstractUserRegisterCheckHandler {

    @Autowired
    private CaptchaHandle captchaHandle;

    @Autowired
    private RedisCache redisCache;

    /**
     * 执行具体的验证逻辑（密码 + 验证码必要性及有效性校验）
     *
     * @param param 泛型参数，用于传递业务执行所需的数据（如订单DTO、请求参数等）
     */
    @Override
    protected void execute(UserRegisterDto param) {
        // 检查密码与确认密码是否相同
        String password = param.getPassword();
        String confirmPassword = param.getConfirmPassword();
        // 密码不一致时抛出异常，终止注册流程（异常信息会被上层捕获并返回给前端）
        if (!password.equals(confirmPassword)) {
            throw new DaMaiFrameException(BaseCode.TWO_PASSWORDS_DIFFERENT);
        }
        // 从redis中获取验证码标识（是否需要验证的标记，由Lua脚本设置）
        // 键格式：VERIFY_CAPTCHA_ID:{captchaId}，值为"yes"或"no"
        String verifyCaptcha = redisCache.get(
                RedisKeyBuild.createRedisKey(RedisKeyManage.VERIFY_CAPTCHA_ID, param.getCaptchaId()), String.class);
        // 验证码标识不存在（可能已过期或非法请求）
        if (StringUtil.isEmpty(verifyCaptcha)) {
            throw new DaMaiFrameException(BaseCode.VERIFY_CAPTCHA_ID_NOT_EXIST);
        }
        // 标识为"yes"：需要验证验证码
        if (VerifyCaptcha.YES.getValue().equals(verifyCaptcha)) {
            // 检查用户是否提交了验证码结果
            if (StringUtil.isEmpty(param.getCaptchaVerification())) {
                throw new DaMaiFrameException(BaseCode.VERIFY_CAPTCHA_EMPTY);
            }
            log.info("传入的captchaVerification:{}", param.getCaptchaVerification());
            CaptchaVO captchaVO = new CaptchaVO();
            captchaVO.setCaptchaVerification(param.getCaptchaVerification());  // 设置用户提交的验证码信息
            ResponseModel responseModel = captchaHandle.verification(captchaVO);  // 调用验证接口
            if (!responseModel.isSuccess()) {
                throw new DaMaiFrameException(responseModel.getRepCode(), responseModel.getRepMsg());
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
