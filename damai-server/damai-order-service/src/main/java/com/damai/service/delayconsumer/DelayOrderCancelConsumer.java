package com.damai.service.delayconsumer;

import com.alibaba.fastjson.JSON;
import com.damai.core.SpringUtil;
import com.damai.util.StringUtil;
import com.damai.core.ConsumerTask;
import com.damai.dto.DelayOrderCancelDto;
import com.damai.dto.OrderCancelDto;
import com.damai.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.damai.service.constant.OrderConstant.DELAY_ORDER_CANCEL_TOPIC;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 延迟订单取消消息的消费者组件
 * 负责监听延迟队列中的订单取消消息，在订单超时未支付时执行自动取消逻辑
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class DelayOrderCancelConsumer implements ConsumerTask {

    @Autowired
    private OrderService orderService;

    /**
     * 消息延迟订单取消消息的核心方法
     * 当延迟队列触发时，执行订单取消操作
     *
     * @param content 消息内容
     */
    @Override
    public void execute(String content) {
        log.info("延迟订单取消消息进行消费 content : {}", content);
        // 校验消息内容为空
        if (StringUtil.isEmpty(content)) {
            log.error("延迟队列消息不存在");
            return;
        }
        // 将JSON格式的消息内容转换为延迟订单取消DTO对象
        DelayOrderCancelDto delayOrderCancelDto = JSON.parseObject(content, DelayOrderCancelDto.class);
        // 构建订单取消参数
        OrderCancelDto orderCancelDto = new OrderCancelDto();
        orderCancelDto.setOrderNumber(delayOrderCancelDto.getOrderNumber());
        // 调用订单服务执行订单取消操作
        boolean cancel = orderService.cancel(orderCancelDto);
        if (cancel) {
            log.info("延迟订单取消成功 orderCancelDto : {}", content);
        } else {
            log.error("延迟订单取消失败 orderCancelDto : {}", content);
        }
    }

    /**
     * 定义当前消费者监听的消息主题
     *
     * @return
     */
    @Override
    public String topic() {
        return SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC;
    }
}
