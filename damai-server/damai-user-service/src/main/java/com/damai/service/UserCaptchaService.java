package com.damai.service;

import com.damai.captcha.model.common.ResponseModel;
import com.damai.captcha.model.vo.CaptchaVO;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.core.RedisKeyManage;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.lua.CheckNeedCaptchaOperate;
import com.damai.vo.CheckNeedCaptchaDataVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * 用户验证码服务类
 * @description: 判断是否需要验证码、生成验证码及验证验证码
 * @author: 阿星不是程序员
 **/
@Service
public class UserCaptchaService {

    /**
     * 触发验证码的阈值（从配置文件中读取）
     */
    @Value("${verify_captcha_threshold:10}")
    private int verifyCaptchaThreshold;

    /**
     * 验证码ID过期时间（秒）
     */
    @Value("${verify_captcha_id_expire_time:60}")
    private int verifyCaptchaIdExpireTime;

    /**
     * 是否强制验证验证码（从配置文件中读取）
     */
    @Value("${always_verify_captcha:0}")
    private int alwaysVerifyCaptcha;

    /**
     * 验证码处理器，封装验证码生成和验证的核心逻辑
     */
    @Autowired
    private CaptchaHandle captchaHandle;

    /**
     * 分布式ID生成器
     */
    @Autowired
    private UidGenerator uidGenerator;

    /*
     * 基于Lua脚本的“是否需要验证码”判断操作
     */
    @Autowired
    private CheckNeedCaptchaOperate checkNeedCaptchaOperate;

    /**
     * 判断当前用户操作是否需要验证验证码
     * 结合Redis计数器和Lua脚本实现高并发场景下的精准判断
     *
     * @return CheckNeedCaptchaDataVo 包含是否需要验证及验证码ID的结果对象
     */
    public CheckNeedCaptchaDataVo checkNeedCaptcha() {
        // 获取当前时间戳
        long currentTimeMillis = System.currentTimeMillis();
        // 生成唯一验证码ID
        long id = uidGenerator.getUid();
        // 构建Redis操作所需的键列表
        List<String> keys = new ArrayList<>();
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.COUNTER_COUNT).getRelKey());  // 操作次数计数器键
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.COUNTER_TIMESTAMP).getRelKey());  // 时间戳键（用于频率计算）
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.VERIFY_CAPTCHA_ID, id).getRelKey());  // 验证码ID键
        // 构建Lua脚本所需的参数数组
        String[] data = new String[4];
        data[0] = String.valueOf(verifyCaptchaThreshold);  // 触发阈值
        data[1] = String.valueOf(currentTimeMillis);   // 当前时间戳
        data[2] = String.valueOf(verifyCaptchaIdExpireTime);  // 验证码ID过期时间
        data[3] = String.valueOf(alwaysVerifyCaptcha);  // 是否强制验证
        // 调用Lua脚本执行判断逻辑
        Boolean result = checkNeedCaptchaOperate.checkNeedCaptchaOperate(keys, data);
        // 封装结果对象并返回
        CheckNeedCaptchaDataVo checkNeedCaptchaDataVo = new CheckNeedCaptchaDataVo();
        checkNeedCaptchaDataVo.setCaptchaId(id);  // 设置验证码ID
        checkNeedCaptchaDataVo.setVerifyCaptcha(result);  // 设置是否需要验证
        return checkNeedCaptchaDataVo;
    }

    /**
     * 生成验证码，委托给CaptchaHandle处理具体的生成逻辑
     *
     * @param captchaVO
     * @return
     */
    public ResponseModel getCaptcha(CaptchaVO captchaVO) {
        return captchaHandle.getCaptcha(captchaVO);
    }

    /**
     * 验证验证码，委托给CaptchaHandle处理具体的验证逻辑
     *
     * @param captchaVO
     * @return
     */
    public ResponseModel verifyCaptcha(final CaptchaVO captchaVO) {
        return captchaHandle.checkCaptcha(captchaVO);
    }
}
