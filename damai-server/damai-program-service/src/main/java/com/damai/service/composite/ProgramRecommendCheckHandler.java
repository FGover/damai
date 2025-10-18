package com.damai.service.composite;

import com.damai.dto.ProgramRecommendListDto;
import com.damai.enums.BaseCode;
import com.damai.enums.CompositeCheckType;
import com.damai.exception.DaMaiFrameException;
import com.damai.initialize.impl.composite.AbstractComposite;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目推荐参数验证处理器
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramRecommendCheckHandler extends AbstractComposite<ProgramRecommendListDto> {

    /**
     * 执行参数验证逻辑
     * 校验节目推荐请求的必要参数，确保至少包含一个查询条件
     *
     * @param param 节目推荐列表查询参数对象（包含区域ID、父分类ID、节目ID等查询条件）
     */
    @Override
    protected void execute(final ProgramRecommendListDto param) {
        if (Objects.isNull(param.getAreaId()) &&
                Objects.isNull(param.getParentProgramCategoryId()) &&
                Objects.isNull(param.getProgramId())) {
            throw new DaMaiFrameException(BaseCode.PARAMETERS_CANNOT_BE_EMPTY);
        }
    }

    @Override
    public String type() {
        return CompositeCheckType.PROGRAM_RECOMMEND_CHECK.getValue();
    }

    @Override
    public Integer executeParentOrder() {
        return 0;
    }

    @Override
    public Integer executeTier() {
        return 1;
    }

    @Override
    public Integer executeOrder() {
        return 1;
    }
}
