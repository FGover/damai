package com.damai.config;

import com.damai.properties.AjCaptchaProperties;
import com.damai.captcha.service.CaptchaCacheService;
import com.damai.captcha.service.CaptchaService;
import com.damai.captcha.service.impl.CaptchaServiceFactory;
import com.damai.service.CaptchaCacheServiceRedisImpl;
import com.damai.service.CaptchaHandle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 验证码核心配置类
 * 负责整合验证码服务、缓存服务及相关依赖，实现Spring容器对验证码组件的管理
 * 解决SPI机制与Spring依赖注入的衔接问题，确保分布式环境下验证码功能正常工作
 * @author: 阿星不是程序员
 **/
public class CaptchaAutoConfig {

    /**
     * 注册验证码处理器Bean
     *
     * @param captchaService 验证码服务实例（由SPI机制加载并通过Spring管理）
     * @return 初始化完成的CaptchaHandle实例，用于处理验证码相关业务
     */
    @Bean
    public CaptchaHandle captchaHandle(CaptchaService captchaService) {
        // 创建验证码处理器实例，并注入验证码服务依赖
        return new CaptchaHandle(captchaService);
    }

    /**
     * 注册验证码缓存服务Bean，并处理Redis依赖注入
     * 是SPI机制与Spring容器衔接的关键方法，确保缓存服务能正常使用Spring管理的资源
     *
     * @param config        验证码配置属性（包含缓存类型等配置，从application配置文件加载）
     * @param redisTemplate Spring管理的Redis操作模板（用于Redis缓存实现）
     * @return 初始化完成的CaptchaCacheService实例，注册到Spring容器中
     */
    @Bean(name = "AjCaptchaCacheService") // 指定Bean名称，便于其他组件通过名称注入
    @Primary  // 当存在多个CaptchaCacheService实现时，优先使用当前Bean
    public CaptchaCacheService captchaCacheService(AjCaptchaProperties config, StringRedisTemplate redisTemplate) {
        // 通过工厂类（基于SPI机制）获取缓存服务实例
        CaptchaCacheService ret = CaptchaServiceFactory.getCache(config.getCacheType().name());
        // 特殊处理Redis缓存实现：手动注入Spring管理的StringRedisTemplate
        if (ret instanceof CaptchaCacheServiceRedisImpl) {
            ((CaptchaCacheServiceRedisImpl) ret).setStringRedisTemplate(redisTemplate);
        }
        return ret;
    }
}
