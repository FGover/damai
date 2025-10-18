package com.damai.property;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 网关配置属性类：用于封装网关服务相关的配置信息，通过注解与配置文件关联
 * @author: 阿星不是程序员
 **/
@Data
@Component
public class GatewayProperty {
    /**
     * 需要进行频率限制的API路径数组
     * 从配置文件中读取api.limit.paths配置，默认值为null
     */
    @Value("${api.limit.paths:#{null}}")
    private String[] apiRestrictPaths;

    /**
     * 需要跳过token校验的路径数组
     * 从配置文件读取skip.check.token.paths配置，包含各类订单操作、用户信息修改等接口
     * 默认值包含多个无需令牌校验的接口路径，如订单创建、用户认证、订单管理等相关接口
     */
    @Value("${skip.check.token.paths:/**/program/order/create/v1,/**/program/order/create/v2,/**/program/order/create/v3," +
            "/**/program/order/create/v4,/**/ticket/user/add,/**/ticket/user/delete,/**/ticket/user/list,/**/user/authentication," +
            "/**/user/update,/**/user/update/email,/**/user/update/mobile,/**/user/update/password," +
            "/**/order/cancel,/**/order/create,/**/order/pay,/**/order/select/list,/**/order/get,/**/order/cancel}")
    private String[] checkTokenPaths;

    /**
     * 需要跳过参数校验的路径数组
     * 从配置文件读取skip.check.parmeter.paths配置
     * 默认值为支付宝回调通知接口，这类接口通常由第三方系统调用，参数格式固定无需额外校验
     */
    @Value("${skip.check.parmeter.paths:/**/alipay/notify}")
    private String[] checkSkipParmeterPaths;

    /**
     * 是否允许正常访问的开关
     * 从配置文件读取allow.normal.access配置，默认值为true（允许访问）
     * 可用于系统维护时关闭正常访问，切换到降级或维护页面
     */
    @Value("${allow.normal.access:true}")
    private boolean allowNormalAccess;

    /**
     * 需要获取用户id的路径数组
     * 从配置文件读取userId.paths配置，默认值为各类节目详情接口
     * 这些接口可能需要根据用户ID返回个性化内容或进行权限判断
     */
    @Value("${userId.paths:/**/program/detail,/**/program/detail/v1,/**/program/detail/v2}")
    private String[] userIdPaths;
}
