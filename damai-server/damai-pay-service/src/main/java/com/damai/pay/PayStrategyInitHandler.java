package com.damai.pay;

import com.damai.initialize.base.AbstractApplicationInitializingBeanHandler;
import lombok.AllArgsConstructor;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.Map.Entry;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 支付策略初始化
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class PayStrategyInitHandler extends AbstractApplicationInitializingBeanHandler {

    // 支付策略上下文，用于存储支付渠道与处理器的映射关系
    private final PayStrategyContext payStrategyContext;

    @Override
    public Integer executeOrder() {
        return 1;
    }

    /**
     * 应用启动时执行的初始化逻辑：注册所有支付策略处理器
     *
     * @param context Spring应用上下文，用于获取容器中的Bean
     */
    @Override
    public void executeInit(ConfigurableApplicationContext context) {
        // 1. 从Spring容器中获取所有实现了PayStrategyHandler接口的Bean（如AlipayStrategyHandler、WxPayStrategyHandler等）
        // 键：Bean的名称（由Spring自动生成）；值：支付策略处理器实例
        Map<String, PayStrategyHandler> payStrategyHandlerMap = context.getBeansOfType(PayStrategyHandler.class);
        // 2. 遍历所有支付策略处理器，将其注册到支付策略上下文
        for (Entry<String, PayStrategyHandler> entry : payStrategyHandlerMap.entrySet()) {
            // 具体的支付处理器（如支付宝处理器）
            PayStrategyHandler payStrategyHandler = entry.getValue();
            // 调用处理器的getChannel()方法获取支付渠道标识（如"alipay"、"wx"）
            // 将渠道标识与处理器的映射关系存入payStrategyContext
            payStrategyContext.put(payStrategyHandler.getChannel(), payStrategyHandler);
        }
    }
}
