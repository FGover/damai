package com.damai.config;

import com.damai.properties.AjCaptchaProperties;
import com.damai.captcha.service.CaptchaCacheService;
import com.damai.captcha.service.impl.CaptchaServiceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 验证码存储策略自动配置类
 * 负责根据配置创建并注册验证码缓存服务的Bean，实现验证码数据的存储管理
 * @author: 阿星不是程序员
 **/
@Configuration
public class AjCaptchaStorageAutoConfiguration {

    /**
     * 创建并注册验证码缓存服务Bean
     * 方法名"AjCaptchaCacheService"作为Bean的名称，便于在其他地方通过名称引用
     *
     * @param config 验证码配置属性对象，包含缓存类型等配置信息
     * @return 验证码缓存服务实例，具体实现由配置的缓存类型决定
     */
    @Bean(name = "AjCaptchaCacheService")
    // 条件注解：仅当Spring容器中不存在CaptchaCacheService类型的Bean时，才会创建当前Bean
    // 用于允许用户自定义实现类覆盖默认配置，提高灵活性
    @ConditionalOnMissingBean
    public CaptchaCacheService captchaCacheService(AjCaptchaProperties config) {
        //缓存类型redis/local/....
        return CaptchaServiceFactory.getCache(config.getCacheType().name());
    }
}
