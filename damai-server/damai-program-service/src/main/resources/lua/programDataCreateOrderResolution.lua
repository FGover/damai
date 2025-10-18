-- 用户自主选座（type=1）和自动分配座位（type=2）

-- 操作类型
local type = tonumber(KEYS[1])
-- 未售座位的Redis哈希键（含有占位符，后续需填充programId和ticketCategoryId）
local placeholder_seat_no_sold_hash_key = KEYS[2]
-- 锁定座位的Redis哈希键（含有占位符，后续需填充programId和ticketCategoryId）
local placeholder_seat_lock_hash_key = KEYS[3]
-- 节目ID
local program_id = KEYS[4]
-- 票档购买信息列表：包含每个票档的库存键、票档ID、购买数量等
local ticket_count_list = cjson.decode(ARGV[1])
-- 最终确认可购买的座位列表
local purchase_seat_list = {}
-- 用户请求中携带的座位价格总和
local total_seat_dto_price = 0
-- 缓存中实际的座位价格总和
local total_seat_vo_price = 0

-- 自动匹配座位算法（同排且列号连续）
local function find_adjacent_seats(all_seats, seat_count)
    local adjacent_seats = {}

    table.sort(all_seats, function(s1, s2)
        if s1.rowCode == s2.rowCode then
            return s1.colCode < s2.colCode
        else
            return s1.rowCode < s2.rowCode
        end
    end)

    for i = 1, #all_seats - seat_count + 1 do
        local seats_found = true
        for j = 0, seat_count - 2 do
            local current = all_seats[i + j]
            local next = all_seats[i + j + 1]

            if not (current.rowCode == next.rowCode and next.colCode - current.colCode == 1) then
                seats_found = false
                break
            end
        end
        if seats_found then
            for k = 0, seat_count - 1 do
                table.insert(adjacent_seats, all_seats[i + k])
            end
            return adjacent_seats
        end
    end
    return adjacent_seats
end

-- 用户自主选座（type=1）
if (type == 1) then
    -- 1.先校验每个票档的库存是否充足
    for index, ticket_count in ipairs(ticket_count_list) do
        -- 票档库存的Redis哈希键（存储该票档的剩余可售数量）
        local ticket_remain_number_hash_key = ticket_count.programTicketRemainNumberHashKey
        -- 当前票档ID
        local ticket_category_id = ticket_count.ticketCategoryId
        -- 用户购买的该票档数量
        local count = ticket_count.ticketCount
        -- 从缓存中查询该票档的剩余数量
        local remain_number_str = redis.call('hget', ticket_remain_number_hash_key, tostring(ticket_category_id))
        -- 缓存中没有该票档库存数据，返回票档库存数据不存在
        if not remain_number_str then
            return string.format('{"%s": %d}', 'code', 40010)
        end
        local remain_number = tonumber(remain_number_str)
        -- 用户要购买的数量如果超过剩余库存，返回库存不足
        if (count > remain_number) then
            return string.format('{"%s": %d}', 'code', 40011)
        end
    end
    -- 2.校验用户选中的座位是否有效（未售、未锁定，且价格一致）
    -- 用户选中的座位信息列表
    local seat_data_list = cjson.decode(ARGV[2])
    for index, seatData in pairs(seat_data_list) do
        -- 该票档未售座位的Redis哈希键（存储座位ID -> 座位详情）
        local seat_no_sold_hash_key = seatData.seatNoSoldHashKey;
        -- 用户选中的具体座位列表（每个座位含ID、价格等）
        local seat_dto_list = cjson.decode(seatData.seatDataList)
        for index2, seat_dto in ipairs(seat_dto_list) do
            -- 座位ID
            local id = seat_dto.id
            -- 座位价格（前端传入）
            local seat_dto_price = seat_dto.price
            -- 从缓存中查询该座位的实际信息（校验是否存在且状态有效）
            local seat_vo_str = redis.call('hget', seat_no_sold_hash_key, tostring(id))
            -- 座位不存在于未售缓存中，返回座位不存在或已被占用
            if not seat_vo_str then
                return string.format('{"%s": %d}', 'code', 40001)
            end
            -- 解析缓存中的座位详情
            local seat_vo = cjson.decode(seat_vo_str)
            -- 如果缓存中座位是锁定状态，返回座位已被锁定
            if (seat_vo.sellStatus == 2) then
                return string.format('{"%s": %d}', 'code', 40002)
            end
            -- 如果缓存中座位是已售状态，返回座位已售出
            if (seat_vo.sellStatus == 3) then
                return string.format('{"%s": %d}', 'code', 40003)
            end
            -- 座位有效，添加到可购买列表
            table.insert(purchase_seat_list, seat_vo)
            -- 累加价格用于校验（防止前端篡改价格）
            -- 请求价格总和
            total_seat_dto_price = total_seat_dto_price + seat_dto_price
            -- 缓存中实际价格总和
            total_seat_vo_price = total_seat_vo_price + seat_vo.price
            -- 如果请求价格总和 > 实际价格总和，返回价格校验失败
            if (total_seat_dto_price > total_seat_vo_price) then
                return string.format('{"%s": %d}', 'code', 40008)
            end
        end
    end
