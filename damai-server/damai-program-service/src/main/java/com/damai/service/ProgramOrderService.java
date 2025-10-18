package com.damai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.client.OrderClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.dto.DelayOrderCancelDto;
import com.damai.dto.OrderCreateDto;
import com.damai.dto.OrderTicketUserCreateDto;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.dto.SeatDto;
import com.damai.entity.ProgramShowTime;
import com.damai.enums.BaseCode;
import com.damai.enums.OrderStatus;
import com.damai.enums.SellStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.delaysend.DelayOrderCancelSend;
import com.damai.service.kafka.CreateOrderMqDomain;
import com.damai.service.kafka.CreateOrderSend;
import com.damai.service.lua.ProgramCacheCreateOrderData;
import com.damai.service.lua.ProgramCacheCreateOrderResolutionOperate;
import com.damai.service.lua.ProgramCacheResolutionOperate;
import com.damai.service.tool.SeatMatch;
import com.damai.util.DateUtils;
import com.damai.vo.ProgramVo;
import com.damai.vo.SeatVo;
import com.damai.vo.TicketCategoryVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.service.constant.ProgramOrderConstant.ORDER_TABLE_COUNT;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目订单 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class ProgramOrderService {

    @Autowired
    private OrderClient orderClient;

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private ProgramCacheResolutionOperate programCacheResolutionOperate;

    @Autowired
    ProgramCacheCreateOrderResolutionOperate programCacheCreateOrderResolutionOperate;

    @Autowired
    private DelayOrderCancelSend delayOrderCancelSend;

    @Autowired
    private CreateOrderSend createOrderSend;

    @Autowired
    private ProgramService programService;

    @Autowired
    private ProgramShowTimeService programShowTimeService;

    @Autowired
    private TicketCategoryService ticketCategoryService;

    @Autowired
    private SeatService seatService;

    /**
     * 获取订单创建请求中设计的票档信息列表
     * 用于校验用户选择的票档是否有效，并收集订单相关的票档数据
     *
     * @param programOrderCreateDto 节目订单创建请求参数
     * @param showTime              节目演出时间
     * @return 订单涉及的有效票档信息列表
     */
    public List<TicketCategoryVo> getTicketCategoryList(ProgramOrderCreateDto programOrderCreateDto, Date showTime) {
        // 存储订单实际设计的票档信息
        List<TicketCategoryVo> getTicketCategoryVoList = new ArrayList<>();
        // 从多级缓存查询当前节目在指定演出时间下的所有有效票档
        List<TicketCategoryVo> ticketCategoryVoList =
                ticketCategoryService.selectTicketCategoryListByProgramIdMultipleCache(
                        programOrderCreateDto.getProgramId(),
                        showTime
                );
        // 将票档列表转换为Map（key：票档ID，value：票档信息）
        Map<Long, TicketCategoryVo> ticketCategoryVoMap = ticketCategoryVoList.stream()
                .collect(Collectors.toMap(TicketCategoryVo::getId, ticketCategoryVo -> ticketCategoryVo));
        // 获取用户传入的座位列表（允许用户选座才有值，自动分配座位的为null）
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        // 处理【允许用户选座】：用户选择了具体座位，需校验每个座位对应的票档是否有效
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            // 遍历用户选择的每个座位
            for (SeatDto seatDto : seatDtoList) {
                // 根据座位关联的票档ID，从缓存的票档Map中查询对应的票档信息
                TicketCategoryVo ticketCategoryVo = ticketCategoryVoMap.get(seatDto.getTicketCategoryId());
                // 若票档不存在（可能已下架或不属于当前节目），抛出异常
                if (Objects.nonNull(ticketCategoryVo)) {
                    getTicketCategoryVoList.add(ticketCategoryVo);  // 票档有效，加入结果列表
                } else {
                    throw new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2);
                }
            }
        } else {
            // 处理【自动分配座位】：用户未选择具体座位，需校验用户传入的票档ID是否有效
            TicketCategoryVo ticketCategoryVo = ticketCategoryVoMap.get(programOrderCreateDto.getTicketCategoryId());
            // 若票档不存在，抛出异常
            if (Objects.nonNull(ticketCategoryVo)) {
                getTicketCategoryVoList.add(ticketCategoryVo);   // 票档有效，加入结果列表
            } else {
                throw new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2);
            }
        }
        // 返回订单涉及的所有有效票档信息（用于后续库存校验、价格计算等）
        return getTicketCategoryVoList;
    }

    /**
     * 创建订单
     * 负责处理选座校验、库存检查、价格验证、座位分配及订单创建前置操作
     *
     * @param programOrderCreateDto 节目订单创建请求参数对象，包含用户选择的座位、票档、数量等信息
     * @return 创建成功的订单标识（如订单号）
     */
    public String create(ProgramOrderCreateDto programOrderCreateDto) {
        // 从多级缓存查询节目演出时间信息
        ProgramShowTime programShowTime =
                programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
        // 查询当前节目对应的所有票档信息（结合演出时间过滤有效票档）
        List<TicketCategoryVo> getTicketCategoryList =
                getTicketCategoryList(programOrderCreateDto, programShowTime.getShowTime());
        // 初始化价格校验相关变量
        BigDecimal parameterOrderPrice = new BigDecimal("0"); // 用于累加用户传入的座位总价格（前端提交的价格）
        BigDecimal databaseOrderPrice = new BigDecimal("0"); // 用于累加系统中实际的座位总价格（后端存储的价格）
        // 初始化座位相关集合
        List<SeatVo> purchaseSeatList = new ArrayList<>();  // 最终确定要购买的座位列表（用户选择或系统分配）
        // 用户传入的选座信息（用户自主选座）
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        // 系统中当前节目未售卖的座位列表（用于校验和分配）
        List<SeatVo> seatVoList = new ArrayList<>();
        // 初始化票档余票数量映射（key：票档ID，value：剩余可售数量）
        Map<String, Long> ticketCategoryRemainNumber = new HashMap<>(16);
        // 遍历所有票档，查询可用座位并统计余票
        for (TicketCategoryVo ticketCategory : getTicketCategoryList) {
            // 从缓存中查询当前票档下的座位列表
            // 传入"当前时间到演出时间的秒数差"，用于判断座位是否处于可售状态（如临近演出可能关闭售票）
            List<SeatVo> allSeatVoList = seatService.selectSeatResolution(
                    programOrderCreateDto.getProgramId(),
                    ticketCategory.getId(),
                    DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()),
                    TimeUnit.SECONDS
            );
            // 过滤出“未售卖”状态的座位，存入seatVoList（仅未售卖的座位可被选择或分配）
            seatVoList.addAll(allSeatVoList.stream().
                    filter(seatVo -> seatVo.getSellStatus().equals(SellStatus.NO_SOLD.getCode()))
                    .toList());
            // 查询当前票档的余票数量，并存入映射表（用于后续库存校验）
            ticketCategoryRemainNumber.putAll(ticketCategoryService.getRedisRemainNumberResolution(
                    programOrderCreateDto.getProgramId(), ticketCategory.getId()));
        }
        // 如果用户传入的座位信息不为空
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            // 按票档ID分组统计用户要购买的座位数量（key：票档ID，value：该票档的购买数量）
            Map<Long, Long> seatTicketCategoryDtoCount = seatDtoList.stream()
                    .collect(Collectors.groupingBy(SeatDto::getTicketCategoryId, Collectors.counting()));
            // 校验每个票档的购买数量是否超过余票数量
            for (Entry<Long, Long> entry : seatTicketCategoryDtoCount.entrySet()) {
                Long ticketCategoryId = entry.getKey();  // 票档ID
                Long purchaseCount = entry.getValue();  // 用户要购买的数量
                // 获取该票档的余票数量（若不存在则抛出"票档不存在"异常）
                Long remainNumber = Optional.ofNullable(ticketCategoryRemainNumber.get(String.valueOf(ticketCategoryId)))
                        .orElseThrow(() -> new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
                // 若购买数量 > 余票数量，抛出"库存不足"异常
                if (purchaseCount > remainNumber) {
                    throw new DaMaiFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
                }
            }
            // 校验用户传入的座位是否为“未售卖”状态
            // 将系统中未售卖的座位按“行号-列号”作为key，座位信息作为value，存入seatVoMap（用于后续校验）
            Map<String, SeatVo> seatVoMap = seatVoList.stream()
                    .collect(Collectors.toMap(
                            seat -> seat.getRowCode() + "-" + seat.getColCode(),
                            seat -> seat,
                            (v1, v2) -> v2)
                    );
            // 遍历用户传入的每个座位
            for (SeatDto seatDto : seatDtoList) {
                // 从系统未售座位中查询用户选择的座位
                SeatVo seatVo = seatVoMap.get(seatDto.getRowCode() + "-" + seatDto.getColCode());
                //  若用户选择的座位不在"未售卖"列表中（可能已被卖、已锁定或不存在），抛出异常
                if (Objects.isNull(seatVo)) {
                    throw new DaMaiFrameException(BaseCode.SEAT_IS_NOT_NOT_SOLD);
                }
                // 将合法的座位添加到待购买列表
                purchaseSeatList.add(seatVo);
                // 累加用户传入的座位价格（前端提交的）
                parameterOrderPrice = parameterOrderPrice.add(seatDto.getPrice());
                // 累加系统中该座位的实际价格（后端存储的）
                databaseOrderPrice = databaseOrderPrice.add(seatVo.getPrice());
            }
            // 校验用户传入的座位总价格是否超过系统实际总价格（防止前端篡改价格）
            if (parameterOrderPrice.compareTo(databaseOrderPrice) > 0) {
                throw new DaMaiFrameException(BaseCode.PRICE_ERROR);
            }
        } else {
            // 处理【系统自动分配座位】
            Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();  // 用户选择的票档ID
            Integer ticketCount = programOrderCreateDto.getTicketCount();   // 用户要购买的数量
            // 校验该票档的余票是否充足
            Long remainNumber = Optional.ofNullable(ticketCategoryRemainNumber.get(String.valueOf(ticketCategoryId)))
                    .orElseThrow(() -> new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
            // 如果用户要购买的数量 > 余票数量，抛出异常
            if (ticketCount > remainNumber) {
                throw new DaMaiFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
            }
            // 座位匹配，从当前票档
            // 过滤出当前票档的可用座位，再调用算法匹配座位（连续优先 → 随机兜底）
            List<SeatVo> seats = seatVoList.stream()
                    .filter(seatVo -> Objects.equals(seatVo.getTicketCategoryId(), ticketCategoryId))
                    .toList();
            purchaseSeatList = SeatMatch.matchSeats(
                    seats,
                    ticketCount,
                    SeatMatch.SeatMatchStrategy.PRIORITY_CONTINUOUS
            );
            // 若匹配到的座位数量 < 用户要购买的数量，抛出异常
            if (purchaseSeatList.size() < ticketCount) {
                throw new DaMaiFrameException(BaseCode.SEAT_OCCUPY);
            }
        }
        // 更新缓存中的节目数据（传入“未支付”状态，表明座位已被锁定但未完成支付）
        updateProgramCacheDataResolution(programOrderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.NO_PAY);
        // 执行最终的订单创建逻辑
        return doCreate(programOrderCreateDto, purchaseSeatList);
    }


    /**
     * 新建节目订单方法
     *
     * @param programOrderCreateDto
     * @return
     */
    public String createNew(ProgramOrderCreateDto programOrderCreateDto) {
        // 处理缓存相关逻辑，解析并获取用户实际购买的座位信息
        List<SeatVo> purchaseSeatList = createOrderOperateProgramCacheResolution(programOrderCreateDto);
        // 调用实际的订单创建逻辑，传入原始请求参数和已确认的座位列表
        return doCreate(programOrderCreateDto, purchaseSeatList);
    }

    public String createNewAsync(ProgramOrderCreateDto programOrderCreateDto) {
        List<SeatVo> purchaseSeatList = createOrderOperateProgramCacheResolution(programOrderCreateDto);
        return doCreateV2(programOrderCreateDto, purchaseSeatList);
    }

    /**
     * 订单创建过程中的节目缓存操作与解析
     *
     * @param programOrderCreateDto 订单创建请求DTO
     * @return 确认可购买的座位列表
     */
    public List<SeatVo> createOrderOperateProgramCacheResolution(ProgramOrderCreateDto programOrderCreateDto) {
        // 1.多级缓存中获取节目演出时间
        ProgramShowTime programShowTime = programShowTimeService
                .selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
        // 2.获取该节目的有效票档信息列表
        List<TicketCategoryVo> getTicketCategoryList =
                getTicketCategoryList(programOrderCreateDto, programShowTime.getShowTime());
        // 3.遍历票档
        for (TicketCategoryVo ticketCategory : getTicketCategoryList) {
            // 从缓存中查询座位，如果缓存不存在，则从数据库查询后再放入缓存
            seatService.selectSeatResolution(programOrderCreateDto.getProgramId(), ticketCategory.getId(),
                    DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()), TimeUnit.SECONDS);
            // 从缓存中查询余票数量，如果缓存不存在，则从数据库查询后再放入缓存
            ticketCategoryService.getRedisRemainNumberResolution(
                    programOrderCreateDto.getProgramId(), ticketCategory.getId());
        }
        // 4.构建参数
        // 节目ID
        Long programId = programOrderCreateDto.getProgramId();
        // 用户选择的座位列表
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        List<String> keys = new ArrayList<>();  // 存储Redis操作相关的键前缀或标识
        String[] data = new String[2];  // 存储缓存操作的核心数据（JSON格式）
        JSONArray jsonArray = new JSONArray();   // 存储库存扣减相关的参数
        JSONArray addSeatDatajsonArray = new JSONArray();  // 存储座位锁定相关的参数
        // 是否为自主选座还是自动分配座位
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            // 自主选座
            keys.add("1");
            // 按票档ID分组座位列表
            Map<Long, List<SeatDto>> seatTicketCategoryDtoCount = seatDtoList.stream()
                    .collect(Collectors.groupingBy(SeatDto::getTicketCategoryId));
            // 遍历分组后的座位列表
            for (Entry<Long, List<SeatDto>> entry : seatTicketCategoryDtoCount.entrySet()) {
                // 票档ID
                Long ticketCategoryId = entry.getKey();
                // 该票档用户要购买的数量
                int ticketCount = entry.getValue().size();
                // 构建库存扣减参数：包含Redis中剩余票数的Hash键、票档ID、购票数量
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
                jsonObject.put("ticketCategoryId", ticketCategoryId);
                jsonObject.put("ticketCount", ticketCount);
                jsonArray.add(jsonObject);
                // 构建座位锁定参数：包含Redis中未售座位的Hash键、座位详情列表
                JSONObject seatDatajsonObject = new JSONObject();
                seatDatajsonObject.put("seatNoSoldHashKey", RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
                seatDatajsonObject.put("seatDataList", JSON.toJSONString(entry.getValue()));
                addSeatDatajsonArray.add(seatDatajsonObject);
            }
        } else {
            // 自动分配座位
            keys.add("2");
            // 票档ID
            Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
            // 购买的数量
            Integer ticketCount = programOrderCreateDto.getTicketCount();
            // 构建库存扣减和座位分配的参数
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
            jsonObject.put("ticketCategoryId", ticketCategoryId);
            jsonObject.put("ticketCount", ticketCount);
            jsonObject.put("seatNoSoldHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
            jsonArray.add(jsonObject);
        }
        // 未售卖座位hash的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH));
        // 锁定座位hash的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH));
        // 节目ID
        keys.add(String.valueOf(programOrderCreateDto.getProgramId()));
        data[0] = JSON.toJSONString(jsonArray);
        data[1] = JSON.toJSONString(addSeatDatajsonArray);
        // 5.执行Lua脚本
        ProgramCacheCreateOrderData programCacheCreateOrderData =
                programCacheCreateOrderResolutionOperate.programCacheOperate(keys, data);
        if (!Objects.equals(programCacheCreateOrderData.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(Objects.requireNonNull(BaseCode.getRc(programCacheCreateOrderData.getCode())));
        }
        return programCacheCreateOrderData.getPurchaseSeatList();
    }

    /**
     * 执行订单创建的核心逻辑
     * 负责组装订单参数、调用远程服务创建订单、并发送延迟取消消息（防止订单长期未支付）
     *
     * @param programOrderCreateDto 节目订单创建请求参数
     * @param purchaseSeatList      已确认的购买座位列表
     * @return 创建成功的订单号
     */
    private String doCreate(ProgramOrderCreateDto programOrderCreateDto, List<SeatVo> purchaseSeatList) {
        // 1.构建通用订单创建参数（转换为底层订单服务所需的格式）
        OrderCreateDto orderCreateDto = buildCreateOrderParam(programOrderCreateDto, purchaseSeatList);
        // 2.调用远程订单服务创建订单
        String orderNumber = createOrderByRpc(orderCreateDto, purchaseSeatList);
        // 3.发送延迟取消订单信息（处理未支付超时场景）
        DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
        delayOrderCancelDto.setOrderNumber(orderCreateDto.getOrderNumber());  // 关联订单号
        // 发送延迟消息
        delayOrderCancelSend.sendMessage(JSON.toJSONString(delayOrderCancelDto));
        // 返回创建成功的订单号
        return orderNumber;
    }

    private String doCreateV2(ProgramOrderCreateDto programOrderCreateDto, List<SeatVo> purchaseSeatList) {
        OrderCreateDto orderCreateDto = buildCreateOrderParam(programOrderCreateDto, purchaseSeatList);

        String orderNumber = createOrderByMq(orderCreateDto, purchaseSeatList);

        DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
        delayOrderCancelDto.setOrderNumber(orderCreateDto.getOrderNumber());
        delayOrderCancelSend.sendMessage(JSON.toJSONString(delayOrderCancelDto));

        return orderNumber;
    }

    /**
     * 构建订单创建参数（将节目订单信息和选中的座位信息转换为通用订单服务所需的参数格式）
     *
     * @param programOrderCreateDto 节目订单创建请求DTO（
     * @param purchaseSeatList      已确认的购买座位列表
     * @return 转换后的通用订单创建DTO
     */
    private OrderCreateDto buildCreateOrderParam(ProgramOrderCreateDto programOrderCreateDto, List<SeatVo> purchaseSeatList) {
        // 1.从多级缓存中查询节目基本信息
        ProgramVo programVo = programService.simpleGetProgramAndShowMultipleCache(programOrderCreateDto.getProgramId());
        // 2.构建通用订单创建参数
        OrderCreateDto orderCreateDto = new OrderCreateDto();
        // 3.设置订单核心标识信息
        // 生成唯一订单号（基于用户ID和订单表数量的分布式ID基因生成策略，避免重复和数据跨表）
        orderCreateDto.setOrderNumber(uidGenerator.getOrderNumber(programOrderCreateDto.getUserId(), ORDER_TABLE_COUNT));
        orderCreateDto.setProgramId(programOrderCreateDto.getProgramId());  // 关联节目ID
        orderCreateDto.setUserId(programOrderCreateDto.getUserId());  // 下单用户ID
        // 4.设置订单展示信息（用于用户查看订单详情）
        orderCreateDto.setProgramItemPicture(programVo.getItemPicture());   // 节目封面图
        orderCreateDto.setProgramTitle(programVo.getTitle());  // 节目标题
        orderCreateDto.setProgramPlace(programVo.getPlace());  // 演出地点
        orderCreateDto.setProgramShowTime(programVo.getShowTime());  // 演出时间
        orderCreateDto.setProgramPermitChooseSeat(programVo.getPermitChooseSeat());  // 是否允许选座（冗余字段，用于后续校验）
        // 5.计算订单总金额
        BigDecimal databaseOrderPrice = purchaseSeatList.stream()
                .map(SeatVo::getPrice)  // 提取每个座位的单价
                .reduce(BigDecimal.ZERO, BigDecimal::add);  // 累加得到总金额
        orderCreateDto.setOrderPrice(databaseOrderPrice);  // 设置订单总金额
        orderCreateDto.setCreateOrderTime(DateUtils.now());  // 订单创建时间
        // 6.构建子订单信息（订单与票用户的关联，支持一个订单对应多个观影人）
        List<Long> ticketUserIdList = programOrderCreateDto.getTicketUserIdList();  // 票用户ID列表（观影人ID）
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>();
        // 遍历票用户列表，为每个用户创建对应的子订单信息
        for (int i = 0; i < ticketUserIdList.size(); i++) {
            Long ticketUserId = ticketUserIdList.get(i);  // 当前票用户ID（观影人）
            OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
            // 子订单与主订单关联
            orderTicketUserCreateDto.setOrderNumber(orderCreateDto.getOrderNumber());  // 订单号
            orderTicketUserCreateDto.setProgramId(programOrderCreateDto.getProgramId());  // 节目ID
            orderTicketUserCreateDto.setUserId(programOrderCreateDto.getUserId());   // 下单用户（付款人）
            orderTicketUserCreateDto.setTicketUserId(ticketUserId);  // 票用户ID（观影人）
            // 关联座位信息（确保座位与票用户一一对应）
            SeatVo seatVo = Optional.ofNullable(purchaseSeatList.get(i))
                    .orElseThrow(() -> new DaMaiFrameException(BaseCode.SEAT_NOT_EXIST));
            orderTicketUserCreateDto.setSeatId(seatVo.getId());  // 座位ID
            // 座位文字描述（如"3排5列"）
            orderTicketUserCreateDto.setSeatInfo(seatVo.getRowCode() + "排" + seatVo.getColCode() + "列");
            // 票档ID（用于区分不同票价类型）
            orderTicketUserCreateDto.setTicketCategoryId(seatVo.getTicketCategoryId());
            // 该座位的单价（子订单金额）
            orderTicketUserCreateDto.setOrderPrice(seatVo.getPrice());
            // 子订单创建时间
            orderTicketUserCreateDto.setCreateOrderTime(DateUtils.now());
            // 添加到子订单列表
            orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
        }
        // 7.将子订单列表设置到主订单参数中
        orderCreateDto.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);
        return orderCreateDto;
    }

    /**
     * 通过RPC调用订单服务创建订单，并处理创建失败时的缓存回滚
     *
     * @param orderCreateDto   通用订单创建参数
     * @param purchaseSeatList 已选中的座位列表
     * @return 订单中心返回的订单号
     */
    private String createOrderByRpc(OrderCreateDto orderCreateDto, List<SeatVo> purchaseSeatList) {
        // 通过Feign调用远程订单服务创建订单
        ApiResponse<String> createOrderResponse = orderClient.create(orderCreateDto);
        // 判断订单创建是否成功
        if (!Objects.equals(createOrderResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            log.error("创建订单失败 需人工处理 orderCreateDto : {}", JSON.toJSONString(orderCreateDto));
            // 创建订单失败，回滚缓存数据：将已锁定的座位释放为"未售"，恢复库存
            updateProgramCacheDataResolution(orderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.CANCEL);
            // 抛出异常
            throw new DaMaiFrameException(createOrderResponse);
        }
        // 订单创建成功，返回订单号
        return createOrderResponse.getData();
    }

    private String createOrderByMq(OrderCreateDto orderCreateDto, List<SeatVo> purchaseSeatList) {
        CreateOrderMqDomain createOrderMqDomain = new CreateOrderMqDomain();
        CountDownLatch latch = new CountDownLatch(1);
        createOrderSend.sendMessage(JSON.toJSONString(orderCreateDto), sendResult -> {
            createOrderMqDomain.orderNumber = String.valueOf(orderCreateDto.getOrderNumber());
            assert sendResult != null;
            log.info("创建订单kafka发送消息成功 topic : {}", sendResult.getRecordMetadata().topic());
            latch.countDown();
        }, ex -> {
            log.error("创建订单kafka发送消息失败 error", ex);
            log.error("创建订单失败 需人工处理 orderCreateDto : {}", JSON.toJSONString(orderCreateDto));
            updateProgramCacheDataResolution(orderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.CANCEL);
            createOrderMqDomain.daMaiFrameException = new DaMaiFrameException(ex);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("createOrderByMq InterruptedException", e);
            throw new DaMaiFrameException(e);
        }
        if (Objects.nonNull(createOrderMqDomain.daMaiFrameException)) {
            throw createOrderMqDomain.daMaiFrameException;
        }
        return createOrderMqDomain.orderNumber;
    }

    /**
     * 更新节目相关的缓存数据（处理座位状态和票档余票）
     * 根据订单状态（未支付/已取消）执行不同的缓存操作：
     * - 未支付（NO_PAY）：锁定座位，扣减票档余票
     * - 已取消（CANCEL）：释放座位，恢复票档余票
     *
     * @param programId   节目ID
     * @param seatVoList  需要处理的座位列表（涉及锁定或释放的座位）
     * @param orderStatus 订单状态（仅支持未支付和已取消）
     */
    private void updateProgramCacheDataResolution(Long programId, List<SeatVo> seatVoList, OrderStatus orderStatus) {
        // 1.校验订单状态：仅允许"未支付"和"已取消"两种状态调用此方法
        if (!(Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode()) ||
                Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()))) {
            throw new DaMaiFrameException(BaseCode.OPERATE_ORDER_STATUS_NOT_PERMIT);
        }
        // 2.初始化缓存操作所需的参数容器
        List<String> keys = new ArrayList<>();
        keys.add("#");  // 占位符键
        // data数组用于存储三类缓存操作数据（JSON格式）：
        // data[0]：票档余票变更数据；data[1]：需要从原缓存移除的座位ID；data[2]：需要添加到新缓存的座位数据
        String[] data = new String[3];
        // 3.统计每个票档需要处理的座位数量（用于更新余票）
        // 按票档ID分组，统计每组座位数量（key：票档ID，value：座位数量）
        Map<Long, Long> ticketCategoryCountMap = seatVoList.stream()
                .collect(Collectors.groupingBy(SeatVo::getTicketCategoryId, Collectors.counting()));
        // 4.组装票档余票变更数据（用于更新Redis中的余票哈希表）
        JSONArray jsonArray = new JSONArray();
        ticketCategoryCountMap.forEach((ticketCategoryId, seatCount) -> {
            JSONObject jsonObject = new JSONObject();
            // 余票缓存键：PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION:{programId}:{ticketCategoryId}
            jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
            jsonObject.put("ticketCategoryId", String.valueOf(ticketCategoryId));
            // 根据订单状态设置余票变更数量
            if (Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode())) {
                // 未支付，锁定座位，余票扣减
                jsonObject.put("count", "-" + seatCount);
            } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                // 取消订单，释放座位，余票恢复
                jsonObject.put("count", seatCount);
            }
            jsonArray.add(jsonObject);
        });
        // 5.按票档分组处理座位状态变更（从“未售卖” -> “锁定” 或 “锁定” -> “未售卖”）
        Map<Long, List<SeatVo>> seatVoMap = seatVoList.stream()
                .collect(Collectors.groupingBy(SeatVo::getTicketCategoryId));
        // 存储需要从原缓存中删除的座位ID（如从"未售卖"缓存移走时，需先删除）
        JSONArray delSeatIdjsonArray = new JSONArray();
        // 存储需要添加到新缓存中的座位数据（如移到"锁定"缓存时，需添加带新状态的座位）
        JSONArray addSeatDatajsonArray = new JSONArray();
        seatVoMap.forEach((ticketCategoryId, seatVos) -> {
            JSONObject delSeatIdjsonObject = new JSONObject();  // 单票档的删除缓存信息
            JSONObject seatDatajsonObject = new JSONObject();   // 单票档的添加缓存信息
            String seatHashKeyDel = "";  // 需要删除座位的缓存键
            String seatHashKeyAdd = "";  // 需要添加座位的缓存键
            // 根据订单状态确定座位的移动方向和状态更新
            if (Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode())) {
                // 未支付：座位从“未售卖”缓存 -> “锁定”缓存，状态更新为“已锁定”
                seatHashKeyDel = (RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
                seatHashKeyAdd = (RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
                // 更新座位状态为“锁定”
                for (SeatVo seatVo : seatVos) {
                    seatVo.setSellStatus(SellStatus.LOCK.getCode());
                }
            } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                // 已取消：座位从"锁定"缓存 → "未售卖"缓存，状态恢复为"未售卖"
                seatHashKeyDel = (RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
                seatHashKeyAdd = (RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
                // 更新座位状态为“未售卖”
                for (SeatVo seatVo : seatVos) {
                    seatVo.setSellStatus(SellStatus.NO_SOLD.getCode());
                }
            }
            // 组装需要删除的座位信息：缓存键 + 座位ID列表
            delSeatIdjsonObject.put("seatHashKeyDel", seatHashKeyDel);
            delSeatIdjsonObject.put("seatIdList", seatVos.stream()
                    .map(SeatVo::getId).map(String::valueOf).collect(Collectors.toList()));
            delSeatIdjsonArray.add(delSeatIdjsonObject);
            // 组装需要添加的座位信息：缓存键 + 座位完整数据（ID + 序列化的SeatVo对象）
            seatDatajsonObject.put("seatHashKeyAdd", seatHashKeyAdd);
            List<String> seatDataList = new ArrayList<>();
            for (SeatVo seatVo : seatVos) {
                seatDataList.add(String.valueOf(seatVo.getId()));  // 座位ID（作为哈希的field）
                seatDataList.add(JSON.toJSONString(seatVo));   // 座位完整信息（作为哈希的value）
            }
            seatDatajsonObject.put("seatDataList", seatDataList);
            addSeatDatajsonArray.add(seatDatajsonObject);
        });
        // 6.将三类缓存操作数据转为JSON字符串，存入data数组
        data[0] = JSON.toJSONString(jsonArray);  // 票档余票变更数据
        data[1] = JSON.toJSONString(delSeatIdjsonArray);  // 座位删除数据
        data[2] = JSON.toJSONString(addSeatDatajsonArray);  // 座位添加数据
        // 7.执行Lua脚本
        programCacheResolutionOperate.programCacheOperate(keys, data);
    }
}
