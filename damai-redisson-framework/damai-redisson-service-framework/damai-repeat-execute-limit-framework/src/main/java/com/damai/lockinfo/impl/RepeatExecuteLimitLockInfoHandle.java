package com.damai.lockinfo.impl;

import com.damai.lockinfo.AbstractLockInfoHandle;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 防重复执行（幂等性）的锁信息处理器实现类
 * 负责生成防重复执行场景下的锁前缀，为分布式锁的唯一键提供统一标识
 * @author: 阿星不是程序员
 **/
public class RepeatExecuteLimitLockInfoHandle extends AbstractLockInfoHandle {

    // 防重复执行场景的锁前缀（用于区分不同业务场景的锁）
    public static final String PREFIX_NAME = "REPEAT_EXECUTE_LIMIT";

    /**
     * 获取当前业务场景的锁前缀
     * 继承自抽象类AbstractLockInfoHandle，实现具体的锁前缀生成逻辑
     *
     * @return 防重复执行场景的锁前缀字符串（"REPEAT_EXECUTE_LIMIT"）
     */
    @Override
    protected String getLockPrefixName() {
        return PREFIX_NAME;
    }
}
