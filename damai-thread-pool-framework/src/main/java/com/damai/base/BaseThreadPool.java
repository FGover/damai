package com.damai.base;

import com.damai.threadlocal.BaseParameterHolder;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 线程池基类
 * @author: 阿星不是程序员
 **/
public class BaseThreadPool {

    /**
     * 获取当前线程的MDC日志上下文
     * MDC（Mapped Diagnostic Context）用于存储日志相关的上下文信息（如traceId、userId）
     * 便于在多线程环境下追踪日志链路
     *
     * @return 包含MDC上下文信息的Map，为当前线程上下文的副本
     */
    protected static Map<String, String> getContextForTask() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 获取当前线程的业务参数上下文
     * 从BaseParameterHolder中获取业务相关的参数映射（如请求参数、用户信息等）
     *
     * @return 包含业务参数的Map
     */
    protected static Map<String, String> getContextForHold() {
        return BaseParameterHolder.getParameterMap();
    }

    /**
     * 包装Runnable任务，实现父线程上下文向子线程的传递与恢复
     * 确保子线程执行时能获取到正确的日志上下文和业务参数
     *
     * @param runnable          原始任务
     * @param parentMdcContext  父线程的MDC上下文
     * @param parentHoldContext 父线程的业务参数上下文
     * @return 包装后的Runnable任务
     */
    protected static Runnable wrapTask(final Runnable runnable, final Map<String, String> parentMdcContext,
                                       final Map<String, String> parentHoldContext) {
        return () -> {
            // 保存子线程原有上下文，并设置父线程上下文
            Map<String, Map<String, String>> preprocess = preprocess(parentMdcContext, parentHoldContext);
            Map<String, String> holdContext = preprocess.get("holdContext");  // 子线程原有业务上下文
            Map<String, String> mdcContext = preprocess.get("mdcContext");  // 子线程原有MDC上下文
            try {
                // 执行原始任务（此时已使用父线程上下文）
                runnable.run();
            } finally {
                // 任务执行完毕后，恢复子线程原有上下文
                postProcess(mdcContext, holdContext);
            }
        };
    }

    /**
     * 包装Callable任务，功能与wrapTask（Runnable）一致，适用于有返回值的任务
     *
     * @param task              原始Callable任务
     * @param parentMdcContext  父线程的MDC上下文
     * @param parentHoldContext 父线程的业务参数上下文
     * @param <T>               任务返回值类型
     * @return 包装后的Callable任务
     */
    protected static <T> Callable<T> wrapTask(Callable<T> task, final Map<String, String> parentMdcContext, final Map<String, String> parentHoldContext) {
        return () -> {
            // 保存子线程原有上下文，并设置父线程上下文
            Map<String, Map<String, String>> preprocess = preprocess(parentMdcContext, parentHoldContext);
            Map<String, String> holdContext = preprocess.get("holdContext");
            Map<String, String> mdcContext = preprocess.get("mdcContext");
            try {
                // 执行原始任务并返回结果
                return task.call();
            } finally {
                // 恢复子线程原有上下文
                postProcess(mdcContext, holdContext);
            }
        };
    }

    /**
     * 预处理方法
     * 保存子线程当前上下文，并将父线程上下文设置到当前线程
     * 实现上下文的"继承"，确保任务在正确的上下文环境中执行
     *
     * @param parentMdcContext  父线程的MDC上下文
     * @param parentHoldContext 父线程的业务参数上下文
     * @return 包含子线程原有上下文的Map（MDC上下文和业务上下文）
     */
    private static Map<String, Map<String, String>> preprocess(final Map<String, String> parentMdcContext,
                                                               final Map<String, String> parentHoldContext) {
        // 存储子线程原有上下文的容器
        Map<String, Map<String, String>> resultMap = new HashMap<>(8);
        // 保存子线程当前的业务参数上下文
        Map<String, String> holdContext = BaseParameterHolder.getParameterMap();
        // 保存子线程当前的MDC上下文
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        // 设置父线程MDC上下文到当前线程
        if (parentMdcContext == null) {
            // 父线程无MDC上下文时，清空当前线程MDC
            MDC.clear();
        } else {
            // 使用父线程的MDC上下文
            MDC.setContextMap(parentMdcContext);
        }
        // 设置父线程的业务参数上下文到当前线程
        if (parentHoldContext == null) {
            // 父线程无业务上下文时，清除当前线程业务参数
            BaseParameterHolder.removeParameterMap();
        } else {
            // 使用父线程的业务上下文
            BaseParameterHolder.setParameterMap(parentHoldContext);
        }
        // 存储子线程原有上下文
        resultMap.put("holdContext", holdContext);
        resultMap.put("mdcContext", mdcContext);
        return resultMap;
    }

    /**
     * 后处理方法
     * 任务执行完毕后，恢复子线程原有上下文，避免线程池复用线程导致的上下文污染问题
     *
     * @param mdcContext  子线程原有MDC上下文
     * @param holdContext 子线程原有业务参数上下文
     */
    private static void postProcess(Map<String, String> mdcContext, Map<String, String> holdContext) {
        // 恢复子线程原有MDC上下文
        if (mdcContext == null) {
            // 原有MDC上下文为空时，清空当前MDC
            MDC.clear();
        } else {
            // 恢复为子线程原有MDC上下文
            MDC.setContextMap(mdcContext);
        }
        // 恢复业务参数上下文
        if (holdContext == null) {
            // 原有业务上下文为null时，清除参数
            BaseParameterHolder.removeParameterMap();
        } else {
            // 恢复为子线程原有业务上下文
            BaseParameterHolder.setParameterMap(holdContext);
        }
    }
}
