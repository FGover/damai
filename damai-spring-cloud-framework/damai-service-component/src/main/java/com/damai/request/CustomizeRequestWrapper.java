package com.damai.request;

import com.damai.util.StringUtil;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: request包装
 * @author: 阿星不是程序员
 **/
@Getter
public class CustomizeRequestWrapper extends HttpServletRequestWrapper {

    // 存储读取到的请求体内容（字符串形式）
    private final String requestBody;

    /**
     * 构造方法：初始化时读取并保存原始请求的请求体
     *
     * @param request 原始的HttpServletRequest对象
     * @throws IOException 读取请求流时发生IO异常
     */
    public CustomizeRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // 通过工具类将请求输入流转换为字符串，保存到requestBody中
        requestBody = StringUtil.inputStreamConvertString(request.getInputStream());
    }

    /**
     * 重写getInputStream方法，返回基于保存的请求体内容的输入流
     * 解决原始请求流只能读取一次的问题，支持多次获取输入流
     *
     * @return ServletInputStream 包含请求体内容的输入流
     */
    @Override
    public ServletInputStream getInputStream() {
        // 将保存的请求体字符串转换为字节数组输入流
        ByteArrayInputStream byteArrayInputStream =
                new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8));
        // 返回自定义的ServletInputStream实现
        return new ServletInputStream() {
            @Override
            public int read() {
                // 读取字节数组输入流中的数据
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                // 标识输入流是否已读取完毕（此处简化实现为false）
                return false;
            }

            @Override
            public boolean isReady() {
                // 标识输入流是否已准备好读取（此处简化实现为true）
                return false;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // 异步读取相关的监听器（此处无需实现）
            }
        };
    }

    /**
     * 重写getReader方法，返回基于保存的请求体内容的BufferedReader
     * 支持通过字符流方式多次读取请求体
     *
     * @return BufferedReader 字符流读取器
     */
    @Override
    public BufferedReader getReader() {
        // 基于重写的getInputStream创建字符读取器
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

}
