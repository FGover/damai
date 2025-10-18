package com.damai.config;

import com.damai.MessageConsumer;
import com.damai.RedisStreamConfigProperties;
import com.damai.RedisStreamHandler;
import com.damai.RedisStreamListener;
import com.damai.RedisStreamPushHandler;
import com.damai.constant.RedisStreamConstant;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: redis-stream消息队列自动配置类
 * 负责初始化Redis Stream相关组件
 * @author: 阿星不是程序员
 **/
@Slf4j
@EnableConfigurationProperties(RedisStreamConfigProperties.class)
public class RedisStreamAutoConfig {

    /**
     * 初始化Redis Stream消息生产者处理器，用于向Redis Stream发送消息
     *
     * @param stringRedisTemplate         Redis操作模板
     * @param redisStreamConfigProperties Redis Stream配置属性（如流名称、消费组等）
     * @return RedisStreamPushHandler 消息推送处理器实例
     */
    @Bean
    public RedisStreamPushHandler redisStreamPushHandler(StringRedisTemplate stringRedisTemplate,
                                                         RedisStreamConfigProperties redisStreamConfigProperties) {
        return new RedisStreamPushHandler(stringRedisTemplate, redisStreamConfigProperties);
    }

    /**
     * 初始化Redis Stream核心处理器
     * 封装消息发送、消费组管理等核心逻辑
     *
     * @param redisStreamPushHandler 消息推送处理器
     * @param stringRedisTemplate    Redis操作模板
     * @return RedisStreamHandler 核心处理器实例
     */
    @Bean
    public RedisStreamHandler redisStreamHandler(RedisStreamPushHandler redisStreamPushHandler,
                                                 StringRedisTemplate stringRedisTemplate) {
        return new RedisStreamHandler(redisStreamPushHandler, stringRedisTemplate);
    }

    /**
     * 初始化Redis Stream消息监听容器，核心作用：绑定消费者与消息监听器，实现消息的自动接收和处理
     * 主要做的是将OrderStreamListener监听绑定消费者，用于接收消息
     *
     * @param redisConnectionFactory      Redis连接工厂，用于创建与Redis的连接
     * @param redisStreamConfigProperties Redis Stream配置属性
     * @param redisStreamHandler          Redis Stream核心处理器
     * @param messageConsumer             消息消费业务逻辑处理器（由业务方实现）
     * @return StreamMessageListenerContainer 消息监听容器实例
     */
    @Bean
    // 仅当项目中存在MessageConsumer实例时才会创建（@ConditionalOnBean条件）
    @ConditionalOnBean(MessageConsumer.class)
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisStreamConfigProperties redisStreamConfigProperties,
            RedisStreamHandler redisStreamHandler,
            MessageConsumer messageConsumer) {
        // 1.配置消息监听容器选项
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>>
                options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(5))  // 消息拉取超时时间：5秒未拉取到消息则重试
                .batchSize(10)  // 每次批量拉取的消息数量上限
                .targetType(String.class)  // 消息体的目标类型
                .errorHandler(t -> log.error("出现异常", t))   // 全局异常处理器
                .executor(createThreadPool())   // 指定处理消息的线程池
                .build();
        // 2.创建监听容器实例
        StreamMessageListenerContainer<String, ObjectRecord<String, String>> container =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);
        // 3.校验消费类型
        checkConsumerType(redisStreamConfigProperties.getConsumerType());
        // 4.创建消息监听器（绑定业务消费逻辑）
        RedisStreamListener redisStreamListener = new RedisStreamListener(messageConsumer);
        // 5.根据消费类型配置消息监听模式
        if (RedisStreamConstant.GROUP.equals(redisStreamConfigProperties.getConsumerType())) {
            // 5.1.消费组模式：创建消费组（若不存在），并从消费组游标位置开始消费
            redisStreamHandler.streamBindingGroup(
                    redisStreamConfigProperties.getStreamName(),   // 流名称
                    redisStreamConfigProperties.getConsumerGroup()   // 消费组名称
            );
            // 绑定消费组和消费者，使用自动确认机制（消息处理后自动ACK）
            container.receiveAutoAck(
                    // 定义消费者（属于指定消费组）
                    Consumer.from(
                            redisStreamConfigProperties.getConsumerGroup(),  // 消费组名称
                            redisStreamConfigProperties.getConsumerName()  // 消费者名称
                    ),
                    // 从消费组的最后消费位置继续消费（lastConsumed）
                    StreamOffset.create(redisStreamConfigProperties.getStreamName(), ReadOffset.lastConsumed()),
                    redisStreamListener   // 消息监听器（处理消息）
            );
        } else {
            // 5.2.广播模式：从流的起始位置开始消费（所有消费者都能收到全量消息）
            container.receive(
                    StreamOffset.fromStart(redisStreamConfigProperties.getStreamName()),  // 从第一条消息开始消费
                    redisStreamListener   // 消息监听器
            );
        }
        // 6.启动消息监听容器
        container.start();
        return container;
    }

    /**
     * 创建消息处理专用线程池
     * 用于异步处理接收到的Redis Stream消息
     *
     * @return ThreadPoolExecutor 线程池实例
     */
    public ThreadPoolExecutor createThreadPool() {
        // 核心线程数：CPU核心数（根据服务器性能动态调整）
        int coreThreadCount = Runtime.getRuntime().availableProcessors();
        // 线程名计数器
        AtomicInteger threadCount = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                coreThreadCount,   // 核心线程数
                2 * coreThreadCount,  // 最大线程数（核心线程数的2倍）
                30,   // 空闲线程存活时间（30秒）
                TimeUnit.SECONDS,  // 时间单位
                new ArrayBlockingQueue<>(100),  // 任务队列（容量100）
                // 线程工厂的Lambda实现
                r -> {
                    Thread thread = new Thread(r);  // 创建线程，传入任务（Runnable）
                    thread.setName("thread-consumer-stream-task-" + threadCount.getAndIncrement());  // 自定义线程名称
                    return thread;  // 返回创建的线程
                });
    }

    /**
     * 校验消费类型，仅允许"GROUP"（消费组模式）和"BROADCAST"（广播模式）
     *
     * @param consumerType 配置文件中的消费类型
     */
    public void checkConsumerType(String consumerType) {
        if ((!RedisStreamConstant.GROUP.equals(consumerType)) && (!RedisStreamConstant.BROADCAST.equals(consumerType))) {
            throw new DaMaiFrameException(BaseCode.REDIS_STREAM_CONSUMER_TYPE_NOT_EXIST);
        }
    }
}
