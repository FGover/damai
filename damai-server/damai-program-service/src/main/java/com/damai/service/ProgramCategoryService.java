package com.damai.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RedisKeyManage;
import com.damai.dto.ParentProgramCategoryDto;
import com.damai.dto.ProgramCategoryAddDto;
import com.damai.dto.ProgramCategoryDto;
import com.damai.entity.ProgramCategory;
import com.damai.mapper.ProgramCategoryMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.vo.ProgramCategoryVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.damai.core.DistributedLockConstants.PROGRAM_CATEGORY_LOCK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目类型 service
 * @author: 阿星不是程序员
 **/
@Service
public class ProgramCategoryService extends ServiceImpl<ProgramCategoryMapper, ProgramCategory> {

    @Autowired
    private ProgramCategoryMapper programCategoryMapper;

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private RedisCache redisCache;

    /**
     * 查询所有节目类型
     */
    public List<ProgramCategoryVo> selectAll() {
        QueryWrapper<ProgramCategory> lambdaQueryWrapper = Wrappers.emptyWrapper();
        List<ProgramCategory> programCategoryList = programCategoryMapper.selectList(lambdaQueryWrapper);
        return BeanUtil.copyToList(programCategoryList, ProgramCategoryVo.class);
    }

    public List<ProgramCategoryVo> selectByType(ProgramCategoryDto programCategoryDto) {
        LambdaQueryWrapper<ProgramCategory> lambdaQueryWrapper = Wrappers.lambdaQuery(ProgramCategory.class)
                .eq(ProgramCategory::getType, programCategoryDto.getType());
        List<ProgramCategory> programCategories = programCategoryMapper.selectList(lambdaQueryWrapper);
        return BeanUtil.copyToList(programCategories, ProgramCategoryVo.class);
    }

    public List<ProgramCategoryVo> selectByParentProgramCategoryId(ParentProgramCategoryDto parentProgramCategoryDto) {
        LambdaQueryWrapper<ProgramCategory> lambdaQueryWrapper = Wrappers.lambdaQuery(ProgramCategory.class)
                .eq(ProgramCategory::getParentId, parentProgramCategoryDto.getParentProgramCategoryId());
        List<ProgramCategory> programCategories = programCategoryMapper.selectList(lambdaQueryWrapper);
        return BeanUtil.copyToList(programCategories, ProgramCategoryVo.class);
    }

    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(lockType = LockType.Write, name = PROGRAM_CATEGORY_LOCK, keys = {"all"})
    public void saveBatch(final List<ProgramCategoryAddDto> programCategoryAddDtoList) {
        List<ProgramCategory> programCategoryList = programCategoryAddDtoList.stream().map((programCategoryAddDto) -> {
            ProgramCategory programCategory = new ProgramCategory();
            BeanUtil.copyProperties(programCategoryAddDto, programCategory);
            programCategory.setId(uidGenerator.getUid());
            return programCategory;
        }).collect(Collectors.toList());

        if (CollectionUtil.isNotEmpty(programCategoryList)) {
            this.saveBatch(programCategoryList);
            Map<String, ProgramCategory> programCategoryMap = programCategoryList.stream().collect(
                    Collectors.toMap(p -> String.valueOf(p.getId()), p -> p, (v1, v2) -> v2));
            redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_CATEGORY_HASH), programCategoryMap);
        }

    }

    /**
     * 获取节目分类信息
     *
     * @param programCategoryId 节目分类ID，用于查询对应的分类信息
     * @return 节目分类对象（ProgramCategory），若不存在则返回null
     */
    public ProgramCategory getProgramCategory(Long programCategoryId) {
        // 优先从Redis缓存获取分类信息
        ProgramCategory programCategory = redisCache.getForHash(RedisKeyBuild.createRedisKey(
                RedisKeyManage.PROGRAM_CATEGORY_HASH), String.valueOf(programCategoryId), ProgramCategory.class);
        // 缓存未命中，从数据库加载并更新缓存
        if (Objects.isNull(programCategory)) {
            // 调用初始化方法从数据库加载所有节目分类并更新到Redis缓存
            // 该方法会返回完整的分类映射表（ID为键，分类对象为值）
            Map<String, ProgramCategory> programCategoryMap = programCategoryRedisDataInit();
            // 从完整映射表中获取当前所需的分类信息
            return programCategoryMap.get(String.valueOf(programCategoryId));
        }
        // 缓存命中，直接返回
        return programCategory;
    }

    /**
     * 初始化节目分类Redis缓存数据
     * 使用写锁保证缓存初始化过程的线程安全，防止并发场景下的数据不一致
     *
     * @return 初始化后的节目分类映射表（ID为键，节目分类对象为值）
     */
    @ServiceLock(lockType = LockType.Write, name = PROGRAM_CATEGORY_LOCK, keys = {"#all"})
    public Map<String, ProgramCategory> programCategoryRedisDataInit() {
        // 初始化哈希表用于存储节目分类数据
        Map<String, ProgramCategory> programCategoryMap = new HashMap<>(64);
        // 创建空查询条件（查询所有节目分类数据）
        QueryWrapper<ProgramCategory> lambdaQueryWrapper = Wrappers.emptyWrapper();
        // 从数据库查询所有节目分类记录
        List<ProgramCategory> programCategoryList = programCategoryMapper.selectList(lambdaQueryWrapper);
        // 若查询结果不为空，则进行数据处理和缓存
        if (CollectionUtil.isNotEmpty(programCategoryList)) {
            // 将节目分类列表转换为哈希表
            programCategoryMap = programCategoryList.stream().collect(
                    Collectors.toMap(
                            p -> String.valueOf(p.getId()),  // 键：节目分类ID的字符串形式
                            p -> p,   // 值：节目分类对象
                            (v1, v2) -> v2  // 冲突解决：当两个不同的节目分类对象映射到同一个键时，保留后出现的值
                    )
            );
            // 将哈希表数据存入redis缓存
            redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_CATEGORY_HASH), programCategoryMap);
        }
        // 返回初始化后的节目分类映射表
        return programCategoryMap;
    }
}
