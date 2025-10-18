package com.damai.vo;

import lombok.Data;

import java.util.Date;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 深度规则 返回vo
 * @author: 阿星不是程序员
 **/
@Data
public class DepthRuleVo {
    
    private String id;
    
    private String startTimeWindow;  // 限制生效的开始时间

    private long startTimeWindowTimestamp;  // 起始时间（时间戳）
    
    private String endTimeWindow;   // 限制生效的结束时间
    
    private long endTimeWindowTimestamp;  // 结束时间（时间戳）
    
    private Integer statTime;  // 统计时间数值
    
    private Integer statTimeType;  // 统计时间类型
    
    private Integer threshold;  // 调用限制阈值
    
    private Integer effectiveTime;  // 限制时间数值，超过阈值后要禁止多久
    
    private Integer effectiveTimeType;  // 限制时间类型
    
    private String limitApi;  // 被限制的API路径列表
    
    private String message;  // 限制提示信息
    
    private Integer status;  // 状态标识
    
    private Date createTime;
}
