package com.damai.toolkit;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.lang.Assert;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 雪花算法id生成器实现类
 * @author: 阿星不是程序员
 **/
@Slf4j
public class SnowflakeIdGenerator {

    // 基准时间戳，取自Twitter雪花算法的起始时间：2010-11-04 09:42:54.657
    private static final long BASIS_TIME = 1288834974657L;
    // 工作节点id所占的位数（5位，支持最大31个节点）
    private final long workerIdBits = 5L;
    // 数据中心id所占的位数（5位，支持最大31个节点）
    private final long datacenterIdBits = 5L;
    // 最大工作节点ID（2^5 - 1 = 31）
    @Getter
    private final long maxWorkerId = ~(-1L << workerIdBits);
    // 最大数据中心ID（2^5 - 1 = 31）
    @Getter
    private final long maxDatacenterId = ~(-1L << datacenterIdBits);
    // 序列号所占的位数（12位，支持每个毫秒内生成4096个ID）
    private final long sequenceBits = 12L;
    // 工作节点ID的移位位数（序列号位数，12位）
    private final long workerIdShift = sequenceBits;
    // 数据中心ID的移位位数（序列号位数 + 工作节点ID位数，12+5=17位）
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    // 时间戳的移位位数（序列号位数 + 工作节点ID位数 + 数据中心ID位数，12+5+5=22位）
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    // 序列号的掩码（用于限制序列号在12位范围内，0xFF）
    private final long sequenceMask = ~(-1L << sequenceBits);

    // 当前工作节点ID
    private final long workerId;
    // 当前数据中心ID
    private final long datacenterId;
    // 序列号（同一毫秒内的自增序号）
    private long sequence = 0L;
    // 上一次生成ID的时间戳
    private long lastTimestamp = -1L;
    // 网络地址（用于自动生成节点ID）
    private InetAddress inetAddress;


    /**
     * 构造方法：通过WorkDataCenterId对象初始化工作节点ID和数据中心ID
     *
     * @param workDataCenterId
     */
    public SnowflakeIdGenerator(WorkDataCenterId workDataCenterId) {
        // 若数据中心ID不为空，直接使用对象中的ID
        if (Objects.nonNull(workDataCenterId.getDataCenterId())) {
            this.workerId = workDataCenterId.getWorkId();
            this.datacenterId = workDataCenterId.getDataCenterId();
        } else {
            // 否则自动生成数据中心ID和工作节点ID
            this.datacenterId = getDatacenterId(maxDatacenterId);
            workerId = getMaxWorkerId(datacenterId, maxWorkerId);
        }
    }

    /**
     * 构造方法：通过网络地址初始化，自动生成节点ID
     *
     * @param inetAddress 网络地址对象
     */
    public SnowflakeIdGenerator(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
        this.datacenterId = getDatacenterId(maxDatacenterId);
        this.workerId = getMaxWorkerId(datacenterId, maxWorkerId);
        initLog();
    }

