package com.damai.enums;

import lombok.Getter;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 通用业务状态枚举类
 * @author: 阿星不是程序员
 **/

public enum BusinessStatus {
    /**
     * 通用状态枚举
     */
    YES(1, "是"),
    NO(0, "否");

    @Getter
    private Integer code;

    private String msg;

    BusinessStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取状态描述
     * 若描述为null则返回空字符串，避免NPE
     *
     * @return 状态描述文字
     */
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }

    /**
     * 根据编码获取对应的状态描述
     * 用于将数据库中的编码转换为可读的文字描述
     *
     * @param code 状态编码
     * @return 对应的描述文字，无匹配时返回空字符串
     */
    public static String getMsg(Integer code) {
        for (BusinessStatus re : BusinessStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    /**
     * 根据编码获取对应的枚举实例
     * 用于将编码转换为枚举对象，便于后续逻辑判断
     *
     * @param code 状态编码
     * @return 对应的枚举实例，无匹配时返回null
     */
    public static BusinessStatus getRc(Integer code) {
        for (BusinessStatus re : BusinessStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
