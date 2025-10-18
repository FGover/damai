-- 计数器的值（存储单位时间内的操作次数）
local counter_count_key = KEYS[1]
-- 时间戳的键（存储计数器上次重置的时间戳）
local counter_timestamp_key = KEYS[2]
-- 校验验证码id的键（存储当前请求是否需要验证码的标识）
local verify_captcha_id = KEYS[3]

-- 单位时间内的最大请求次数阈值
local verify_captcha_threshold = tonumber(ARGV[1])
-- 当前请求的时间戳（毫秒级，用于判断时间窗口）
local current_time_millis = tonumber(ARGV[2])
-- 验证码ID的过期时间（秒，控制标识的缓存时长）
local verify_captcha_id_expire_time = tonumber(ARGV[3])
-- 始终开启验证码校验的开关
local always_verify_captcha = tonumber(ARGV[4])

-- 时间窗口大小（1000毫秒）
local differenceValue = 1000

-- 【逻辑1：强制开启验证码】
-- 如果全局开关开启（always_verify_captcha=1），直接要求验证
if always_verify_captcha == 1 then
    -- 存储验证码ID标识为“yes”（需要验证）
    redis.call('set', verify_captcha_id, 'yes')
    -- 设置标识的过期时间
    redis.call('expire', verify_captcha_id, verify_captcha_id_expire_time)
    return 'true'  -- 返回需要验证
end

-- 【逻辑2：初始化计数器】
-- 获取当前计数（默认0）和上次重置时间戳（默认0）
local count = tonumber(redis.call('get', counter_count_key) or "0")
local lastResetTime = tonumber(redis.call('get', counter_timestamp_key) or "0")

-- 【逻辑3：判断时间窗口是否过期】
-- 如果当前时间与上次重置时间的差值 >= 时间窗口（1秒），则重置计数器
if current_time_millis - lastResetTime > differenceValue then
    count = 0  -- 重置计数为0
    redis.call('set', counter_count_key, count)  -- 更新计数
    redis.call('set', counter_timestamp_key, current_time_millis)  -- 更新重置时间为当前时间
end

-- 【逻辑4：更新计数并判断是否超过阈值】
count = count + 1  -- 累加当前请求的计数

-- 如果计数超过阈值（如1秒内超过10次请求）
if count > verify_captcha_threshold then
    -- 重置计数器（避免持续触发验证码）
    count = 0
    redis.call('set', counter_count_key, count)
    redis.call('set', counter_timestamp_key, current_time_millis)
    -- 标记需要验证验证码
    redis.call('set', verify_captcha_id, 'yes')
    redis.call('expire', verify_captcha_id, verify_captcha_id_expire_time)
    return 'true'  -- 返回需要验证
end
-- 【逻辑5：未超过阈值，无需验证】
-- 更新计数（未超过阈值，正常累加）
redis.call('set', counter_count_key, count)
-- 标记不需要验证验证码
redis.call('set', verify_captcha_id, 'no')
redis.call('expire', verify_captcha_id, verify_captcha_id_expire_time)
return 'false'  -- 返回不需要验证
