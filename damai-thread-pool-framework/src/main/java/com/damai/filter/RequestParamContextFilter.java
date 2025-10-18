package com.damai.filter;


import com.damai.util.StringUtil;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import static com.damai.constant.Constant.TRACE_ID;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 链路过滤器
 * @author: 阿星不是程序员
 **/

public class RequestParamContextFilter extends OncePerRequestFilter {

    /**
     * 核心过滤方法：处理每个HTTP请求的链路追踪ID
     * 确保在请求处理过程中，所有日志都包含相同的TRACE_ID，实现跨线程/跨服务日志链路关联
     *
     * @param request     HTTP请求对象，用于获取TRACE_ID头信息
     * @param response    HTTP响应对象
     * @param filterChain 过滤器链，用于传递请求到下一个过滤器或处理器
     * @throws ServletException 处理请求时发生的Servlet异常
     * @throws IOException      处理请求时发生的IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 从请求头中获取链路追踪唯一标识
        // TRACE_ID通常由网关或上游服务生成，随请求传递到下游服务
        String traceId = request.getHeader(TRACE_ID);
        // 如果请求头中存在标识，则将其放入MDC上下文
        if (StringUtil.isNotEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            // 继续执行过滤器链，将请求传递给后续处理
            // 此时后续所有日志输出都会自动包含MDC中的traceId
            filterChain.doFilter(request, response);
        } finally {
            // 无论请求处理成功与否，最终都要从MDC中移除TRACE_ID
            // 避免线程复用（如Tomcat线程池）导致的上下文污染
            MDC.remove(TRACE_ID);
        }
    }
}
