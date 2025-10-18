package com.damai.common;

import com.damai.enums.BaseCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 接口统一响应体
 * 定义所有API接口的标准返回格式，包含响应码、提示信息和业务数据，确保前后端交互的数据格式一致，便于前端统一处理响应结果
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title = "ApiResponse", description = "数据响应规范结构")
// 实现Serializable，支持序列化（如用于缓存、远程调用）
public class ApiResponse<T> implements Serializable {

    @Schema(name = "code", type = "Integer", description = "响应码 0:成功 其余:失败")
    private Integer code;

    @Schema(name = "message", type = "String", description = "提示信息：成功时可为空，失败时说明错误原因")
    private String message;

    @Schema(name = "data", description = "响应的具体业务数据：成功时返回实际数据，失败时可能为null或错误详情")
    private T data;

    /**
     * 私有构造方法：禁止外部直接创建实例，强制通过静态工厂方法（如ok()、error()）创建
     */
    private ApiResponse() {
    }

    /**
     * 创建失败响应（自定义响应码和消息）
     *
     * @param code    失败响应码（非0）
     * @param message 错误提示消息
     * @param <T>     数据类型
     * @return 失败响应体实例
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = code;
        apiResponse.message = message;
        return apiResponse;
    }

    /**
     * 创建失败响应（默认响应码-100，自定义消息）
     *
     * @param message 错误提示消息
     * @param <T>     数据类型
     * @return 失败响应体实例
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.message = message;
        return apiResponse;
    }

    /**
     * 创建失败响应（默认响应码-100，携带错误数据）
     *
     * @param data 错误相关数据（如参数校验失败的字段详情）
     * @param <T>  数据类型
     * @return 失败响应体实例
     */
    public static <T> ApiResponse<T> error(Integer code, T data) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.data = data;
        return apiResponse;
    }

    /**
     * 创建失败响应（基于BaseCode枚举，统一管理响应码和消息）
     *
     * @param baseCode 包含响应码和消息的枚举（如SystemErrorEnum.SYSTEM_BUSY）
     * @param <T>      数据类型
     * @return 失败响应体实例
     */
    public static <T> ApiResponse<T> error(BaseCode baseCode) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = baseCode.getCode();
        apiResponse.message = baseCode.getMsg();
        return apiResponse;
    }

    /**
     * 创建失败响应（基于BaseCode枚举，同时携带业务数据）
     *
     * @param baseCode 包含响应码和消息的枚举
     * @param data     错误相关的业务数据
     * @param <T>      数据类型
     * @return 失败响应体实例
     */
    public static <T> ApiResponse<T> error(BaseCode baseCode, T data) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = baseCode.getCode();
        apiResponse.message = baseCode.getMsg();
        apiResponse.data = data;
        return apiResponse;
    }

    /**
     * 创建默认失败响应（响应码-100，默认系统错误消息）
     *
     * @param <T> 数据类型
     * @return 失败响应体实例
     */
    public static <T> ApiResponse<T> error() {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = -100;
        apiResponse.message = "系统错误，请稍后重试!";
        return apiResponse;
    }

    /**
     * 创建成功响应（无业务数据）
     *
     * @param <T> 数据类型
     * @return 成功响应体实例（code=0）
     */
    public static <T> ApiResponse<T> ok() {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = 0;
        return apiResponse;
    }

    /**
     * 创建成功响应（携带业务数据）
     *
     * @param t   具体业务数据（如查询到的用户信息、订单列表等）
     * @param <T> 数据类型
     * @return 成功响应体实例（code=0，data为业务数据）
     */
    public static <T> ApiResponse<T> ok(T t) {
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.code = 0;
        apiResponse.setData(t);
        return apiResponse;
    }
}