    /**
     * 初始化日志：打印数据中心id和工作节点id
     */
    private void initLog() {
        if (log.isDebugEnabled()) {
            log.debug("Initialization SnowflakeIdGenerator datacenterId:{} workerId:{}", this.datacenterId, this.workerId);
        }
    }


    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        Assert.isFalse(workerId > maxWorkerId || workerId < 0,
                String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        Assert.isFalse(datacenterId > maxDatacenterId || datacenterId < 0,
                String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        initLog();
    }

    /**
     * 生成工作节点id（基于数据中心id和进程信息）
     *
     * @param datacenterId 数据中心ID
     * @param maxWorkerId  最大工作节点ID限制
     * @return 生成的工作节点ID
     */
    protected long getMaxWorkerId(long datacenterId, long maxWorkerId) {
        StringBuilder mpid = new StringBuilder();
        mpid.append(datacenterId);
        // 获取进程ID（格式通常为“pid@hostname”）
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (StringUtils.isNotBlank(name)) {
            mpid.append(name.split("@")[0]);  // 取@符号前的pid部分
        }
        // 通过哈希计算并取模，确保ID在合法范围内
        return (mpid.toString().hashCode() & 0xffff) % (maxWorkerId + 1);
    }

    /**
     * 生成数据中心ID（基于MAC地址）
     *
     * @param maxDatacenterId 最大数据中心ID限制
     * @return 生成的数据中心ID
     */
    protected long getDatacenterId(long maxDatacenterId) {
        long id = 0L;
        try {
            if (this.inetAddress == null) {
                this.inetAddress = InetAddress.getLocalHost();  // 获取本地主机地址
            }
            // 获取网络接口（基于IP地址）
            NetworkInterface network = NetworkInterface.getByInetAddress(this.inetAddress);
            if (null == network) {
                id = 1L;  // 网络接口为空时默认ID为1
            } else {
                // 获取MAC地址（硬件地址）
                byte[] mac = network.getHardwareAddress();
                if (mac != null) {
                    // 取MAC地址后两位计算ID
                    id = ((0x000000FF & (long) mac[mac.length - 2]) |
                            (0x0000FF00 & (((long) mac[mac.length - 1]) << 8))) >> 6;
                    id = id % (maxDatacenterId + 1);  // 确保ID在合法范围内
                }
            }
        } catch (Exception e) {
            log.warn(" getDatacenterId: {}", e.getMessage());
        }
        return id;
    }

    /**
     * 获取当前时间戳并处理时钟回拨问题，生成序列号
     *
     * @return 当前时间戳（毫秒）
     */
    public long getBase() {
        // 5毫秒的容忍阈值
        int five = 5;
        // 获取当前时间戳
        long timestamp = timeGen();
        // 处理时钟回拨问题（当前时间戳小于上一次生成ID的时间戳）
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            // 回拨时间在5毫秒内，等待后重试
            if (offset <= five) {
                try {
                    // 等待两倍的偏移时间（避免再次回拨）
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        // 重试后仍回拨，抛出异常
                        throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", offset));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                // 回拨时间超过5毫秒，直接抛出异常
                throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", offset));
            }
        }
        // 处理同一毫秒内的序列号
        if (lastTimestamp == timestamp) {
            // 相同毫秒内，序列号自增（并通过掩码限制范围）
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                // 同一毫秒的序列数已经达到最大，等待到下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒内，序列号随机初始化为1或2（避免0值重复）
            sequence = ThreadLocalRandom.current().nextLong(1, 3);
        }
        lastTimestamp = timestamp;  // 更新上一次时间戳
        return timestamp;
    }

    /**
     * 生成下一个全局唯一ID
     *
     * @return
     */
    public synchronized long nextId() {
        // 获取处理后的时间戳
        long timestamp = getBase();
        // 组合ID：时间戳差 + 数据中心ID + 工作节点ID + 序列号
        return ((timestamp - BASIS_TIME) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    /**
     * 生成包含用户id和表数量的订单号
     *
     * @param userId     用户ID
     * @param tableCount 表数量（用于分表场景）
     * @return 包含分表信息的订单号
     */
    public synchronized long getOrderNumber(long userId, long tableCount) {
        long timestamp = getBase();
        // 计算分表所需的移位位数
        long sequenceShift = log2N(tableCount);
        // 组合ID：时间戳差 + 数据中心ID + 工作节点ID + 序列号（移位后） + 用户ID取模（分表标识）
        return ((timestamp - BASIS_TIME) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | (sequence << sequenceShift)
                | (userId % tableCount);
    }

    /**
     * 等待到下一毫秒（当序列号最大）
     *
     * @param lastTimestamp 上一次的时间戳
     * @return 新的时间戳（大于lastTimestamp）
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();  // 循环等待直到时间戳递增
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳（使用SystemClock提高性能）
     *
     * @return 当前时间戳（毫秒）
     */
    protected long timeGen() {
        return SystemClock.now();
    }

    /**
     * 从ID中解析出时间戳
     *
     * @param id 雪花算法生成的ID
     * @return 生成该ID时的时间戳（毫秒）
     */
    public static long parseIdTimestamp(long id) {
        return (id >> 22) + BASIS_TIME; // 右移22位获取时间戳差，加上基准时间
    }

    /**
     * 计算以2为底的对数（用于分表移位位数）
     *
     * @param count 数值
     * @return 对数结果（long类型）
     */
    public long log2N(long count) {
        return (long) (Math.log(count) / Math.log(2));
    }

}