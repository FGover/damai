package com.damai.jwt;

import com.alibaba.fastjson.JSONObject;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: token工具
 * @author: 阿星不是程序员
 **/
@Slf4j
public class TokenUtil {

    // 定义JWT使用的签名算法，这里选用 HS256（对称加密，使用相同密钥进行加解密）
    private static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS256;

    /**
     * 创建JWT Token
     *
     * @param id          JWT 的唯一标识
     * @param info        放到 JWT 的主体信息（可以是 JSON）
     * @param ttlMillis   Token 有效期，单位毫秒
     * @param tokenSecret 签名密钥
     * @return 生成的 JWT Token 字符串
     */
    public static String createToken(String id, String info, long ttlMillis, String tokenSecret) {
        // 获取当前时间戳
        long nowMillis = System.currentTimeMillis();
        // 构建JWT
        JwtBuilder builder = Jwts.builder()
                .setId(id)  // 设置唯一id
                .setIssuedAt(new Date(nowMillis))  // 设置签发时间
                .setSubject(info)  // 设置主体内容（用户自定义信息，一般是 JSON）
                .signWith(SIGNATURE_ALGORITHM, tokenSecret);  // 设置签名算法和密钥
        // 如果设置了有效期，则设置过期时间
        if (ttlMillis >= 0) {
            //设置过期时间
            builder.setExpiration(new Date(nowMillis + ttlMillis));
        }
        // 构造并返回最终的JWT Token字符串
        return builder.compact();
    }

    /**
     * 解析 JWT Token，获取里面的主体信息（即 createToken 时设置的 info）
     *
     * @param token       前端传过来的 Token
     * @param tokenSecret 签名密钥
     * @return JWT 中的主体信息（如 JSON 字符串）
     */
    public static String parseToken(String token, String tokenSecret) {
        try {
            return Jwts.parser()
                    .setSigningKey(tokenSecret)  // 设置解析时使用的签名密钥
                    .parseClaimsJws(token)  // 解析 Token
                    .getBody() // 获取 Token 中的主体信息
                    .getSubject();
        } catch (ExpiredJwtException jwtException) {
            // 如果解析时抛出 Token 过期异常，记录错误日志并抛自定义异常
            log.error("parseToken error", jwtException);
            throw new DaMaiFrameException(BaseCode.TOKEN_EXPIRE);
        }

    }

    public static void main(String[] args) {
        String tokenSecret = "CSYZWECHAT";

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("001key", "001value");
        jsonObject.put("002key", "002value");

        String token = TokenUtil.createToken("1", jsonObject.toJSONString(), 10000, tokenSecret);
        System.out.println("生成的token: " + token);

        String subject = TokenUtil.parseToken(token, tokenSecret);
        System.out.println("解析token后的值: " + subject);
    }

}