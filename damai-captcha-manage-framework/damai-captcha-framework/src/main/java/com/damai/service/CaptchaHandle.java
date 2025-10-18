package com.damai.service;

import com.damai.captcha.model.common.ResponseModel;
import com.damai.captcha.model.vo.CaptchaVO;
import com.damai.captcha.service.CaptchaService;
import com.damai.util.RemoteUtil;
import lombok.AllArgsConstructor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 验证码处理器类
 * 作为验证码功能的门面类，封装了验证码的生成、验证等核心业务逻辑
 * 负责处理HTTP请求上下文与验证码服务之间的交互，简化上层调用
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class CaptchaHandle {

    // 验证码核心服务接口，由构造方法注入具体实现（如图片验证码、滑块验证码等）
    private final CaptchaService captchaService;

    /**
     * 生成验证码
     *
     * @param captchaVO 验证码请求参数对象，包含验证码类型、业务场景等信息
     * @return ResponseModel 验证码生成结果，包含验证码图片/数据和唯一标识
     */
    public ResponseModel getCaptcha(CaptchaVO captchaVO) {
        // 从Spring上下文获取当前请求属性（包含HTTP请求信息）
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        assert requestAttributes != null;
        // 从请求属性中提取HTTPServletRequest对象
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取浏览器唯一标识，设置到验证码参数中
        // 用于绑定验证码与请求来源，增强安全性
        captchaVO.setBrowserInfo(RemoteUtil.getRemoteId(request));
        // 调用验证码服务生成验证码
        return captchaService.get(captchaVO);
    }

    /**
     * 验证用户提交的验证码
     * 同样绑定浏览器信息，确保验证请求与生成请求来自同一客户端
     *
     * @param captchaVO 验证码验证参数，包含用户输入的结果和验证码唯一标识
     * @return ResponseModel 验证结果（成功/失败，失败原因等）
     */
    public ResponseModel checkCaptcha(CaptchaVO captchaVO) {
        // 从上下文获取当前HTTP请求（与生成验证码时的逻辑一致）
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        assert requestAttributes != null;
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 设置浏览器唯一标识，与生成时的标识比对，防止跨站提交
        captchaVO.setBrowserInfo(RemoteUtil.getRemoteId(request));
        // 调用验证码服务进行验证，并返回结果
        return captchaService.check(captchaVO);
    }

    /**
     * 验证码二次验证（通常用于敏感操作前的再次校验）
     * 无需绑定请求上下文，适用于已获取验证码标识后的独立验证场景
     *
     * @param captchaVO 验证码验证参数，包含验证码标识和用户输入
     * @return ResponseModel 验证结果
     */
    public ResponseModel verification(CaptchaVO captchaVO) {
        // 直接调用验证码服务的二次验证方法
        return captchaService.verification(captchaVO);
    }
}
