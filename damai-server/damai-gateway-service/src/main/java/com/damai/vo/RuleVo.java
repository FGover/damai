package com.damai.vo;

import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 普通对象 返回vo
 * @author: 阿星不是程序员
 **/
@Data
public class RuleVo {
    
    private String id;
    
    private Integer statTime;  // 统计时间数值（统计请求次数的时间窗口，“在多久内”统计请求次数）
    
    private Integer statTimeType;  // 统计时间类型
    
    private Integer threshold;  // 调用限制阈值

    private Integer effectiveTime;  // 限制时间数值（触发限流后，限制生效的时长）
    
    private Integer effectiveTimeType;  // 限制时间类型
    
    private String limitApi;  // 被限制的API路径列表
    
    private String message;  // 返回的限制消息
    
    private Integer status;  // 状态标识
}
