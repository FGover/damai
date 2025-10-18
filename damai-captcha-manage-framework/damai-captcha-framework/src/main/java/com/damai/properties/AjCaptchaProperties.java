package com.damai.properties;

import com.damai.captcha.model.common.CaptchaTypeEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.awt.*;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 配置属性
 * @author: 阿星不是程序员
 **/

@Setter
@ConfigurationProperties(AjCaptchaProperties.PREFIX)
public class AjCaptchaProperties {
    public static final String PREFIX = "aj.captcha";

    /**
     * 验证码类型.
     */
    private CaptchaTypeEnum type = CaptchaTypeEnum.DEFAULT;

    /**
     * 滑动拼图底图路径.
     */
    private String jigsaw = "";

    /**
     * 点选文字底图路径.
     */
    private String picClick = "";


    /**
     * 右下角水印文字(我的水印).
     */
    private String waterMark = "我的水印";

    /**
     * 右下角水印字体(文泉驿正黑).
     */
    private String waterFont = "WenQuanZhengHei.ttf";

    /**
     * 点选文字验证码的文字字体(文泉驿正黑).
     */
    private String fontType = "WenQuanZhengHei.ttf";

    /**
     * 校验滑动拼图允许误差偏移量(默认5像素).
     */
    private String slipOffset = "5";

    /**
     * aes加密坐标开启或者禁用(true|false).
     */
    private Boolean aesStatus = true;

    /**
     * 滑块干扰项(0/1/2)
     */
    private String interferenceOptions = "0";

    /**
     * local缓存的阈值
     */
    private String cacheNumber = "1000";

    /**
     * 定时清理过期local缓存(单位秒)
     */
    private String timingClear = "180";

    /**
     * 缓存类型redis/local/....
     */
    private StorageType cacheType = StorageType.local;
    /**
     * 历史数据清除开关
     */
    @Getter
    private boolean historyDataClearEnable = false;

    /**
     * 一分钟内接口请求次数限制 开关
     */
    @Getter
    private boolean reqFrequencyLimitEnable = false;

    /***
     * 一分钟内check接口失败次数
     */
    @Getter
    private int reqGetLockLimit = 5;
    /**
     *
     */
    @Getter
    private int reqGetLockSeconds = 300;

    /***
     * get接口一分钟内限制访问数
     */
    @Getter
    private int reqGetMinuteLimit = 100;
    private int reqCheckMinuteLimit = 100;
    @Getter
    private int reqVerifyMinuteLimit = 100;

    /**
     * 点选字体样式
     */
    @Getter
    private int fontStyle = Font.BOLD;

    /**
     * 点选字体大小
     */
    @Getter
    private int fontSize = 25;

    /**
     * 点选文字个数，存在问题，暂不要使用
     */
    @Getter
    private int clickWordCount = 4;

    public boolean getReqFrequencyLimitEnable() {
        return reqFrequencyLimitEnable;
    }

    public int getReqCheckMinuteLimit() {
        return reqGetMinuteLimit;
    }

    public enum StorageType {
        /**
         * 内存.
         */
        local,
        /**
         * redis.
         */
        redis,
        /**
         * 其他.
         */
        other,
    }

    public static String getPrefix() {
        return PREFIX;
    }

    public CaptchaTypeEnum getType() {
        return type;
    }

    public String getJigsaw() {
        return jigsaw;
    }

    public String getPicClick() {
        return picClick;
    }

    public String getWaterMark() {
        return waterMark;
    }

    public String getWaterFont() {
        return waterFont;
    }

    public String getFontType() {
        return fontType;
    }

    public String getSlipOffset() {
        return slipOffset;
    }

    public Boolean getAesStatus() {
        return aesStatus;
    }

    public StorageType getCacheType() {
        return cacheType;
    }

    public String getInterferenceOptions() {
        return interferenceOptions;
    }

    public String getCacheNumber() {
        return cacheNumber;
    }

    public String getTimingClear() {
        return timingClear;
    }

    @Override
    public String toString() {
        return "\nAjCaptchaProperties{" +
                "type=" + type +
                ", jigsaw='" + jigsaw + '\'' +
                ", picClick='" + picClick + '\'' +
                ", waterMark='" + waterMark + '\'' +
                ", waterFont='" + waterFont + '\'' +
                ", fontType='" + fontType + '\'' +
                ", slipOffset='" + slipOffset + '\'' +
                ", aesStatus=" + aesStatus +
                ", interferenceOptions='" + interferenceOptions + '\'' +
                ", cacheNumber='" + cacheNumber + '\'' +
                ", timingClear='" + timingClear + '\'' +
                ", cacheType=" + cacheType +
                ", reqFrequencyLimitEnable=" + reqFrequencyLimitEnable +
                ", reqGetLockLimit=" + reqGetLockLimit +
                ", reqGetLockSeconds=" + reqGetLockSeconds +
                ", reqGetMinuteLimit=" + reqGetMinuteLimit +
                ", reqCheckMinuteLimit=" + reqCheckMinuteLimit +
                ", reqVerifyMinuteLimit=" + reqVerifyMinuteLimit +
                '}';
    }
}
