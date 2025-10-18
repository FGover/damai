package com.damai.service;

import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 接口请求记录 实体对象
 * 可用于在请求网关、限流器、拦截器中做实时规则校验，并在触发阈值后把记录写入 Kafka 或存库，方便后续风控、告警。
 * @author: 阿星不是程序员
 **/
@Data
public class ApiRestrictData {

    /**
     * 触发结果标识
     * 0:未触发 1:已触发
     */
    private Long triggerResult;

    /**
     * 限流规则类型
     * 0：没有规则  1：普通规则  2：深度规则
     */
    private Long triggerCallStat;

    /**
     * API 已请求次数：
     * 记录当前请求累计次数，可用于和阈值比较。
     */
    private Long apiCount;

    /**
     * 限制阈值：
     * 规则配置的最大允许次数，如：每分钟最多 100 次。
     */
    private Long threshold;

    /**
     * 消息索引：
     * 把异常请求发往 Kafka，可用此字段记录消息在队列中的索引或 ID。
     * 也可以用于分布式幂等，防止重复处理。
     */
    private Long messageIndex;
}
