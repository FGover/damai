package com.damai.service.strategy;

import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目订单上下文，用于管理不同版本的节目订单处理策略
 * @author: 阿星不是程序员
 **/
public class ProgramOrderContext {

    /**
     * 存储节目订单策略的映射表
     * key：策略版本标识（V1，V2）
     * value：对应的订单处理策略实现类
     */
    private static final Map<String, ProgramOrderStrategy> MAP = new HashMap<>(8);

    /**
     * 注册节目订单处理策略
     * 将策略版本与具体策略实现类关联并存储到映射表中
     *
     * @param version              策略版本标识（用于后续获取对应策略）
     * @param programOrderStrategy 具体的订单处理策略实现
     */
    public static void add(String version, ProgramOrderStrategy programOrderStrategy) {
        MAP.put(version, programOrderStrategy);
    }

    /**
     * 根据版本标识获取对应的节目订单处理策略
     * 若未找到对应策略，则抛出策略不存在的异常
     *
     * @param version 策略版本标识
     * @return 对应的订单处理策略实现类
     */
    public static ProgramOrderStrategy get(String version) {
        // 使用Optional避免空指针，若MAP中无对应策略则抛出异常
        return Optional.ofNullable(MAP.get(version)).orElseThrow(() ->
                new DaMaiFrameException(BaseCode.PROGRAM_ORDER_STRATEGY_NOT_EXIST));
    }
}
