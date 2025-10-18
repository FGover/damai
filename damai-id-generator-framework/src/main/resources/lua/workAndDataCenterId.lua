-- 从KEYS数组中获取redis中存储work_id的键名（第一个参数）
local snowflake_work_id_key = KEYS[1]
-- 从KEYS数组中获取Redis中存储data_center_id的键名（第二个参数）
local snowflake_data_center_id_key = KEYS[2]
-- 从ARGV数组中获取worker_id的最大阈值（第一个参数），转换为数字类型
local max_worker_id = tonumber(ARGV[1])
-- 从ARGV数组中获取data_center_id的最大阈值（第二个参数），转换为数字类型
local max_data_center_id = tonumber(ARGV[2])
-- 初始化要返回的work_id，默认为0
local return_worker_id = 0
-- 初始化要返回的data_center_id，默认为0
local return_data_center_id = 0
-- 标记work_id是否是首次初始化（不存在时设置为0）
local snowflake_work_id_flag = false
-- 标记data_center_id是否是首次初始化（不存在时设置为0）
local snowflake_data_center_id_flag = false
-- 初始化JSON格式的返回结果（默认值为0）
local json_result = string.format('{"%s": %d, "%s": %d}',
        'workId', return_worker_id,
        'dataCenterId', return_data_center_id)

-- 检查redis中是否存在work_id的键，若不存在（即返回值为0）则初始化为0，并标记初始化完成
if (redis.call('exists', snowflake_work_id_key) == 0) then
    redis.call('set', snowflake_work_id_key, 0)
    snowflake_work_id_flag = true
end

-- 检查redis中是否存在data_center_id的键，若不存在（即返回值为0）则初始化为0，并标记初始化完成
if (redis.call('exists', snowflake_data_center_id_key) == 0) then
    redis.call('set', snowflake_data_center_id_key, 0)
    snowflake_data_center_id_flag = true
end

-- 如果work_id和data_center_id都是首次初始化，直接返回初始值（0,0）
if (snowflake_work_id_flag and snowflake_data_center_id_flag) then
    return json_result
end

-- 从redis中获取当前的work_id值，并转换为数字
local snowflake_work_id = tonumber(redis.call('get', snowflake_work_id_key))
-- 从redis中获取当前的data_center_id值，并转换为数字
local snowflake_data_center_id = tonumber(redis.call('get', snowflake_data_center_id_key))

-- 当work_id达到最大阈值时的处理
if (snowflake_work_id == max_worker_id) then
    -- 若work_id已达最大值，检查data_center_id是否也达最大值
    if (snowflake_data_center_id == max_data_center_id) then
        -- 两者都达最大值时，重置为初始值（0,0），实现循环复用
        redis.call('set', snowflake_work_id_key, 0)
        redis.call('set', snowflake_data_center_id_key, 0)
    else
        -- data_center_id未达最大值时，自增data_center_id，并将结果作为返回值
        return_data_center_id = redis.call('incr', snowflake_data_center_id_key)
    end
else
    -- work_id未达最大值时，自增work_id，并将结果作为返回值
    return_worker_id = redis.call('incr', snowflake_work_id_key)
end

-- 格式化最终的work_id和data_center_id为JSON字符串并返回
return string.format('{"%s": %d, "%s": %d}',
        'workId', return_worker_id,
        'dataCenterId', return_data_center_id)