package com.damai.config;


import com.damai.captcha.model.common.Const;
import com.damai.captcha.service.CaptchaService;
import com.damai.captcha.service.impl.CaptchaServiceFactory;
import com.damai.captcha.util.ImageUtils;
import com.damai.captcha.util.StringUtils;
import com.damai.properties.AjCaptchaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.FileCopyUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: AjCaptchaServiceAutoConfiguration验证码服务自动配置类
 * 负责将验证码配置属性（AjCaptchaProperties）转换为服务所需的配置，并初始化验证码核心服务（CaptchaService)
 * 同时处理自定义图片资源的加载，支持从classpath加载验证码底图（如拼图、点选图等）
 * @author: 阿星不是程序员
 **/

@Configuration
public class AjCaptchaServiceAutoConfiguration {

    // 日志记录器
    private static Logger logger = LoggerFactory.getLogger(AjCaptchaServiceAutoConfiguration.class);

    /**
     * 创建并注册验证码核心服务Bean（CaptchaService）
     * 从配置属性中提取参数，转换为服务所需的配置格式，通过工厂类创建具体服务实例
     *
     * @param prop 验证码配置属性对象（从application配置文件绑定而来）
     * @return CaptchaService 验证码核心服务实例，用于生成和验证验证码
     */
    @Bean
    @ConditionalOnMissingBean
    public CaptchaService captchaService(AjCaptchaProperties prop) {
        logger.info("自定义配置项：{}", prop.toString());
        Properties config = new Properties();
        config.put(Const.CAPTCHA_CACHETYPE, prop.getCacheType().name());
        config.put(Const.CAPTCHA_WATER_MARK, prop.getWaterMark());
        config.put(Const.CAPTCHA_FONT_TYPE, prop.getFontType());
        config.put(Const.CAPTCHA_TYPE, prop.getType().getCodeValue());
        config.put(Const.CAPTCHA_INTERFERENCE_OPTIONS, prop.getInterferenceOptions());
        config.put(Const.ORIGINAL_PATH_JIGSAW, prop.getJigsaw());
        config.put(Const.ORIGINAL_PATH_PIC_CLICK, prop.getPicClick());
        config.put(Const.CAPTCHA_SLIP_OFFSET, prop.getSlipOffset());
        config.put(Const.CAPTCHA_AES_STATUS, String.valueOf(prop.getAesStatus()));
        config.put(Const.CAPTCHA_WATER_FONT, prop.getWaterFont());
        config.put(Const.CAPTCHA_CACAHE_MAX_NUMBER, prop.getCacheNumber());
        config.put(Const.CAPTCHA_TIMING_CLEAR_SECOND, prop.getTimingClear());
        config.put(Const.HISTORY_DATA_CLEAR_ENABLE, prop.isHistoryDataClearEnable() ? "1" : "0");
        config.put(Const.REQ_FREQUENCY_LIMIT_ENABLE, prop.getReqFrequencyLimitEnable() ? "1" : "0");
        config.put(Const.REQ_GET_LOCK_LIMIT, String.valueOf(prop.getReqGetLockLimit()));
        config.put(Const.REQ_GET_LOCK_SECONDS, String.valueOf(prop.getReqGetLockSeconds()));
        config.put(Const.REQ_GET_MINUTE_LIMIT, String.valueOf(prop.getReqGetMinuteLimit()));
        config.put(Const.REQ_CHECK_MINUTE_LIMIT, String.valueOf(prop.getReqCheckMinuteLimit()));
        config.put(Const.REQ_VALIDATE_MINUTE_LIMIT, String.valueOf(prop.getReqVerifyMinuteLimit()));
        config.put(Const.CAPTCHA_FONT_SIZE, String.valueOf(prop.getFontSize()));
        config.put(Const.CAPTCHA_FONT_STYLE, String.valueOf(prop.getFontStyle()));
        config.put(Const.CAPTCHA_WORD_COUNT, String.valueOf(prop.getClickWordCount()));
        // 判断是否需要从classpath加载自定义底图（路径以classpath:开头）
        boolean result1 = StringUtils.isNotBlank(prop.getJigsaw()) && prop.getJigsaw().startsWith("classpath:");
        boolean result2 = StringUtils.isNotBlank(prop.getPicClick()) && prop.getPicClick().startsWith("classpath:");
        if (result1 || result2) {
            // 标记需要初始化自定义底图
            config.put(Const.CAPTCHA_INIT_ORIGINAL, "true");
            // 初始化底图资源（加载拼图和点选图）
            initializeBaseMap(prop.getJigsaw(), prop.getPicClick());
        }
        // 通过工厂类创建验证码服务实例（基于SPI机制，根据配置动态选择实现）
        return CaptchaServiceFactory.getInstance(config);
    }

    /**
     * 初始化验证码地图资源
     * 从配置的路径加载拼图底层、滑块图和点选图，缓存到工具类中供验证码生成使用
     *
     * @param jigsaw   拼图资源路径（包含底图和滑块图）
     * @param picClick 点选图资源路径
     */
    private static void initializeBaseMap(String jigsaw, String picClick) {
        // 加载拼图底图、滑块图和点选图，转换为Base64后缓存
        ImageUtils.cacheBootImage(getResourcesImagesFile(jigsaw + "/original/*.png"), // 拼图底图
                getResourcesImagesFile(jigsaw + "/slidingBlock/*.png"),  // 拼图滑块
                getResourcesImagesFile(picClick + "/*.png"));  // 点选图
    }

    /**
     * 从classpath加载图片资源，并转换为Base64字符串
     * 支持通过通配符路径批量加载图片（如classpath:images/*.png）
     *
     * @param path 图片资源路径（含通配符）
     * @return Map<String, String> 图片文件名到Base64字符串的映射
     */
    public static Map<String, String> getResourcesImagesFile(String path) {
        // 初始化Map存储图片
        Map<String, String> imgMap = new HashMap<>(64);
        // 资源解析器，用于加载classpath下的资源
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            // 根据路径加载所有匹配的资源
            Resource[] resources = resolver.getResources(path);
            for (Resource resource : resources) {
                // 读取图片文件内容为字节数组
                byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
                // 将字节数组转换为Base64字符串（便于在前端展示和传输）
                String string = Base64.getEncoder().encodeToString(bytes);
                // 获取文件名作为key，Base64字符串作为value存入Map
                String filename = resource.getFilename();
                imgMap.put(filename, string);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imgMap;
    }
}
