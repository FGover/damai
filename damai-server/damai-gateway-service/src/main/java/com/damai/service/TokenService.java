package com.damai.service;

import com.alibaba.fastjson.JSONObject;
import com.damai.core.RedisKeyManage;
import com.damai.util.StringUtil;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.jwt.TokenUtil;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.vo.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: token数据获取服务，负责解析token并从redis获取用户信息
 * @author: 阿星不是程序员
 **/

@Component
public class TokenService {

    @Autowired
    private RedisCache redisCache;

    /**
     * 解析前端传来的token，获取userId
     *
     * @param token       前端传递的 JWT token
     * @param tokenSecret 签名用的密钥
     * @return
     */
    public String parseToken(String token, String tokenSecret) {
        // 调用工具类解析 token，解析到的结果是一个 JSON 字符串
        String userStr = TokenUtil.parseToken(token, tokenSecret);
        if (StringUtil.isNotEmpty(userStr)) {
            // 把解析出来的 JSON 转成对象，从中提取 userId
            return JSONObject.parseObject(userStr).getString("userId");
        }
        return null;
    }

    /**
     * 根据token和code从redis中获取当前登录用户信息
     *
     * @param token       前端传递的 JWT token
     * @param code        渠道编码（多渠道场景下区分不同渠道）
     * @param tokenSecret JWT 密钥
     * @return UserVo 用户信息
     */
    public UserVo getUser(String token, String code, String tokenSecret) {
        UserVo userVo = null;
        // 先解析 token 得到 userId
        String userId = parseToken(token, tokenSecret);
        if (StringUtil.isNotEmpty(userId)) {
            // 拼接 Redis key，然后从缓存中获取用户信息
            userVo = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, code, userId), UserVo.class);
        }
        // 如果缓存中找不到用户信息，就抛出未登录异常
        return Optional.ofNullable(userVo).orElseThrow(() -> new DaMaiFrameException(BaseCode.LOGIN_USER_NOT_EXIST));
    }
}
