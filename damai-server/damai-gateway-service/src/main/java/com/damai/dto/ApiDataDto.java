package com.damai.dto;

import lombok.Data;

import java.util.Date;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: api调用记录 接受参数
 * @author: 阿星不是程序员
 **/
@Data
public class ApiDataDto {
    
    private Long id;
    
    private String headVersion;  // 请求头版本

    private String apiAddress;  // 	API请求IP地址
    
    private String apiMethod;  // API请求方法
    
    private String apiBody;  // API请求体
    
    private String apiParams;  // URL参数
    
    private String apiUrl;  // API请求URL
    
    private Date createTime;  // 创建时间
    
    private Integer status;  // 状态

    private String callDayTime;  // 调用日期时间维度

    private String callHourTime;  // 调用小时时间维度
    
    private String callMinuteTime;  // 调用分钟时间维度
    
    private String callSecondTime;  // 调用秒时间维度
    
    private Integer type;  // 调用限流规则类型
    
}
