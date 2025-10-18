package com.damai.redis;


import com.damai.core.RedisKeyManage;
import com.damai.core.SpringUtil;
import lombok.Getter;

import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: redis key包装
 * @author: 阿星不是程序员
 **/
@Getter
public final class RedisKeyBuild {
    /**
     * 封装后的真实 Redis Key，包含前缀和格式化后的业务key
     */
    private final String relKey;

    /**
     * 私有构造方法，防止外部直接实例化，
     * 只能通过静态方法构造RedisKeyBuild对象
     *
     * @param relKey 实际使用的完整Redis Key
     */
    private RedisKeyBuild(String relKey) {
        this.relKey = relKey;
    }

    /**
     * 基于枚举和参数构建真实的Redis Key
     *
     * @param redisKeyManage key的枚举
     * @param args           占位符的值
     */
    public static RedisKeyBuild createRedisKey(RedisKeyManage redisKeyManage, Object... args) {
        // 通过 String.format 将模板 Key 中的占位符替换成具体参数
        String redisRelKey = String.format(redisKeyManage.getKey(), args);
        // 加上全局前缀，保证不同服务或环境的 Key 不会冲突
        return new RedisKeyBuild(SpringUtil.getPrefixDistinctionName() + "-" + redisRelKey);
    }

    /**
     * 获取带前缀的 Redis Key 字符串（不支持格式化参数）
     *
     * @param redisKeyManage
     * @return
     */
    public static String getRedisKey(RedisKeyManage redisKeyManage) {
        return SpringUtil.getPrefixDistinctionName() + "-" + redisKeyManage.getKey();
    }

    /**
     * 重写 equals 方法，基于relKey判断两个RedisKeyBuild对象是否相等
     *
     * @param o 另一个对象
     * @return true表示两个对象的relKey相等
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RedisKeyBuild that = (RedisKeyBuild) o;
        return relKey.equals(that.relKey);
    }

    /**
     * 重写 hashCode 方法，基于relKey计算对象的哈希值
     *
     * @return
     */
    @Override
    public int hashCode() {
        return Objects.hash(relKey);
    }
}
