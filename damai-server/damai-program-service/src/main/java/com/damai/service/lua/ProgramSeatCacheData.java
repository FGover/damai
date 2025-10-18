package com.damai.service.lua;

import com.alibaba.fastjson.JSON;
import com.damai.redis.RedisCache;
import com.damai.vo.SeatVo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 查询节目座位缓存
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramSeatCacheData {

    @Autowired
    private RedisCache redisCache;

    private DefaultRedisScript redisScript;

    private static final Integer THRESHOLD_VALUE = 2000;

    @PostConstruct
    public void init() {
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/programSeat.lua")));
            redisScript.setResultType(Object.class);
        } catch (Exception e) {
            log.error("redisScript init lua error", e);
        }
    }

    /**
     * 从Redis中批量查询并解析座位信息（整合多个缓存键的结果）
     * 负责将Redis中的序列化数据转换为业务所需的SeatVo列表，支持大数据量时的并行处理
     *
     * @param keys 缓存键列表（对应未售、锁定、已售座位的Redis哈希键）
     * @param args 额外参数
     * @return 座位信息列表
     */
    public List<SeatVo> getData(List<String> keys, String[] args) {
        List<SeatVo> seatVoList;
        // 执行redis脚本，批量查询多个哈希键的所有字段值
        // 执行结果：返回所有缓存键对应的座位信息字符串列表（如JSON格式）
        Object object = redisCache.getInstance().execute(redisScript, keys, args);
        // 转换查询结果为字符串列表
        List<String> seatVoStrlist = new ArrayList<>();
        // 校验结果非空且为ArrayList类型（脚本返回的列表类型）
        if (Objects.nonNull(object) && object instanceof ArrayList) {
            seatVoStrlist = (ArrayList<String>) object;
        }
        // 根据数据量旋转序列化方式
        if (seatVoStrlist.size() > THRESHOLD_VALUE) {
            // 若数据量超过阈值，使用并行流解析（多线程处理，提升大列表转换效率）
            seatVoList = seatVoStrlist.parallelStream()
                    .map(seatVoStr -> JSON.parseObject(seatVoStr, SeatVo.class))
                    .collect(Collectors.toList());
        } else {
            // 若数据量较小，使用普通流解析（单线程处理，避免多线程带来的开销）
            seatVoList = seatVoStrlist.stream()
                    .map(seatVoStr -> JSON.parseObject(seatVoStr, SeatVo.class))
                    .collect(Collectors.toList());
        }
        return seatVoList;
    }
}
