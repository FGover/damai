package com.damai.lockinfo;


import com.damai.core.SpringUtil;
import com.damai.parser.ExtParameterNameDiscoverer;
import com.damai.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.damai.core.Constants.SEPARATOR;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 锁信息处理器抽象类（模板方法模式）
 * 实现LockInfoHandle接口，提供生成锁名称的通用逻辑，子类只需实现getLockPrefixName()方法定义具体业务的锁前缀，即可完成锁信息生成
 * @author: 阿星不是程序员
 **/
@Slf4j
public abstract class AbstractLockInfoHandle implements LockInfoHandle {

    // 分布式锁id的基础前缀（用于simpleGetLockName方法）
    private static final String LOCK_DISTRIBUTE_ID_NAME_PREFIX = "LOCK_DISTRIBUTE_ID";
    // 参数名发现器，用于解析方法参数名（支持复杂参数类型）
    private final ParameterNameDiscoverer nameDiscoverer = new ExtParameterNameDiscoverer();
    // SpEL表达式解析器，用于解析keys中的表达式（如"#apiData.id"）
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 获取当前业务场景的锁前缀（抽象方法，由子类实现）
     * 用于区分不同业务场景的锁（如防重复执行的前缀为"REPEAT_EXECUTE_LIMIT"）
     *
     * @return 具体业务的锁前缀字符串
     */
    protected abstract String getLockPrefixName();

    /**
     * 生成完整的锁名称
     * 结合环境前缀、业务前缀、业务名称和解析后的键，生成分布式锁的唯一标识
     *
     * @param joinPoint 切面
     * @param name      锁业务名
     * @param keys      键数组
     * @return 完整锁名称（格式：环境前缀-业务前缀-业务名-解析后的键）
     */
    @Override
    public String getLockName(JoinPoint joinPoint, String name, String[] keys) {
        // 拼接逻辑：环境前缀（如"dev-"） + 业务前缀（如"REPEAT_EXECUTE_LIMIT"） + 分隔符 + 业务名 + 解析后的键
        return SpringUtil.getPrefixDistinctionName() + "-" + getLockPrefixName() + SEPARATOR + name +
                getRelKey(joinPoint, keys);
    }

    /**
     * 简单拼装锁名称（不解析SpEL表达式，直接使用keys字符串）
     * 用于无需动态参数的场景，快速生成锁标识
     *
     * @param name 锁业务名
     * @param keys 键数组
     * @return 简单锁名称（格式：环境前缀-基础锁前缀-业务名-键数组）
     */
    @Override
    public String simpleGetLockName(String name, String[] keys) {
        List<String> definitionKeyList = new ArrayList<>();
        for (String key : keys) {
            if (StringUtil.isNotEmpty(key)) {
                definitionKeyList.add(key);
            }
        }
        // 拼接逻辑：环境前缀 + 基础锁前缀 + 分隔符 + 业务名 + 分隔符 + 键数组拼接
        return SpringUtil.getPrefixDistinctionName() + "-" + LOCK_DISTRIBUTE_ID_NAME_PREFIX + SEPARATOR + name +
                SEPARATOR + String.join(SEPARATOR, definitionKeyList);
    }

    /**
     * 解析keys中的SpEL表达式，生成具体的业务键
     *
     * @param joinPoint 切面连接点，包含方法参数
     * @param keys      键数组（可能包含SpEL表达式）
     * @return 解析后的键字符串（格式：分隔符+键1+分隔符+键2...）
     */
    private String getRelKey(JoinPoint joinPoint, String[] keys) {
        // 获取被拦截的方法对象
        Method method = getMethod(joinPoint);
        // 解析SpEL表达式，获取实际的键值（如将"#apiData.id"解析为具体的ID值）
        List<String> definitionKeys = getSpElKey(keys, method, joinPoint.getArgs());
        // 拼接解析后的键，用分隔符连接
        return SEPARATOR + String.join(SEPARATOR, definitionKeys);
    }

    /**
     * 从切面连接点中获取被拦截的方法对象（处理接口方法的情况）
     *
     * @param joinPoint 切面连接点
     * @return 被拦截的实际方法对象
     */
    private Method getMethod(JoinPoint joinPoint) {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 获取方法对象
        Method method = signature.getMethod();
        // 如果方法是接口中的方法，则获取目标对象的实际实现方法
        if (method.getDeclaringClass().isInterface()) {
            try {
                method = joinPoint.getTarget()
                        .getClass()
                        .getDeclaredMethod(signature.getName(), method.getParameterTypes());
            } catch (Exception e) {
                log.error("get method error ", e);
            }
        }
        return method;
    }

    /**
     * 解析SpEL表达式，获取实际的键值
     *
     * @param definitionKeys  包含SpEL表达式的键数组
     * @param method          被拦截的方法
     * @param parameterValues 方法的实际参数值
     * @return 解析后的键值列表
     */
    private List<String> getSpElKey(String[] definitionKeys, Method method, Object[] parameterValues) {
        List<String> definitionKeyList = new ArrayList<>();
        for (String definitionKey : definitionKeys) {
            if (!ObjectUtils.isEmpty(definitionKey)) {
                // 创建SpEL表达式的上下文（包含方法参数信息）
                EvaluationContext context = new MethodBasedEvaluationContext(
                        null, method, parameterValues, nameDiscoverer);
                // 解析SpEL表达式，获取实际的键值（如"#apiData.id" -> 123）
                Object objKey = parser.parseExpression(definitionKey).getValue(context);
                // 将解析结果转为字符串，添加到列表（null安全处理）
                definitionKeyList.add(ObjectUtils.nullSafeToString(objKey));
            }
        }
        return definitionKeyList;
    }

}
