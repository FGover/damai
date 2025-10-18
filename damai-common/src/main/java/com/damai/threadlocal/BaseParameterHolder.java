package com.damai.threadlocal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 线程绑定工具
 * @author: 阿星不是程序员
 **/
public class BaseParameterHolder {

    // 声明一个 ThreadLocal，里面放 Map<String, String>，用于存储当前线程的参数
    // ThreadLocal确保每个线程都有独立的Map实例，实现线程间数据隔离
    private static final ThreadLocal<Map<String, String>> THREAD_LOCAL_MAP = new ThreadLocal<>();

    /**
     * 向当前线程绑定的 Map 中放入一个参数
     *
     * @param name
     * @param value
     */
    public static void setParameter(String name, String value) {
        Map<String, String> map = getParameterMap();
        map.put(name, value);
        THREAD_LOCAL_MAP.set(map);
    }

    /**
     * 从当前线程绑定的 Map 中获取一个参数
     *
     * @param name
     * @return
     */
    public static String getParameter(String name) {
        return Optional.ofNullable(THREAD_LOCAL_MAP.get()).map(map -> map.get(name)).orElse(null);
    }

    /**
     * 从当前线程绑定的 Map 中移除一个参数
     *
     * @param name
     */
    public static void removeParameter(String name) {
        Map<String, String> map = THREAD_LOCAL_MAP.get();
        if (map != null) {
            map.remove(name);
        }
    }

    /**
     * 获取底层的ThreadLocal实例
     *
     * @return
     */
    public static ThreadLocal<Map<String, String>> getThreadLocal() {
        return THREAD_LOCAL_MAP;
    }

    /**
     * 获取当前线程绑定的 Map
     *
     * @return
     */
    public static Map<String, String> getParameterMap() {
        // 获取当前线程的映射表
        Map<String, String> map = THREAD_LOCAL_MAP.get();
        // 若映射表为null，创建一个初始容量为64的HashMap（根据业务场景优化的初始容量）
        if (map == null) {
            map = new HashMap<>(64);
        }
        return map;
    }

    /**
     * 直接设置当前线程绑定的 Map
     * 用于批量设置参数或恢复上下文场景
     *
     * @param map 要设置的 Map
     */
    public static void setParameterMap(Map<String, String> map) {
        THREAD_LOCAL_MAP.set(map);
    }

    /**
     * 移除当前线程绑定的 Map（用完一定要调用，防止内存泄漏）
     */
    public static void removeParameterMap() {
        THREAD_LOCAL_MAP.remove();
    }
}
