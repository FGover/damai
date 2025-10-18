-- 接口限流核心逻辑Lua脚本
-- 功能：功能：实现原子性的限流判断，支持普通限流规则和深度限流规则，确保分布式环境下计数准确

-- 初始化变量：是否触发限流（0：未触发，1：已触发）
local trigger_result = 0
-- 初始化变量：触发的规则类型（0：无规则，1-普通限流规则，2-深度限流规则）
local trigger_call_Stat = 0
-- 初始化变量：当前时间窗口内的请求次数
local api_count = 0
-- 初始化变量：当前生效的限流阈值
local threshold = 0
-- 解析传入的限流规则参数（JSON格式，来自Java端的ApiRule参数）
-- cjson.decode 用于将 JSON 字符串解码为一个 Lua 表。这个函数接受一个 JSON 字符串作为参数，并返回一个对应的 Lua 表。
local apiRule = cjson.decode(KEYS[1])
-- 获取规则类型（1：普通限流规则， 2：深度限流规则）
local api_rule_type = apiRule.apiRuleType
-- 普通规则相关参数提取
local rule_key = apiRule.ruleKey  -- 普通规则的计数Key（用于记录请求次数）
local rule_stat_time = apiRule.statTime  -- 普通规则的时间窗口（秒）
local rule_threshold = apiRule.threshold  -- 普通规则的限流阈值（允许的最大请求次数）
local rule_effective_time = apiRule.effectiveTime  -- 普通规则触发后，限制生效的时间（秒）
local rule_limit_key = apiRule.ruleLimitKey  -- 普通规则的限制状态Key（标记是否处于限流中）
local z_set_key = apiRule.zSetRuleStatKey  -- 普通规则的统计有序集合Key（存储触发限流的时间点）
local current_Time = apiRule.currentTime  -- 当前时间戳（毫秒）
local message_index = -1  -- 深度规则的提示消息索引（默认-1，表示无）


-- ########################### 普通规则处理逻辑 ###########################
-- 1.递增请求计数
local count = tonumber(redis.call('incrby', rule_key, 1))
-- 2.首次计数时，设置时间窗口过期时间（确保只在第一个请求时设置，避免重复覆盖）
if (count == 1) then
    redis.call('expire', rule_key, rule_stat_time)
end
-- 3.判断是否超过普通规则阈值
if ((count - rule_threshold) >= 0) then
    -- 检查是否处于限流状态（若限制Key不存在，则说明是本轮首次触发）
    if (redis.call('exists', rule_limit_key) == 0) then
        -- 设置限制Key并配置过期时间（限制生效时长）
        redis.call('set', rule_limit_key, rule_limit_key)
        redis.call('expire', rule_limit_key, rule_effective_time)
        -- 标记触发的是普通规则
        trigger_call_Stat = 1
        -- 将本次触发记录到有序集合（用于深度规则统计，score为时间戳，member为“时间戳_请求数”）
        local z_set_member = current_Time .. "_" .. tostring(count)
        redis.call('zadd', z_set_key, current_Time, z_set_member)
    end
    -- 标记触发限流
    trigger_result = 1
end
-- 4.若已处于限流状态（限制Key存在），直接标记触发限流
if (redis.call('exists', rule_limit_key) == 1) then
    trigger_result = 1
end
-- 5.记录普通规则的当前请求数和阈值（用于返回结果）
api_count = count
threshold = rule_threshold


-- ########################### 深度规则处理逻辑（若规则类型为深度规则） ###########################
if (api_rule_type == 2) then
    -- 获取所有深度规则配置
    local depthRules = apiRule.depthRules
    -- 循环处理每个深度规则
    for index, depth_rule in ipairs(depthRules) do
        -- 提取深度规则参数
        local start_time_window = depth_rule.startTimeWindowTimestamp  -- 规则生效开始时间戳（毫秒）
        local end_time_window = depth_rule.endTimeWindowTimestamp   -- 规则生效结束时间戳（毫秒）
        local depth_rule_stat_time = depth_rule.statTime    -- 深度规则的时间窗口（秒）
        local depth_rule_threshold = depth_rule.threshold   -- 深度规则的限流阈值
        local depth_rule_effective_time = depth_rule.effectiveTime  -- 深度规则触发后，限制生效的时间（秒）
        local depth_rule_limit_key = depth_rule.depthRuleLimit  -- 深度规则的限制状态Key
        -- 更新当前生效的阈值为深度规则阈值
        threshold = depth_rule_threshold
        -- 清理过期的统计数据（删除有序集合中早于规则开始时间的记录）
        if (current_Time > start_time_window) then
            redis.call('zremrangebyscore', z_set_key, 0, start_time_window - 1000)
        end
        -- 检查当前时间是否在深度规则的生效时间窗口内
        if (current_Time >= start_time_window and current_Time <= end_time_window) then
            -- 计算统计的时间范围（最小时间：规则开始时间或当前时间减去时间窗口，取较晚者）
            local z_set_min_score = start_time_window;
            if ((current_Time - start_time_window) > depth_rule_stat_time * 1000) then
                z_set_min_score = current_Time - (depth_rule_stat_time * 1000)  -- 转换秒为毫秒
            end
            local z_set_max_score = current_Time;  -- 最大时间为当前时间
            -- 统计时间范围内的触发次数（从有序集合中计数）
            local rule_trigger_count = tonumber(redis.call('zcount', z_set_key, z_set_min_score, z_set_max_score))
            api_count = rule_trigger_count  -- 更新请求数为深度规则统计的次数
            -- 判断是否超过深度规则阈值
            if ((rule_trigger_count - depth_rule_threshold) >= 0) then
                -- 检查是否处于深度限流状态（若限制Key不存在，说明是本轮首次触发）
                if (redis.call('exists', depth_rule_limit_key) == 0) then
                    -- 设置深度规则限制Key并配置过期时间
                    redis.call('set', depth_rule_limit_key, depth_rule_limit_key)
                    redis.call('expire', depth_rule_limit_key, depth_rule_effective_time)
                    -- 标记触发限流和深度规则类型
                    trigger_result = 1
                    trigger_call_Stat = 2
                    -- 记录当前深度规则的索引（用于返回对应的提示消息）
                    message_index = index
                    -- 提前返回结果（深度规则优先级高于普通规则）
                    return string.format('{"triggerResult": %d, "triggerCallStat": %d, "apiCount": %d, "threshold": %d, "messageIndex": %d}'
                    , trigger_result, trigger_call_Stat, api_count, threshold, message_index)
                end
            end
            -- 若已处于深度限流状态（限制Key存在），直接标记触发限流并返回
            if (redis.call('exists', depth_rule_limit_key) == 1) then
                trigger_result = 1
                message_index = index  -- 记录规则索引
                return string.format('{"triggerResult": %d, "triggerCallStat": %d, "apiCount": %d, "threshold": %d, "messageIndex": %d}'
                , trigger_result, trigger_call_Stat, api_count, threshold, message_index)
            end
        end
    end
end

-- ########################### 返回最终结果 ###########################
-- 以JSON格式返回限流判断结果（供Java端解析为ApiRestrictData对象）
return string.format('{"triggerResult": %d, "triggerCallStat": %d, "apiCount": %d, "threshold": %d, "messageIndex": %d}'
, trigger_result, trigger_call_Stat, api_count, threshold, message_index)
