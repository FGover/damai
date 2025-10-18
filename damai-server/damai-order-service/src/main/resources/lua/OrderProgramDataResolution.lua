-- 订单状态变更时的缓存原子更新脚本
-- 根据订单状态（支付/取消）执行座位状态迁移和票档余票调整

-- 解析输入参数：订单操作状态码
--    NO_PAY(1,"未支付")
--    CANCEL(2,"已取消")
--    PAY(3,"已支付")
--    REFUND(4,"已退单")
local operate_order_status = tonumber(KEYS[1])
-- 需从锁定缓存删除的座位信息
-- 格式示例：[{"programSeatLockHashKey":"锁定缓存键","unLockSeatIdList":["座位ID1","座位ID2"]},...]
local un_lock_seat_id_json_array = cjson.decode(ARGV[1])
-- 需添加到目标缓存的座位数据
-- 格式示例：[{"seatHashKeyAdd":"目标缓存键","seatDataList":["座位ID1","座位1详情JSON","座位ID2","座位2详情JSON"]},...]
local add_seat_data_json_array = cjson.decode(ARGV[2])

-- 1.从锁定缓存中批量删除座位（解锁操作）
-- 遍历所有票档的解锁信息
for index, un_lock_seat_id_json_object in pairs(un_lock_seat_id_json_array) do
    -- 获取该票档的锁定座位缓存键（如：PROGRAM_SEAT_LOCK_RESOLUTION_HASH:节目ID:票档ID）
    local program_seat_hash_key = un_lock_seat_id_json_object.programSeatLockHashKey
    -- 获取需从锁定缓存中删除的座位ID列表（如：["101","102"]）
    local un_lock_seat_id_list = un_lock_seat_id_json_object.unLockSeatIdList
    -- 执行哈希删除命令：从锁定缓存中移除这些座位
    -- HDEL key field1 field2 ...：删除哈希中指定的field（座位ID）
    redis.call('HDEL', program_seat_hash_key, unpack(un_lock_seat_id_list))
end

-- 2.将座位添加到目标缓存（更新座位状态）
-- 遍历所有票档的添加信息
for index, add_seat_data_json_object in pairs(add_seat_data_json_array) do
    -- 获取目标缓存键（根据订单状态动态确定）
    -- 取消订单：未售座位缓存（PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH:节目ID:票档ID）
    -- 支付订单：已售座位缓存（PROGRAM_SEAT_SOLD_RESOLUTION_HASH:节目ID:票档ID）
    local seat_hash_key_add = add_seat_data_json_object.seatHashKeyAdd
    -- 获取需添加的座位数据列表（格式：[座位ID1, 座位1详情JSON, 座位ID2, 座位2详情JSON, ...]）
    local seat_data_list = add_seat_data_json_object.seatDataList
    -- 执行哈希批量设置命令：将座位添加到目标缓存，同步更新状态
    -- HMSET key field1 value1 field2 value2 ...：批量设置哈希的field-value（座位ID→座位详情）
    redis.call('HMSET', seat_hash_key_add, unpack(seat_data_list))
end

-- 3.若为“取消订单”（状态码=2），恢复票档余票
-- 支付订单无需调整余票（已锁定的座位转为已售，余票不变（已在锁定时扣减））
if (operate_order_status == 2) then
    -- 余票变更数据
    -- 格式示例：[{"programTicketRemainNumberHashKey":"余票缓存键","ticketCategoryId":"100","count":2},...]
    local ticket_category_list = cjson.decode(ARGV[3])
    -- 遍历所有票档的余票变更信息
    for index, increase_data in ipairs(ticket_category_list) do
        -- 获取该票档的余票缓存键（如：PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION:节目ID:票档ID）
        local program_ticket_remain_number_hash_key = increase_data.programTicketRemainNumberHashKey
        -- 获取票档ID（如：100）
        local ticket_category_id = increase_data.ticketCategoryId
        -- 获取余票变更数据（取消订单时为正数，恢复之前扣减的余票）
        local increase_count = increase_data.count
        -- 执行哈希自增命令：更新票档余票数量
        -- HINCRBY key field increment：对哈希中field的值增加increment（此处为恢复余票）
        redis.call('HINCRBY', program_ticket_remain_number_hash_key, ticket_category_id, increase_count)
    end
end