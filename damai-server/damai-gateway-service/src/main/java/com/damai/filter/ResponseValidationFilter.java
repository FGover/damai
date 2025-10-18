package com.damai.filter;

import com.alibaba.fastjson.JSON;
import com.damai.common.ApiResponse;
import com.damai.util.StringUtil;
import com.damai.service.ChannelDataService;
import com.damai.util.RsaTool;
import com.damai.vo.GetChannelDataVo;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.function.BiFunction;

import static com.damai.constant.GatewayConstant.CODE;
import static com.damai.constant.GatewayConstant.ENCRYPT;
import static com.damai.constant.GatewayConstant.NO_VERIFY;
import static com.damai.constant.GatewayConstant.V2;
import static com.damai.constant.GatewayConstant.VERIFY_VALUE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;


/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 返回过滤器 参考 {@link org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory}
 * 响应验证过滤器（全局过滤器）
 * 用于对网关返回的响应体进行处理，根据请求头信息对业务数据进行条件性加密，确保敏感数据在传输过程中的安全性，适配多渠道的加密需求
 * @author: 阿星不是程序员
 **/
@Component
@Slf4j
public class ResponseValidationFilter implements GlobalFilter, Ordered {

    /**
     * AES加密的向量（从配置文件读取，默认值为"default"
     */
    @Value("${aes.vector:default}")
    private String aesVector;

    /**
     * 渠道数据服务，用于根据渠道编码查询加密所需的公钥等信息
     */
    @Autowired
    private ChannelDataService channelDataService;

    /**
     * 指定过滤器的执行顺序（Ordered接口方法）
     * 返回值越小，过滤器执行越早（-2确保在响应体处理相关过滤器之前执行）
     *
     * @return 执行顺序值（-2）
     */
    @Override
    public int getOrder() {
        return -2;
    }

    /**
     * 执行过滤逻辑（GlobalFilter接口方法）
     * 对响应进行装饰，实现响应体的拦截和修改
     *
     * @param exchange 服务器Web交换对象，包含请求和响应的上下文
     * @param chain    过滤器链，用于执行后续过滤器
     * @return 表示过滤操作完成的Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 装饰响应对象，替换为自定义的ServerHttpResponseDecorator，实现响应体修改
        return chain.filter(exchange.mutate().response(decorate(exchange)).build());
    }

    /**
     * 装饰原始响应对象，实现响应体的拦截和处理
     *
     * @param exchange 服务器Web交换对象
     * @return 装饰后的ServerHttpResponse，包含响应体修改逻辑
     */
    private ServerHttpResponse decorate(ServerWebExchange exchange) {
        // 继承ServerHttpResponseDecorator，重写响应体写入方法
        return new ServerHttpResponseDecorator(exchange.getResponse()) {
            /**
             * 重写响应体写入方法，拦截原始响应体并进行修改
             * @param body 原始响应体的数据流（DataBuffer publisher）
             * @return 表示响应体写入完成的Mono<Void>
             */
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // 获取原始响应的Content-Type（如application/json）
                String originalResponseContentType = exchange
                        .getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
                // 构建新的HTTP头，保持原始响应的Content-Type
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.add(HttpHeaders.CONTENT_TYPE,
                        originalResponseContentType);
                // 将原始响应体包装为ClientResponse，便于解析为字符串
                ClientResponse clientResponse = ClientResponse
                        .create(Objects.requireNonNull(exchange.getResponse().getStatusCode())) // 响应状态码
                        .headers(headers -> headers.putAll(httpHeaders))  // 响应头
                        .body(Flux.from(body))  // 响应体数据流
                        .build();
                // 将原始响应体解析为字符串，并执行修改逻辑（加密处理）
                Mono<String> modifiedBody = clientResponse
                        .bodyToMono(String.class)  // 转换为字符串
                        .flatMap(originalBody -> modifyResponseBody().apply(exchange, originalBody)); // 调用修改方法
                // 创建响应体插入器，将修改后的响应体写入输出流
                BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters
                        .fromPublisher(modifiedBody, String.class);
                // 缓存输出消息，用于暂存修改后的响应体
                CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(
                        exchange, exchange.getResponse().getHeaders());
                // 执行响应体插入，并将结果写入原始响应
                return bodyInserter.insert(outputMessage, new BodyInserterContext())
                        .then(Mono.defer(() -> {
                            // 获取修改后的响应体数据流
                            Flux<DataBuffer> messageBody = outputMessage.getBody();
                            HttpHeaders headers = getDelegate().getHeaders();
                            // 若响应头中没有Transfer-Encoding，设置Content-Length
                            if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
                                messageBody = messageBody.doOnNext(data -> headers
                                        .setContentLength(data.readableByteCount()));
                            }
                            // 将修改后的响应体写入原始响应
                            return getDelegate().writeWith(messageBody);
                        }));
            }

