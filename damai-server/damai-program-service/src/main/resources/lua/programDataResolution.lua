-- 订单状态变更时的Redis缓存更新脚本
-- 功能：处理订单创建/取消时的票档余票更新、座位状态迁移（未售↔锁定）

-- ARG[1]：票档余票变更数据（JSON数组）
-- 格式示例：[{"programTicketRemainNumberHashKey":"xxx","ticketCategoryId":"1","count":"-2"},...]
local ticket_category_list = cjson.decode(ARGV[1])
-- ARG[2]：需要删除的座位信息（JSON数组）
-- 格式示例：[{"seatHashKeyDel":"xxx","seatIdList":["1","2"]},...]
local del_seat_list = cjson.decode(ARGV[2])
-- ARG[3]：需要添加的座位信息（JSON数组）
-- 格式示例：[{"seatHashKeyAdd":"xxx","seatDataList":["1","{...}", "2","{...}"]},...]
local add_seat_data_list = cjson.decode(ARGV[3])

-- 1.更新票档余票数量
-- 订单创建：count为负数（扣减余票）
-- 订单取消：count为正数（恢复余票）
for index, increase_data in ipairs(ticket_category_list) do
    -- 票档余票存储的Redis哈希键（格式：PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION:programId:ticketCategoryId）
    local program_ticket_remain_number_hash_key = increase_data.programTicketRemainNumberHashKey
    -- 票档ID（作为哈希的field）
    local ticket_category_id = increase_data.ticketCategoryId
    -- 变更数量（作为哈希的value）
    local increase_count = increase_data.count
    -- 执行哈希字段值增减
    -- HINCRBY key field increment：对哈希中field的值增加increment
    redis.call('HINCRBY', program_ticket_remain_number_hash_key, ticket_category_id, increase_count)
end

-- 2.从原状态哈希中删除座位
-- 订单创建：从“未售座位哈希”中删除
-- 订单取消：从“锁定座位哈希”中删除
for index, seat in pairs(del_seat_list) do
    -- 要删除的座位所在的Redis哈希键（未售/锁定座位哈希）
    local seat_hash_key_del = seat.seatHashKeyDel
    -- 需要删除的座位ID列表（哈希的field）
    local seat_id_list = seat.seatIdList
    -- 批量删除哈希中的多个field（座位ID）
    -- HDEL key field1 field2 ...：删除哈希中指定的field
    redis.call('HDEL', seat_hash_key_del, unpack(seat_id_list))
end

-- 3.向新状态哈希中添加座位
-- 订单创建：添加到“锁定座位哈希”中
-- 订单取消：添加到“未售座位哈希”中
for index, seat in pairs(add_seat_data_list) do
    -- 要添加的座位目标Redis哈希键（锁定/未售座位哈希）
    local seat_hash_key_add = seat.seatHashKeyAdd
    -- 需要添加的座位完整数据列表（哈希的field + value）
    local seat_data_list = seat.seatDataList
    -- 批量添加多个field-value对到哈希中（座位ID -> 座位信息）
    -- HMSET key field1 value1 field2 value2 ...：批量设置哈希字段
    redis.call('HMSET', seat_hash_key_add, unpack(seat_data_list))
end