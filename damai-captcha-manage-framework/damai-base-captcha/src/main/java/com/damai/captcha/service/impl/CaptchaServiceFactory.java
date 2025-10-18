package com.damai.captcha.service.impl;

import com.damai.captcha.model.common.Const;
import com.damai.captcha.service.CaptchaCacheService;
import com.damai.captcha.service.CaptchaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 验证码服务工厂类
 * 采用工厂模式和服务加载机制，负责管理和提供验证码服务及缓存服务的实例
 * 实现了服务的动态发现和实例化，提高了系统的扩展性和灵活性
 * @author: 阿星不是程序员
 **/
public class CaptchaServiceFactory {

    // 日志记录器，用于记录工厂类的运行信息和错误
    private static Logger logger = LoggerFactory.getLogger(CaptchaServiceFactory.class);
    // 存储验证码服务实例的Map，key为验证码类型，value为对应的服务实例
    public volatile static Map<String, CaptchaService> instances = new HashMap<>();
    // 存储缓存服务实例的Map，key为缓存类型，value为对应的缓存服务实例
    public volatile static Map<String, CaptchaCacheService> cacheService = new HashMap<>();

    // 静态代码块，在类加载时执行，用于初始化服务实例
    static {
        // 使用Java的ServiceLoader机制加载所有实现了CaptchaCacheService接口的服务
        // 这是一种SPI（Service Provider Interface）机制，允许第三方提供实现并自动发现
        ServiceLoader<CaptchaCacheService> cacheServices = ServiceLoader.load(CaptchaCacheService.class);
        // 遍历所有加载的缓存服务，将其按类型存入Map
        for (CaptchaCacheService item : cacheServices) {
            cacheService.put(item.type(), item);
        }
        // 记录支持的缓存服务类型
        logger.info("supported-captchaCache-service:{}", cacheService.keySet());
        // 使用Java的ServiceLoader机制加载所有实现了CaptchaService接口的服务
        ServiceLoader<CaptchaService> services = ServiceLoader.load(CaptchaService.class);
        // 遍历所有加载的验证码服务，将其按类型存入Map
        for (CaptchaService item : services) {
            instances.put(item.captchaType(), item);
        }
        // 记录支持的验证码服务类型
        logger.info("supported-captchaTypes-service:{}", instances.keySet());
    }

    /**
     * 获取验证码服务实例
     * 根据配置的验证码类型，返回对应的验证码服务实现
     *
     * @param config 配置属性对象，包含验证码类型等配置信息
     * @return 对应的CaptchaService实例，已初始化完成
     */
    public static CaptchaService getInstance(Properties config) {
        // 从配置中获取验证码类型，默认使用default类型
        String captchaType = config.getProperty(Const.CAPTCHA_TYPE, "default");
        // 从缓存的实例Map中获取对应类型的服务实例
        CaptchaService ret = instances.get(captchaType);
        // 如果没有找到对应类型的服务实例，则抛出异常
        if (ret == null) {
            throw new RuntimeException("unsupported-[captcha.type]=" + captchaType);
        }
        // 初始化服务实例
        ret.init(config);
        return ret;
    }

    /**
     * 获取验证码缓存服务实例
     * 根据缓存类型，返回对应的缓存服务实现
     *
     * @param cacheType 缓存类型（如redis、local等）
     * @return 对应的CaptchaCacheService实例
     */
    public static CaptchaCacheService getCache(String cacheType) {
        // 从缓存服务Map中获取对应类型的实例
        return cacheService.get(cacheType);
    }

}