            /**
             * 定义响应体修改的函数（BiFunction：接收交换对象和原始响应体，返回修改后的响应体）
             * @return 响应体修改函数
             */
            private BiFunction<ServerWebExchange, String, Mono<String>> modifyResponseBody() {
                return (serverWebExchange, responseBody) -> {
                    // 调用checkResponseBody方法处理响应体，返回修改后的结果
                    String modifyResponseBody = checkResponseBody(serverWebExchange, responseBody);
                    // 包装为Mono返回
                    return Mono.just(modifyResponseBody);
                };
            }

            /**
             * 重写批量写入并刷新响应体的方法，适配响应体修改逻辑
             * @param body 响应体数据流（嵌套Publisher）
             * @return 表示操作完成的Mono<Void>
             */
            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                // 扁平化为单层数据流，调用writeWith处理
                return writeWith(Flux.from(body).flatMapSequential(p -> p));
            }
        };
    }

    /**
     * 检查并修改响应体（核心逻辑：根据请求头判断是否对业务数据加密）
     *
     * @param serverWebExchange 服务器Web交换对象
     * @param responseBody      原始响应体字符串
     * @return 修改后的响应体字符串（加密后或原始数据）
     */
    private String checkResponseBody(final ServerWebExchange serverWebExchange, final String responseBody) {
        // 初始化修改后的响应体（默认使用原始响应体）
        String modifyResponseBody = responseBody;
        // 获取当前请求对象
        ServerHttpRequest request = serverWebExchange.getRequest();
        // 从请求头获取"是否跳过验证"标识（控制是否加密）
        String noVerify = request.getHeaders().getFirst(NO_VERIFY);
        // 从请求头获取加密版本（控制加密规则，如"v2"）
        String encrypt = request.getHeaders().getFirst(ENCRYPT);
        // 加密条件：未跳过验证 + 加密版本为v2 + 响应体非空
        if ((!VERIFY_VALUE.equals(noVerify)) && V2.equals(encrypt) && StringUtil.isNotEmpty(responseBody)) {
            // 将原始响应体解析为统一的ApiResponse对象（格式：{code, msg, data}）
            ApiResponse apiResponse = JSON.parseObject(responseBody, ApiResponse.class);
            // 提取响应中的业务数据（如用户信息、订单详情等敏感数据）
            Object data = apiResponse.getData();
            // 若业务数据不为空，则执行加密
            if (data != null) {
                // 从请求头获取渠道编码（标识请求来源的渠道，如合作方A、合作方B）
                String code = request.getHeaders().getFirst(CODE);
                // 根据渠道编码查询该渠道的加密参数（如公钥）
                GetChannelDataVo channelDataVo = channelDataService.getChannelDataByCode(code);
                // 使用渠道公钥对业务数据进行RSA加密（确保只有渠道方的私钥可解密）
                String rsaEncrypt = RsaTool.encrypt(JSON.toJSONString(data), channelDataVo.getDataPublicKey());
                // 替换响应体中的业务数据为加密后的字符串
                apiResponse.setData(rsaEncrypt);
                // 将修改后的ApiResponse转换为JSON字符串，作为最终响应体
                modifyResponseBody = JSON.toJSONString(apiResponse);
            }
        }
        return modifyResponseBody;
    }
}
