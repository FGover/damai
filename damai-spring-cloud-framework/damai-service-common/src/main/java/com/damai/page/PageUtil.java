package com.damai.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.damai.dto.BasePageDto;
import com.github.pagehelper.PageInfo;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 分页工具类
 * 提供分页参数构建、分页结果转换等功能，适配MyBatis-Plus的IPage和PageHelper的PageInfo
 * @author: 阿星不是程序员
 **/
public class PageUtil {

    /**
     * 根据基础分页DTO构建MyBatis-Plus的IPage分页参数
     * 从BasePageDto中提取页码和页大小，创建MyBatis-Plus的分页查询
     *
     * @param basePageDto
     * @param <T>
     * @return
     */
    public static <T> IPage<T> getPageParams(BasePageDto basePageDto) {
        return getPageParams(basePageDto.getPageNumber(), basePageDto.getPageSize());
    }

    /**
     * 根据页码和页大小构建MyBatis-Plus的IPage分页参数
     * 直接使用传入的页码和页大小创建分页对象，用于MyBatis-Plus的分页查询
     *
     * @param pageNumber 页码（从1开始）
     * @param pageSize   每页条数
     * @param <T>        分页数据的实体类型
     * @return IPage<T> MyBatis-Plus的分页参数对象
     */
    public static <T> IPage<T> getPageParams(int pageNumber, int pageSize) {
        return new Page<>(pageNumber, pageSize);
    }

    /**
     * 将PageHelper的PageInfo分页结果转换为自定义的PageVo
     * 支持通过Function函数将原始数据类型（OLD）转换为目标视图类型（NEW）
     *
     * @param pageInfo PageHelper的分页结果对象，包含原始数据列表和分页信息
     * @param function 数据转换函数，用于将OLD类型转换为NEW类型
     * @param <OLD>    原始数据类型
     * @param <NEW>    目标视图类型
     * @return PageVo<NEW> 自定义的分页视图对象，包含转换后的数据和分页信息
     */
    public static <OLD, NEW> PageVo<NEW> convertPage(PageInfo<OLD> pageInfo,
                                                     Function<? super OLD, ? extends NEW> function) {
        return new PageVo<>(
                pageInfo.getPageNum(),  // 当前页码
                pageInfo.getPageSize(),  // 每页条数
                pageInfo.getTotal(),   // 总记录数
                // 将原始数据列表转换为目标视图列表
                pageInfo.getList().stream().map(function).collect(Collectors.toList()));
    }

    /**
     * 将MyBatis-Plus的IPage分页结果转换为自定义的PageVo
     * 支持通过Function函数将原始数据类型（OLD）转换为目标视图类型（NEW）
     *
     * @param iPage    MyBatis-Plus的分页结果对象，包含原始数据列表和分页信息
     * @param function 数据转换函数，用于将OLD类型转换为NEW类型
     * @param <OLD>    原始数据类型
     * @param <NEW>    目标视图类型
     * @return PageVo<NEW> 自定义的分页视图对象，包含转换后的数据和分页信息
     */
    public static <OLD, NEW> PageVo<NEW> convertPage(IPage<OLD> iPage,
                                                     Function<? super OLD, ? extends NEW> function) {
        return new PageVo<>(
                iPage.getCurrent(),  // 当前页码
                iPage.getSize(),     // 每页条数
                iPage.getTotal(),    // 总记录数
                // 将原始数据列表转换为目标视图列表
                iPage.getRecords().stream().map(function).collect(Collectors.toList()));
    }
}
