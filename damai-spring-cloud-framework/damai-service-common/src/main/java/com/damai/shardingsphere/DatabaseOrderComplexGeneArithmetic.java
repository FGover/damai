package com.damai.shardingsphere;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.util.StringUtil;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * 使用雪花算法生成全局唯一的 order_number，在生成时会把 user_id 的一部分（或者它的哈希）拼接到雪花ID的低位，作为【基因位】。
 * 这样同一个用户的订单号末尾相似，保证路由时能用基因位把相同用户的数据尽量落到同一个库里、同一个分表里。路由时先解析 order_number 的基因位，
 * 如果没有就退化用 user-id 做分片键。然后对基因位做哈希和位运算，再按数据库数量和分表数量取模，最终定位到目标库和目标表，实现水平分库分表，
 * 保证热点用户不跨库跨表，查询和扩容都方便。
 * @description: 订单分库
 * @author: 阿星不是程序员
 **/
public class DatabaseOrderComplexGeneArithmetic implements ComplexKeysShardingAlgorithm<Long> {

    // 属性分库名
    private static final String SHARDING_COUNT_KEY_NAME = "sharding-count";
    // 属性分表名
    private static final String TABLE_SHARDING_COUNT_KEY_NAME = "table-sharding-count";
    // 分库数量
    private int shardingCount;
    // 分表数量
    private int tableShardingCount;

    /**
     * 从配置中读取数据库和表的分片数量，初始化成员变量
     *
     * @param props
     */
    @Override
    public void init(Properties props) {
        this.shardingCount = Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY_NAME));
        this.tableShardingCount = Integer.parseInt(props.getProperty(TABLE_SHARDING_COUNT_KEY_NAME));
    }

    /**
     * 根据传入的分片键值计算，决定要路由到哪些数据库
     *
     * @param allActualSplitDatabaseNames 所有实际可用的数据库名称列表
     * @param complexKeysShardingValue    拿到所有分片键对应的值集合，比如{"order_number": [123456], "user_id": [789]}
     * @return
     */
    @Override
    public Collection<String> doSharding
    (Collection<String> allActualSplitDatabaseNames, ComplexKeysShardingValue<Long> complexKeysShardingValue) {
        // 初始化结果列表，容量设为所有数据库个数，最后只返回匹配的一个或多个数据库名
        List<String> actualDatabaseNames = new ArrayList<>(allActualSplitDatabaseNames.size());

        // 获取所有分片键对应的值集合，比如order_number对应的所有值、user_id对应的所有值
        Map<String, Collection<Long>> columnNameAndShardingValuesMap =
                complexKeysShardingValue.getColumnNameAndShardingValuesMap();

        // 如果没有任何分片键值，默认返回所有数据库（不做分片）
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return allActualSplitDatabaseNames;
        }

        // 取出 order_number 和 user_id 对应的值集合
        Collection<Long> orderNumberValues = columnNameAndShardingValuesMap.get("order_number");
        Collection<Long> userIdValues = columnNameAndShardingValuesMap.get("user_id");

        Long value = null;
        // 优先使用order_number的第一个值作为分片依据，如果没有则用user_id的第一个值
        if (CollectionUtil.isNotEmpty(orderNumberValues)) {
            value = orderNumberValues.stream().findFirst().orElseThrow(
                    () -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
        } else if (CollectionUtil.isNotEmpty(userIdValues)) {
            value = userIdValues.stream().findFirst().orElseThrow(
                    () -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
        }
        // 如果分片键值存在
        if (Objects.nonNull(value)) {
            // 计算数据库索引
            long databaseIndex = calculateDatabaseIndex(shardingCount, value, tableShardingCount);
            String databaseIndexStr = String.valueOf(databaseIndex);
            // 遍历所有数据库名，找出名称包含该索引的数据库，比如 damai_order_0 包含 0
            for (String actualSplitDatabaseName : allActualSplitDatabaseNames) {
                if (actualSplitDatabaseName.contains(databaseIndexStr)) {
                    actualDatabaseNames.add(actualSplitDatabaseName);
                    break;
                }
            }
            // 返回目标数据库名集合
            return actualDatabaseNames;
        } else {
            // 如果没有找到有效的分片键值，返回所有数据库，表示不做分片限制
            return allActualSplitDatabaseNames;
        }
    }

    /**
     * 计算给定分片键应该路由到哪个数据库索引
     *
     * @param databaseCount 数据库总数
     * @param splicingKey   分片键
     * @param tableCount    表总数
     * @return 分配到的数据库编号
     */
    public long calculateDatabaseIndex(Integer databaseCount, Long splicingKey, Integer tableCount) {
        // 将分片值转换为二进制字符串
        String splicingKeyBinary = Long.toBinaryString(splicingKey);
        // 计算需要截取的二进制长度
        long replacementLength = log2N(tableCount);
        // 从分片键二进制末尾截取 replacementLength 位，作为“基因片段”
        String geneBinaryStr = splicingKeyBinary.substring(splicingKeyBinary.length() - (int) replacementLength);
        // 如果基因片段不为空
        if (StringUtil.isNotEmpty(geneBinaryStr)) {
            int h;
            // 对基因片段做hash，并用 hash 扰动，提升分布均匀度
            int geneOptimizeHashCode = (h = geneBinaryStr.hashCode()) ^ (h >>> 16);
            // 用 hashCode 与（数据库总数 - 1）做位与运算，确保落在[0, databaseCount - 1]区间
            return (databaseCount - 1) & geneOptimizeHashCode;
        }
        // 如果基因片段为空，抛异常
        throw new DaMaiFrameException(BaseCode.NOT_FOUND_GENE);
    }

    /**
     * 计算 log2(count)，即 以 2 为底的对数
     * 用于推导需要从二进制里截取多少位
     *
     * @param count 需要计算的值
     * @return log2(count) 的结果
     */
    public long log2N(long count) {
        return (long) (Math.log(count) / Math.log(2));
    }
}
