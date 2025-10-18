package com.damai.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.core.RedisKeyManage;
import com.damai.util.StringUtil;
import com.damai.dto.ApiDataDto;
import com.damai.enums.ApiRuleType;
import com.damai.enums.BaseCode;
import com.damai.enums.RuleTimeUnit;
import com.damai.exception.DaMaiFrameException;
import com.damai.kafka.ApiDataMessageSend;
import com.damai.property.GatewayProperty;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.lua.ApiRestrictCacheOperate;
import com.damai.util.DateUtils;
import com.damai.vo.DepthRuleVo;
import com.damai.vo.RuleVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 接口请求记录。接口限流服务类，负责接口请求的限流规则校验、限流触发判断、限流日志记录等核心逻辑
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ApiRestrictService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private GatewayProperty gatewayProperty; // 网关配置属性（包含需要限流的路径等配置）

    @Autowired(required = false)
    private ApiDataMessageSend apiDataMessageSend;  // Kafka消息发送器（用于记录限流日志）

    @Autowired
    private ApiRestrictCacheOperate apiRestrictCacheOperate;  // 限流缓存操作工具（封装Lua脚本调用）

    @Autowired
    private UidGenerator uidGenerator;  // 分布式ID生成器（用于生成限流日志的唯一ID）

    /**
     * 校验当前URI是否匹配需要进行限流的路径
     *
     * @param requestUri
     * @return
     */
    public boolean checkApiRestrict(String requestUri) {
        // 从网关配置中获取需要限流的路径数组
        if (gatewayProperty.getApiRestrictPaths() != null) {
            // 遍历所有需要限流的路径规则
            for (String apiRestrictPath : gatewayProperty.getApiRestrictPaths()) {
                PathMatcher matcher = new AntPathMatcher();
                if (matcher.match(apiRestrictPath, requestUri)) {
                    return true;  // 匹配成功，需要限流
                }
            }
        }
        return false;  // 未匹配任何限流路径，无需限流
    }

    /**
     * 检查接口是否触发限流规则，若触发则抛异常
     *
     * @param id      用户ID（可选，用于精细化限流，如区分不同用户的请求）
     * @param url     请求的接口路径（用于匹配限流规则）
     * @param request 服务请求对象（用于获取请求IP等信息）
     */
    public void apiRestrict(String id, String url, ServerHttpRequest request) {
        // 先判断当前接口是否需要限流
        if (checkApiRestrict(url)) {
            long triggerResult = 0L;   // 限流触发结果：1 - 触发限流；0 - 未触发
            long triggerCallStat = 0L;  // 触发的限流类型：普通规则/深度规则
            long apiCount;   // 当前时间窗口内的请求次数
            long threshold;   // 限流阈值（允许的最大请求次数）
            long messageIndex;  // 限流提示消息的索引（用于深度规则）
            String message = "";  // 限流提示消息
            // 获取请求的真实ip地址（处理代理场景）
            String ip = getIpAddress(request);
            // 构建限流唯一标识Key：IP + 可选用户ID + URL
            // 确保同一IP、同一用户、同一接口的请求被归为同一类进行计数
            StringBuilder stringBuilder = new StringBuilder(ip);
            if (StringUtil.isNotEmpty(id)) {
                stringBuilder.append("_").append(id);  // 若有用户ID，拼接用户ID
            }
            String commonKey = stringBuilder.append("_").append(url).toString();
            try {
                // 存储深度限流规则的列表（支持时间窗口的精细化限流）
                List<DepthRuleVo> depthRuleVoList = new ArrayList<>();
                // 从redis获取普通限流规则（全局哈希表中存储）
                RuleVo ruleVo = redisCache.getForHash(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.ALL_RULE_HASH),  // 全局规则哈希表Key
                        RedisKeyBuild.createRedisKey(RedisKeyManage.RULE).getRelKey(),  // 普通规则在哈希表中的字段
                        RuleVo.class    // 规则对象类型
                );
                // 从redis获取深度限流规则
                String depthRuleStr = redisCache.getForHash(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.ALL_RULE_HASH), // 全局规则哈希表Key
                        RedisKeyBuild.createRedisKey(RedisKeyManage.DEPTH_RULE).getRelKey(),  // 深度规则在哈希表中的字段
                        String.class);   // 结果类型为字符串
                // 如果深度规则存在，解析为 DepthRuleVo 列表
                if (StringUtil.isNotEmpty(depthRuleStr)) {
                    depthRuleVoList = JSON.parseArray(depthRuleStr, DepthRuleVo.class);
                }
                // 确定当前生效的限流规则类型
                int apiRuleType = ApiRuleType.NO_RULE.getCode();  // 默认：无规则
                if (Optional.ofNullable(ruleVo).isPresent()) {
                    // 存在普通规则
                    apiRuleType = ApiRuleType.RULE.getCode();
                    // 普通规则的提示消息
                    message = ruleVo.getMessage();
                }
                // 同时存在普通规则和深度规则（优先深度规则）
                if (Optional.ofNullable(ruleVo).isPresent() && CollectionUtil.isNotEmpty(depthRuleVoList)) {
                    apiRuleType = ApiRuleType.DEPTH_RULE.getCode();
                }
                // 若存在有效的限流规则，执行限流检查
                if (apiRuleType == ApiRuleType.RULE.getCode() || apiRuleType == ApiRuleType.DEPTH_RULE.getCode()) {
                    assert ruleVo != null;  // 确保规则对象非空（因apiRuleType已判断）
                    // 构建普通规则的参数（如限流Key、时间窗口、阈值等）
                    JSONObject parameter = getRuleParameter(apiRuleType, commonKey, ruleVo);
                    // 若存在深度规则，补充构建深度规则的参数
                    if (apiRuleType == ApiRuleType.DEPTH_RULE.getCode()) {
                        parameter = getDepthRuleParameter(parameter, commonKey, depthRuleVoList);
                    }
                    // 调用Lua脚本执行限流检查（保证原子性，避免并发计数错误）
                    ApiRestrictData apiRestrictData = apiRestrictCacheOperate
                            .apiRuleOperate(Collections.singletonList(JSON.toJSONString(parameter)), new Object[]{});
                    // 解析Lua脚本返回的结果
                    triggerResult = apiRestrictData.getTriggerResult();
                    triggerCallStat = apiRestrictData.getTriggerCallStat();
                    apiCount = apiRestrictData.getApiCount();
                    threshold = apiRestrictData.getThreshold();
                    messageIndex = apiRestrictData.getMessageIndex();
                    // 若存在深度规则的消息索引，优先使用深度规则的提示消息
                    if (messageIndex != -1) {
                        message = Optional.ofNullable(depthRuleVoList.get((int) messageIndex))
                                .map(DepthRuleVo::getMessage)  // 从深度规则中获取消息
                                .filter(StringUtil::isNotEmpty) // 确保消息非空
                                .orElse(message);  // 否则使用默认消息
                    }
                    // 打印限流检查日志（便于调试和监控）
                    log.info("api rule [key : {}], [triggerResult : {}], [triggerCallStat : {}], [apiCount : {}], " +
                            "[threshold : {}]", commonKey, triggerResult, triggerCallStat, apiCount, threshold);
                }
            } catch (Exception e) {
                log.error("redis Lua eror", e);  // 捕获Lua脚本执行异常
            }
            // 若触发限流规则，记录限流日志并抛出异常
            if (triggerResult == 1) {
                // 若触发的是普通规则或深度规则，保存限流记录到消息队列
                if (triggerCallStat == ApiRuleType.RULE.getCode() || triggerCallStat == ApiRuleType.DEPTH_RULE.getCode()) {
                    saveApiData(request, url, (int) triggerCallStat);
                }
                // 构建限流提示消息（优先使用规则中定义的消息，否则使用默认消息）
                String defaultMessage = BaseCode.API_RULE_TRIGGER.getMsg();
                if (StringUtil.isNotEmpty(message)) {
                    defaultMessage = message;
                }
                // 抛出限流异常
                throw new DaMaiFrameException(BaseCode.API_RULE_TRIGGER.getCode(), defaultMessage);
            }
        }
    }

    /**
     * 构建普通限流规则的参数JSON对象（用于Lua脚本执行）
     *
     * @param apiRuleType 限流规则类型（普通规则/深度规则）
     * @param commonKey   限流唯一标识Key（IP+用户ID+接口路径）
     * @param ruleVo      普通限流规则对象（包含时间窗口、阈值等配置）
     * @return
     */
    public JSONObject getRuleParameter(int apiRuleType, String commonKey, RuleVo ruleVo) {
        JSONObject parameter = new JSONObject();
        parameter.put("apiRuleType", apiRuleType);   // 规则类型
        parameter.put("ruleKey", "rule_api_limit" + "_" + commonKey); // 限流计数的Redis键名
        // 时间窗口（转换为秒：若规则定义为分钟，则乘以60）
        parameter.put("statTime", String.valueOf(
                Objects.equals(ruleVo.getStatTimeType(), RuleTimeUnit.SECOND.getCode())
                        ? ruleVo.getStatTime()
                        : ruleVo.getStatTime() * 60)
        );
        parameter.put("threshold", ruleVo.getThreshold());  // 限流阈值（允许的最大请求次数）
        // 规则生效时间（转换为秒：若规则定义为分钟，则乘以60）
        parameter.put("effectiveTime", String.valueOf(
                Objects.equals(ruleVo.getEffectiveTimeType(), RuleTimeUnit.SECOND.getCode())
                        ? ruleVo.getEffectiveTime()
                        : ruleVo.getEffectiveTime() * 60)
        );
        // 限流规则的Redis键（用于存储限流状态）
        parameter.put("ruleLimitKey", RedisKeyBuild.createRedisKey(RedisKeyManage.RULE_LIMIT, commonKey).getRelKey());
        // 限流统计的有序集合键（用于时间窗口内的请求计数）
        parameter.put("zSetRuleStatKey", RedisKeyBuild.createRedisKey(RedisKeyManage.Z_SET_RULE_STAT, commonKey).getRelKey());
        return parameter;
    }

    /**
     * 构建深度限流规则的参数JSON对象
     *
     * @param parameter       普通规则的参数JSON对象
     * @param commonKey       限流唯一标识Key
     * @param depthRuleVoList 深度限流规则列表
     * @return
     */
    public JSONObject getDepthRuleParameter(JSONObject parameter, String commonKey, List<DepthRuleVo> depthRuleVoList) {
        // 对深度规则按开始时间（StartTime）窗口排序（确保按时间顺序校验）
        depthRuleVoList = sortStartTimeWindow(depthRuleVoList);
        // 深度规则数量
        parameter.put("depthRuleSize", String.valueOf(depthRuleVoList.size()));
        // 当前时间戳（用于判断是否在规则生效窗口内）
        parameter.put("currentTime", System.currentTimeMillis());
        // 构建每个深度规则的参数
        List<JSONObject> depthRules = new ArrayList<>();
        for (int i = 0; i < depthRuleVoList.size(); i++) {
            JSONObject depthRule = new JSONObject();
            DepthRuleVo depthRuleVo = depthRuleVoList.get(i);
            // 时间窗口（转换为秒：若规则定义为分钟，则乘以60）
            depthRule.put("statTime", Objects.equals(
                    depthRuleVo.getStatTimeType(), RuleTimeUnit.SECOND.getCode())
                    ? depthRuleVo.getStatTime()
                    : depthRuleVo.getStatTime() * 60
            );
            // 深度规则的限流阈值
            depthRule.put("threshold", depthRuleVo.getThreshold());
            // 规则生效时间（转换为秒：若规则定义为分钟，则乘以60）
            depthRule.put("effectiveTime", String.valueOf(
                    Objects.equals(depthRuleVo.getEffectiveTimeType(), RuleTimeUnit.SECOND.getCode())
                            ? depthRuleVo.getEffectiveTime()
                            : depthRuleVo.getEffectiveTime() * 60)
            );
            // 深度规则的redis键（包含索引，区分不同的深度规则）
            depthRule.put("depthRuleLimit", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.DEPTH_RULE_LIMIT, i, commonKey).getRelKey());
            // 规则生效的开始/结束时间戳（当天的时间窗口，如09:00-18:00）
            depthRule.put("startTimeWindowTimestamp", depthRuleVo.getStartTimeWindowTimestamp());
            depthRule.put("endTimeWindowTimestamp", depthRuleVo.getEndTimeWindowTimestamp());
            depthRules.add(depthRule);
        }
        // 将所有深度规则参数加入JSON
        parameter.put("depthRules", depthRules);
        return parameter;
    }

    /**
     * 对深度限流规则按开始时间窗口的时间戳排序（确保按时间顺序校验规则）
     *
     * @param depthRuleVoList 未排序的深度规则列表
     * @return 按开始时间戳升序排序的深度规则列表
     */
    public List<DepthRuleVo> sortStartTimeWindow(List<DepthRuleVo> depthRuleVoList) {
        return depthRuleVoList.stream()
                .peek(depthRuleVo -> {
                    // 计算每个规则的开始/结束时间窗口的时间戳（基于当天日期）
                    depthRuleVo.setStartTimeWindowTimestamp(getTimeWindowTimestamp(depthRuleVo.getStartTimeWindow()));
                    depthRuleVo.setEndTimeWindowTimestamp((getTimeWindowTimestamp(depthRuleVo.getEndTimeWindow())));
                })
                .sorted(Comparator.comparing(DepthRuleVo::getStartTimeWindowTimestamp))  // 按开始时间戳升序排序
                .collect(Collectors.toList());
    }

    /**
     * 将时间窗口字符串（如“09:00”）转换为当天对应的时间戳（毫秒）
     *
     * @param timeWindow 时间窗口字符串（格式：HH:mm）
     * @return 对应的时间戳
     */
    public long getTimeWindowTimestamp(String timeWindow) {
        // 获取当天日期
        String today = DateUtil.today();
        // 解析为当天该时间的时间戳
        return DateUtil.parse(today + " " + timeWindow).getTime();
    }

    /**
     * 获取请求的真实ip地址（处理多代理场景，如 X-Forwarded-For等头信息）
     *
     * @param request 服务请求对象
     * @return 真实的客户端IP地址
     */
    public static String getIpAddress(ServerHttpRequest request) {
        String unknown = "unknown";  // 代理头中可能的未知标识
        String split = ",";  // 多IP分隔符（如X-Forwarded-For可能包含多个IP）
        HttpHeaders headers = request.getHeaders();
        // 依次从常见的代理头中获取ip，优先取第一个非unknown的IP
        String ip = headers.getFirst("x-forwarded-for");
        if (ip != null && !ip.isEmpty() && !unknown.equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个ip值，第一个ip才是真实ip
            if (ip.contains(split)) {
                ip = ip.split(split)[0];
            }
        }
        if (ip == null || ip.isEmpty() || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("X-Real-IP");
        }
        // 如果所有代理头都无法获取ip，则使用请求的远程地址
        if (ip == null || ip.isEmpty() || unknown.equalsIgnoreCase(ip)) {
            // getRemoteAddress 可能返回 null，所以需要做非空校验
            ip = Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
        }
        return ip;
    }

    /**
     * 保存触发限流的接口请求数据到消息队列（用于监控和分析）
     *
     * @param request 触发限流的请求对象
     * @param apiUrl  接口路径
     * @param type    限流类型（普通规则/深度规则）
     */
    public void saveApiData(ServerHttpRequest request, String apiUrl, Integer type) {
        ApiDataDto apiDataDto = new ApiDataDto();
        apiDataDto.setId(uidGenerator.getUid());  // 生成唯一ID标识该条日志
        apiDataDto.setApiAddress(getIpAddress(request));  // 请求IP
        apiDataDto.setApiUrl(apiUrl);  // 接口路径
        apiDataDto.setCreateTime(DateUtils.now());  // 创建时间（毫秒级时间戳）
        // 记录请求的年月日时分秒（用于按时间维度统计）
        apiDataDto.setCallDayTime(DateUtils.nowStr(DateUtils.FORMAT_DATE));
        apiDataDto.setCallHourTime(DateUtils.nowStr(DateUtils.FORMAT_HOUR));
        apiDataDto.setCallMinuteTime(DateUtils.nowStr(DateUtils.FORMAT_MINUTE));
        apiDataDto.setCallSecondTime(DateUtils.nowStr(DateUtils.FORMAT_SECOND));
        apiDataDto.setType(type);  // 限流类型
        // 若消息发送器存在，将限流日志转为JSON发送到Kafka（避免空指针）
        Optional.ofNullable(apiDataMessageSend).ifPresent(send -> send.sendMessage(JSON.toJSONString(apiDataDto)));
    }
}
