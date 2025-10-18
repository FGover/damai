package com.damai.lockinfo;

import org.aspectj.lang.JoinPoint;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 锁信息处理器接口
 * 定义生成锁名称的标准方法，用于在分布式锁场景中生成唯一的锁标识，不同业务场景（如防重复执行、库存扣减）可通过实现该接口定制锁信息生成逻辑
 * @author: 阿星不是程序员
 **/
public interface LockInfoHandle {

    /**
     * 根据切面信息、业务名称和键数组生成完整的锁名称（用于分布式锁的唯一标识）
     *
     * @param joinPoint 切面
     * @param name      锁业务名
     * @param keys      键数组
     * @return 完整的锁名称字符串（格式通常为"环境前缀-业务前缀-业务名-具体键"）
     */
    String getLockName(JoinPoint joinPoint, String name, String[] keys);

    /**
     * 简单拼装锁名称（不依赖切面信息，用于快速生成锁标识）
     *
     * @param name 锁业务名
     * @param keys 键数组
     * @return 简单拼装的锁名称字符串
     */
    String simpleGetLockName(String name, String[] keys);
}