end

-- 自动选座算法（type=2）
if (type == 2) then
    -- 遍历票档列表
    for index, ticket_count in ipairs(ticket_count_list) do
        -- 票档库存的Redis哈希键（存储该票档的剩余可售数量）
        local ticket_remain_number_hash_key = ticket_count.programTicketRemainNumberHashKey
        -- 当前票档ID
        local ticket_category_id = ticket_count.ticketCategoryId
        -- 用户购买的该票档数量
        local count = ticket_count.ticketCount
        -- 从缓存中查询该票档的剩余数量（同上面一样）
        local remain_number_str = redis.call('hget', ticket_remain_number_hash_key, tostring(ticket_category_id))
        if not remain_number_str then
            return string.format('{"%s": %d}', 'code', 40010)
        end
        local remain_number = tonumber(remain_number_str)
        if (count > remain_number) then
            return string.format('{"%s": %d}', 'code', 40011)
        end
        -- 查询该票档的所有未售座位
        -- 未售座位的Redis哈希键
        local seat_no_sold_hash_key = ticket_count.seatNoSoldHashKey
        -- 获取所有未售座位详情
        local seat_vo_no_sold_str_list = redis.call('hvals', seat_no_sold_hash_key)
        -- 解析后的未售座位列表
        local filter_seat_vo_no_sold_list = {}
        -- 将JSON字符串转换为座位对象
        for index, seat_vo_no_sold_str in ipairs(seat_vo_no_sold_str_list) do
            local seat_vo_no_sold = cjson.decode(seat_vo_no_sold_str)
            table.insert(filter_seat_vo_no_sold_list, seat_vo_no_sold)
        end
        -- 查找相邻座位（如果找不到相邻座位，返回库存不足）
        purchase_seat_list = find_adjacent_seats(filter_seat_vo_no_sold_list, count)
        -- 如果找不到相邻座位，返回无足够的相邻座位
        if (#purchase_seat_list < count) then
            return string.format('{"%s": %d}', 'code', 40004)
        end
    end
end

-- 按票档ID分组的座位ID数组（用于从“未售”中删除）
local seat_id_list = {}
-- 按票档ID分组的座位数据数组（座位ID -> 座位详情，用于添加到“锁定”）
local seat_data_list = {}
for index, seat in ipairs(purchase_seat_list) do
    -- 座位ID
    local seat_id = seat.id
    -- 票档ID
    local ticket_category_id = seat.ticketCategoryId
    -- 若不存在则初始化票档分组
    if not seat_id_list[ticket_category_id] then
        seat_id_list[ticket_category_id] = {}
    end
    -- 记录座位ID
    table.insert(seat_id_list[ticket_category_id], tostring(seat_id))
    if not seat_data_list[ticket_category_id] then
        seat_data_list[ticket_category_id] = {}
    end
    -- 存储“座位ID -> 座位详情”键值对
    table.insert(seat_data_list[ticket_category_id], tostring(seat_id))
    -- 更新座位状态为“锁定”
    seat.sellStatus = 2
    -- 存储锁定状态的座位详情
    table.insert(seat_data_list[ticket_category_id], cjson.encode(seat))
end
-- 扣减票档库存
for index, ticket_count in ipairs(ticket_count_list) do
    -- 库存键
    local ticket_remain_number_hash_key = ticket_count.programTicketRemainNumberHashKey
    -- 票档ID
    local ticket_category_id = ticket_count.ticketCategoryId
    -- 用户购买的该票档数量
    local count = ticket_count.ticketCount
    -- hincrby：原子性减少库存（负数表示扣减）
    redis.call('hincrby', ticket_remain_number_hash_key, ticket_category_id, "-" .. count)
end
-- 将座位从“未售”缓存中移除
for ticket_category_id, seat_id_array in pairs(seat_id_list) do
    -- 填充占位符，生成实际的未售座位键（programId+ticketCategoryId）
    local actual_seat_no_sold_key = string.format(placeholder_seat_no_sold_hash_key, program_id, tostring(ticket_category_id))
    -- hdel：批量删除已锁定的座位（从"未售"中移除）
    redis.call('hdel', actual_seat_no_sold_key, unpack(seat_id_array))
end
-- 将座位添加到“锁定”缓存中
for ticket_category_id, seat_data_array in pairs(seat_data_list) do
    -- 填充占位符，生成实际的锁定座位键（programId+ticketCategoryId）
    local actual_seat_lock_key = string.format(placeholder_seat_lock_hash_key, program_id, tostring(ticket_category_id))
    -- hmset：批量添加锁定状态的座位（座位ID→锁定状态的详情）
    redis.call('hmset', actual_seat_lock_key, unpack(seat_data_array))
end
-- 返回成功和确认购买的座位列表
return string.format('{"%s": %d, "%s": %s}', 'code', 0, 'purchaseSeatList', cjson.encode(purchase_seat_list))