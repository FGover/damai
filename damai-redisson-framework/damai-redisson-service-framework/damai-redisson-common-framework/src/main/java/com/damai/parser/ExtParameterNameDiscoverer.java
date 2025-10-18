package com.damai.parser;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.NativeDetector;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 对DefaultParameterNameDiscoverer进行扩展，添加{@link LocalVariableTableParameterNameDiscoverer}
 * 扩展Spring默认的参数名发现器，增加本地变量表参数名发现器的支持
 * 用于更精准地解析方法参数名（尤其在未开启调试信息时仍能获取参数名）
 * @author: 阿星不是程序员
 **/
public class ExtParameterNameDiscoverer extends DefaultParameterNameDiscoverer {

    /**
     * 构造方法：扩展默认参数名发现器的功能
     * 在父类{@link DefaultParameterNameDiscoverer}的基础上，添加{@link LocalVariableTableParameterNameDiscoverer}
     * 以支持通过字节码的本地变量表解析参数名，提高参数名获取的兼容性
     */
    public ExtParameterNameDiscoverer() {
        super();  // 调用父类构造方法，初始化默认的参数名发现器
        // 判断当前是否在原生镜像环境（如GraalVM Native Image）中运行
        // 若不是原生镜像环境，则添加本地变量表参数名发现器
        if (!NativeDetector.inNativeImage()) {
            // LocalVariableTableParameterNameDiscoverer：通过解析字节码中的本地变量表获取参数名
            // 即使编译时未保留调试信息，也可能通过字节码元数据获取参数名，增强兼容性
            addDiscoverer(new LocalVariableTableParameterNameDiscoverer());
        }
    }
}
