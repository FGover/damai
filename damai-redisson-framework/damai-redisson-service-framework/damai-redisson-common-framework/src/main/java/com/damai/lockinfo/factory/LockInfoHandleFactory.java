package com.damai.lockinfo.factory;


import com.damai.lockinfo.LockInfoHandle;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 锁信息处理器工厂类
 * 负责通过Spring容器容器容器获取不同业务场景的锁信息处理器实例，实现"根据业务类型动态动态选择对应的锁信息生成逻辑"，解耦业务与具体实现
 * @author: 阿星不是程序员
 **/
public class LockInfoHandleFactory implements ApplicationContextAware {

    // Spring应用上下文，用于获取容器中的Bean实例
    private ApplicationContext applicationContext;

    /**
     * 实现ApplicationContextAware接口的方法，用于获取Spring应用上下文
     * 当该工厂类被Spring初始化时，会自动调用此方法注入ApplicationContext
     *
     * @param applicationContext Spring应用上下文对象
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 根据锁信息类型获取对应的锁信息处理器
     * 不同业务场景（如防重复执行、库存扣减）对应不同的LockInfoHandle实现类，通过Bean名称（即lockInfoType）从Spring容器中获取实例
     *
     * @param lockInfoType 锁信息类型（对应Spring容器中Bean的名称，如"REPEAT_EXECUTE_LIMIT"）
     * @return 对应的锁信息处理器实例（LockInfoHandle接口实现类）
     */
    public LockInfoHandle getLockInfoHandle(String lockInfoType) {
        // 从Spring容器中获取名称为lockInfoType、类型为LockInfoHandle的Bean
        return applicationContext.getBean(lockInfoType, LockInfoHandle.class);
    }

}
