package com.damai.shardingsphere;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
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
 * @description: 订单分表
 * @author: 阿星不是程序员
 **/
public class TableOrderComplexGeneArithmetic implements ComplexKeysShardingAlgorithm<Long> {

    // 属性分表名
    private static final String SHARDING_COUNT_KEY_NAME = "sharding-count";
    // 分表数量
    private int shardingCount;

    /**
     * 从配置中读取分表数量
     *
     * @param props
     */
    @Override
    public void init(Properties props) {
        shardingCount = Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY_NAME));
    }

    /**
     * 分表路由
     *
     * @param allActualSplitTableNames 所有可用的实际分表名
     * @param complexKeysShardingValue 分片键的值（这里可能包含 order_number 和 user_id）
     * @return 最终要路由的目标表名
     */
    @Override
    public Collection<String> doSharding(Collection<String> allActualSplitTableNames,
                                         ComplexKeysShardingValue<Long> complexKeysShardingValue) {
        // 用于存放计算后的目标分表名
        List<String> actualTableNames = new ArrayList<>(allActualSplitTableNames.size());
        // 获取逻辑表名 d_order
        String logicTableName = complexKeysShardingValue.getLogicTableName();
        // 拿到分片键值map
        Map<String, Collection<Long>> columnNameAndShardingValuesMap =
                complexKeysShardingValue.getColumnNameAndShardingValuesMap();
        // 如果没有，默认路由到所有表
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return allActualSplitTableNames;
        }
        // 获取 order_number 和 user_id 分片键值
        Collection<Long> orderNumberValues = columnNameAndShardingValuesMap.get("order_number");
        Collection<Long> userIdValues = columnNameAndShardingValuesMap.get("user_id");
        Long value = null;
        // 优先使用 order_number
        if (CollectionUtil.isNotEmpty(orderNumberValues)) {
            value = orderNumberValues.stream()
                    .findFirst()
                    .orElseThrow(() -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
        }
        // 如果 order_number 不存在，再使用 user_id
        else if (CollectionUtil.isNotEmpty(userIdValues)) {
            value = userIdValues.stream()
                    .findFirst()
                    .orElseThrow(() -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
        }
        // 根据分片键值计算分表索引，定位到具体分表
        if (Objects.nonNull(value)) {
            // 位运算(value & (shardingCount - 1))，相当于对 shardingCount 取模，保证索引范围 [0, shardingCount - 1]
            int tableIndex = (shardingCount - 1) & value.intValue();
            // 拼接逻辑表名 + "_" + 计算出的索引，得到实际分表名，如 d_order_2
            actualTableNames.add(logicTableName + "_" + tableIndex);
            return actualTableNames;
        }
        // 如果分片键值为空，则返回所有分表，路由不确定
        return allActualSplitTableNames;
    }
}
