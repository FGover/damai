package com.damai.service.tool;

import com.damai.vo.SeatVo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 座位自动匹配工具类
 * @author: 阿星不是程序员
 **/
public class SeatMatch {

    // 座位匹配策略枚举
    public enum SeatMatchStrategy {
        PRIORITY_CONTINUOUS, // 优先同排连续 → 其次同列连续 → 最后零散随机
        ROW_ONLY,            // 仅同排连续（无连续则失败）
        COLUMN_ONLY,         // 仅同列连续（无连续则失败）
        RANDOM_ONLY          // 直接随机（跳过连续检查）
    }

    /**
     * 多策略座位匹配入口方法
     *
     * @param allSeats  可用座位列表
     * @param seatCount 需要匹配的座位数量
     * @param strategy  匹配策略
     * @return 匹配到的座位列表，若未匹配到则返回空列表
     */
    public static List<SeatVo> matchSeats(List<SeatVo> allSeats, int seatCount, SeatMatchStrategy strategy) {
        // 基础校验：可用座位不足直接返回
        if (allSeats.size() < seatCount) {
            return new ArrayList<>();
        }
        // 根据策略执行不同匹配逻辑
        switch (strategy) {
            case PRIORITY_CONTINUOUS:
                // 1.优先找同排连续
                List<SeatVo> rowResult = findRowAdjacentSeats(allSeats, seatCount);
                if (!rowResult.isEmpty()) return rowResult;
                // 2.其次找同列连续
                List<SeatVo> columnResult = findColumnAdjacentSeats(allSeats, seatCount);
                if (!columnResult.isEmpty()) return columnResult;
                // 3.最后零散随机
                return randomAllocateSeats(allSeats, seatCount);
            case ROW_ONLY:
                return findRowAdjacentSeats(allSeats, seatCount);
            case COLUMN_ONLY:
                return findColumnAdjacentSeats(allSeats, seatCount);
            case RANDOM_ONLY:
                return randomAllocateSeats(allSeats, seatCount);
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 同排连续匹配座位
     *
     * @param allSeats
     * @param seatCount
     * @return
     */
    private static List<SeatVo> findRowAdjacentSeats(List<SeatVo> allSeats, int seatCount) {
        // 同排相邻排序
        List<SeatVo> sorted = sortByRowThenColumn(allSeats);
        for (int i = 0; i <= sorted.size() - seatCount; i++) {
            boolean isContinuous = true;
            for (int j = 0; j < seatCount - 1; j++) {
                SeatVo current = sorted.get(i + j);
                SeatVo next = sorted.get(i + j + 1);
                // 同排 + 列号 + 1
                if (!(Objects.equals(current.getRowCode(), next.getRowCode()) &&
                        next.getColCode() - current.getColCode() == 1)) {
                    isContinuous = false;
                    break;
                }
            }
            if (isContinuous) {
                return sorted.subList(i, i + seatCount);
            }
        }
        return new ArrayList<>();
    }

    /**
     * 同列连续分配座位
     *
     * @param allSeats
     * @param seatCount
     * @return
     */
    private static List<SeatVo> findColumnAdjacentSeats(List<SeatVo> allSeats, int seatCount) {
        // 同列相邻排序
        List<SeatVo> sorted = sortByColumnThenRow(allSeats);
        for (int i = 0; i <= sorted.size() - seatCount; i++) {
            boolean isContinuous = true;
            for (int j = 0; j < seatCount - 1; j++) {
                SeatVo current = sorted.get(i + j);
                SeatVo next = sorted.get(i + j + 1);
                // 同列 + 行号 + 1
                if (!(Objects.equals(current.getColCode(), next.getColCode()) &&
                        next.getRowCode() - current.getRowCode() == 1)) {
                    isContinuous = false;
                    break;
                }
            }
            if (isContinuous) {
                return sorted.subList(i, i + seatCount);
            }
        }
        return new ArrayList<>();
    }

    /**
     * 随机分配座位
     *
     * @param allSeats
     * @param seatCount
     * @return
     */
    private static List<SeatVo> randomAllocateSeats(List<SeatVo> allSeats, int seatCount) {
        // 深拷贝避免修改原列表
        List<SeatVo> seats = new ArrayList<>(allSeats);
        // 随机打乱
        Collections.shuffle(seats, new Random(System.currentTimeMillis()));
        // 取前seatCount个座位
        return seats.stream()
                .limit(seatCount)
                .collect(Collectors.toList());
    }

    /**
     * 按行号升序 -> 列号升序 排序（同排连续匹配前置条件）
     *
     * @param seats
     * @return
     */
    private static List<SeatVo> sortByRowThenColumn(List<SeatVo> seats) {
        return seats.stream().
                sorted(Comparator.comparing(SeatVo::getRowCode)
                        .thenComparing(SeatVo::getColCode))
                .collect(Collectors.toList());
    }

    /**
     * 按列号升序 -> 行号升序 排序（同列连续匹配前置条件）
     *
     * @param seats
     * @return
     */
    private static List<SeatVo> sortByColumnThenRow(List<SeatVo> seats) {
        return seats.stream().
                sorted(Comparator.comparing(SeatVo::getColCode)
                        .thenComparing(SeatVo::getRowCode))
                .collect(Collectors.toList());
    }
}
