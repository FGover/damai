package com.damai.filter;


import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.conf.RequestTemporaryWrapper;
import com.damai.enums.BaseCode;
import com.damai.exception.ArgumentError;
import com.damai.exception.ArgumentException;
import com.damai.exception.DaMaiFrameException;
import com.damai.pro.limit.RateLimiter;
import com.damai.pro.limit.RateLimiterProperty;
import com.damai.property.GatewayProperty;
import com.damai.service.ApiRestrictService;
import com.damai.service.ChannelDataService;
import com.damai.service.TokenService;
import com.damai.threadlocal.BaseParameterHolder;
import com.damai.util.RsaSignTool;
import com.damai.util.RsaTool;
import com.damai.util.StringUtil;
import com.damai.vo.GetChannelDataVo;
import com.damai.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.damai.constant.Constant.GRAY_PARAMETER;
import static com.damai.constant.Constant.TRACE_ID;
import static com.damai.constant.GatewayConstant.BUSINESS_BODY;
import static com.damai.constant.GatewayConstant.CODE;
import static com.damai.constant.GatewayConstant.ENCRYPT;
import static com.damai.constant.GatewayConstant.NO_VERIFY;
import static com.damai.constant.GatewayConstant.REQUEST_BODY;
import static com.damai.constant.GatewayConstant.TOKEN;
import static com.damai.constant.GatewayConstant.USER_ID;
import static com.damai.constant.GatewayConstant.V2;
import static com.damai.constant.GatewayConstant.VERIFY_VALUE;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 请求全局过滤器
 * @author: 阿星不是程序员
 **/

/**
 * 请求校验过滤器（全局过滤器）
 * 主要负责对所有进入网关的请求做统一的参数校验、签名验签、限流和用户身份提取等操作。
 * 流程：先判断是否开启限流，如果是则通过限流器获取令牌，未获取到会阻塞；然后生成或传递traceId和灰度参数，放到日志上下文和ThreadLocal中；
 * 如果是json请求，就先读取请求体，执行签名验签和参数合法性校验，把校验结果和解析后的body重新封装到请求里，再继续传递给后续过滤器；
 * 如果是非json请求，则直接做必要的header和参数处理后放行。
 */
@Component
@Slf4j
public class RequestValidationFilter implements GlobalFilter, Ordered {

    @Autowired
    private ServerCodecConfigurer serverCodecConfigurer;  // 用于解析请求体的编解码器

    @Autowired
    private ChannelDataService channelDataService;  // 渠道信息服务，用于获取渠道密钥等

    @Autowired
    private ApiRestrictService apiRestrictService;  // API 限制服务，做接口访问限制

    @Autowired
    private TokenService tokenService;  // Token 服务，用于验证用户身份

    @Autowired
    private GatewayProperty gatewayProperty;  // 网关相关配置

    @Autowired
    private UidGenerator uidGenerator;  // UID 生成器，用于生成 traceId

    @Autowired
    private RateLimiterProperty rateLimiterProperty; // 限流相关配置

    @Autowired
    private RateLimiter rateLimiter;  // 限流器

    /**
     * 全局过滤入口
     * 先根据配置看是否启用限流，如果启用，则先尝试获取令牌，否则直接执行后续逻辑
     *
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        if (rateLimiterProperty.getRateSwitch()) {
            try {
                rateLimiter.acquire();
                return doFilter(exchange, chain);  // 获取令牌，未获取到阻塞
            } catch (InterruptedException e) {
                log.error("interrupted error", e);
                throw new DaMaiFrameException(BaseCode.THREAD_INTERRUPTED);
            } finally {
                rateLimiter.release();  // 请求处理完释放令牌
            }
        } else {
            return doFilter(exchange, chain);
        }
    }

    /**
     * 核心过滤逻辑
     *
     * @param exchange 用于获取 HTTP 请求和响应的信息，以及修改它们
     * @param chain    用于将请求传递给下一个过滤器，或者直接将响应返回给客户端
     * @return
     */
    public Mono<Void> doFilter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 链路id
        String traceId = request.getHeaders().getFirst(TRACE_ID);
        // 灰度标识
        String gray = request.getHeaders().getFirst(GRAY_PARAMETER);
        // 是否验证参数
        String noVerify = request.getHeaders().getFirst(NO_VERIFY);
        // 如果链路id不存在，那么在这里生成
        if (StringUtil.isEmpty(traceId)) {
            traceId = String.valueOf(uidGenerator.getUid());
        }
        // 将链路id放到日志的MDC中便于日志输出
        MDC.put(TRACE_ID, traceId);
        Map<String, String> headMap = new HashMap<>(8);
        headMap.put(TRACE_ID, traceId);
        headMap.put(GRAY_PARAMETER, gray);
        if (StringUtil.isNotEmpty(noVerify)) {
            headMap.put(NO_VERIFY, noVerify);
        }
        // 将链路id放到 ThreadLocal 中
        BaseParameterHolder.setParameter(TRACE_ID, traceId);
        // 将灰度标识放到 ThreadLocal 中
        BaseParameterHolder.setParameter(GRAY_PARAMETER, gray);
        // 获取请求类型
        MediaType contentType = request.getHeaders().getContentType();
        //application json请求
        if (Objects.nonNull(contentType) && contentType.toString().toLowerCase()
                .contains(MediaType.APPLICATION_JSON_VALUE.toLowerCase())) {
            // 如果是json，则读取body做签名验证和解析
            return readBody(exchange, chain, headMap);
        } else {
            // 如果非json，则直接执行后续逻辑
            Map<String, String> map = doExecute("", exchange);
            map.remove(REQUEST_BODY);
            map.putAll(headMap);
            request.mutate().headers(httpHeaders -> {
                map.forEach(httpHeaders::add);
            });
            return chain.filter(exchange);
        }
    }

    /**
     * 读取请求体，并进行签名验证和解析
     *
     * @param exchange
     * @param chain
     * @param headMap
     * @return
     */
    private Mono<Void> readBody(ServerWebExchange exchange, GatewayFilterChain chain, Map<String, String> headMap) {
        log.info("current thread readBody : {}", Thread.currentThread().getName());
        RequestTemporaryWrapper requestTemporaryWrapper = new RequestTemporaryWrapper();
        // 将请求封装成ServerRequest，便于使用 reactor 式 API 获取 Body
        ServerRequest serverRequest = ServerRequest.create(exchange, serverCodecConfigurer.getReaders());
        // 从请求中解析body，然后执行签名校验和解析
        Mono<String> modifiedBody = serverRequest
                .bodyToMono(String.class)
                // execute 是执行参数验证的方法
                .flatMap(originalBody -> Mono.just(execute(requestTemporaryWrapper, originalBody, exchange)))
                // 这个方法是使用post请求，方式也是json，但请求体为空的情况
                .switchIfEmpty(Mono.defer(() -> Mono.just(execute(requestTemporaryWrapper, "", exchange))));
        // 将修改后的body重新封装成ServerHttpRequest
        BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(exchange.getRequest().getHeaders());
        headers.remove(HttpHeaders.CONTENT_LENGTH);  // 移除 Content-Length，让框架重新计算
        // 新建一个 CachedBodyOutputMessage：缓存新 body 的输出流
        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
        // 插入新 body，相当于把新 body 写到临时缓存里
        return bodyInserter
                .insert(outputMessage, new BodyInserterContext())
                .then(Mono.defer(() -> chain.filter(
                        exchange.mutate().request(decorateHead(exchange, headers, outputMessage, requestTemporaryWrapper, headMap)).build()
                )))
                .onErrorResume((Function<Throwable, Mono<Void>>) throwable -> Mono.error(throwable));
    }

    /**
     * 执行业务校验、签名校验，返回解析后的Body
     *
     * @param requestTemporaryWrapper
     * @param requestBody
     * @param exchange
     * @return
     */
    public String execute(RequestTemporaryWrapper requestTemporaryWrapper, String requestBody, ServerWebExchange exchange) {
        //进行业务验证，并将相关参数放入map
        Map<String, String> map = doExecute(requestBody, exchange);
        //这里的map中的数据在doExecute中放入的，有修改后的请求体和要放在请求头中的数据，先拿出请求体用来返回，然后从map中移除，
        //这样map剩下的数据就都是要放入请求头中的了
        String body = map.get(REQUEST_BODY);
        map.remove(REQUEST_BODY);
        requestTemporaryWrapper.setMap(map);
        return body;
    }

    /**
     * 具体的签名验签逻辑
     *
     * @param originalBody
     * @param exchange
     * @return
     */
    private Map<String, String> doExecute(String originalBody, ServerWebExchange exchange) {
        log.info("current thread verify: {}", Thread.currentThread().getName());
        ServerHttpRequest request = exchange.getRequest();
        String requestBody = originalBody;
        Map<String, String> bodyContent = new HashMap<>(32);
        if (StringUtil.isNotEmpty(originalBody)) {
            // 将请求体转成map结构
            bodyContent = JSON.parseObject(originalBody, Map.class);
        }
        // 基础参数code渠道
        String code = null;
        // 用户token
        String token;
        // 用户id
        String userId = null;
        // 请求路径
        String url = request.getPath().value();
        // 是否跳过参数验证的标识
        String noVerify = request.getHeaders().getFirst(NO_VERIFY);
        // 如果只允许签名访问，但 header 中未携带签名标记，抛异常
        boolean allowNormalAccess = gatewayProperty.isAllowNormalAccess();
        if ((!allowNormalAccess) && (VERIFY_VALUE.equals(noVerify))) {
            throw new DaMaiFrameException(BaseCode.ONLY_SIGNATURE_ACCESS_IS_ALLOWED);
        }
        // 如果需要校验参数 && 当前 URL 不在跳过校验列表
        if (checkParameter(originalBody, noVerify) && !skipCheckParameter(url)) {
            String encrypt = request.getHeaders().getFirst(ENCRYPT);
            //应用渠道
            code = bodyContent.get(CODE);
            //token
            token = request.getHeaders().getFirst(TOKEN);
            // 验证code参数并获取基础参数
            GetChannelDataVo channelDataVo = channelDataService.getChannelDataByCode(code);
            // 如果V2版本就要先对参数进行解密
            if (StringUtil.isNotEmpty(encrypt) && V2.equals(encrypt)) {
                String decrypt = RsaTool.decrypt(bodyContent.get(BUSINESS_BODY), channelDataVo.getDataSecretKey());
                bodyContent.put(BUSINESS_BODY, decrypt);
            }
            // 进行签名验证
            boolean checkFlag = RsaSignTool.verifyRsaSign256(bodyContent, channelDataVo.getSignPublicKey());
            // 验证失败
            if (!checkFlag) {
                throw new DaMaiFrameException(BaseCode.RSA_SIGN_ERROR);
            }
            // 判断是否跳过验证登录的token，默认注册和登录接口不需要token
            boolean skipCheckTokenResult = skipCheckToken(url);
            if (!skipCheckTokenResult && StringUtil.isEmpty(token)) {
                ArgumentError argumentError = new ArgumentError();
                argumentError.setArgumentName(token);
                argumentError.setMessage("token参数为空");
                List<ArgumentError> argumentErrorList = new ArrayList<>();
                argumentErrorList.add(argumentError);
                throw new ArgumentException(BaseCode.ARGUMENT_EMPTY.getCode(), argumentErrorList);
            }
            // 如果token不为空，从token中解析出用户id
            if (!skipCheckTokenResult) {
                UserVo userVo = tokenService.getUser(token, code, channelDataVo.getTokenSecret());
                userId = userVo.getId();
            }
            // 如果userId为空，并且此url需要userId，则从token中解析出userId
            if (StringUtil.isEmpty(userId) && checkNeedUserId(url) && StringUtil.isNotEmpty(token)) {
                UserVo userVo = tokenService.getUser(token, code, channelDataVo.getTokenSecret());
                userId = userVo.getId();
            }
            // 返回请求体
            requestBody = bodyContent.get(BUSINESS_BODY);
        }
        // 根据规则对API接口进行防刷限制!!!!!!
        apiRestrictService.apiRestrict(userId, url, request);
        // 将修改后的请求体和要传递的请求头参数放入map
        Map<String, String> map = new HashMap<>(4);
        map.put(REQUEST_BODY, requestBody);
        if (StringUtil.isNotEmpty(code)) {
            map.put(CODE, code);
        }
        if (StringUtil.isNotEmpty(userId)) {
            map.put(USER_ID, userId);
        }
        return map;
    }

    /**
     * 将网关层request请求头中的重要参数传递给后续的微服务中
     */
    private ServerHttpRequestDecorator decorateHead(ServerWebExchange exchange, HttpHeaders headers, CachedBodyOutputMessage outputMessage, RequestTemporaryWrapper requestTemporaryWrapper, Map<String, String> headMap) {
        return new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                log.info("current thread getHeaders: {}", Thread.currentThread().getName());
                long contentLength = headers.getContentLength();
                HttpHeaders newHeaders = new HttpHeaders();
                newHeaders.putAll(headers);
                Map<String, String> map = requestTemporaryWrapper.getMap();
                if (CollectionUtil.isNotEmpty(map)) {
                    newHeaders.setAll(map);
                }
                if (CollectionUtil.isNotEmpty(headMap)) {
                    newHeaders.setAll(headMap);
                }
                if (contentLength > 0) {
                    newHeaders.setContentLength(contentLength);
                } else {
                    newHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                }
                if (CollectionUtil.isNotEmpty(headMap) && StringUtil.isNotEmpty(headMap.get(TRACE_ID))) {
                    MDC.put(TRACE_ID, headMap.get(TRACE_ID));
                }
                return newHeaders;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return outputMessage.getBody();
            }
        };
    }

    /**
     * 指定执行顺序
     *
     * @return
     */
    @Override
    public int getOrder() {
        return -2;
    }

    /**
     * 判断是否跳过token验证
     *
     * @param url
     * @return
     */
    public boolean skipCheckToken(String url) {
        for (String skipCheckTokenPath : gatewayProperty.getCheckTokenPaths()) {
            PathMatcher matcher = new AntPathMatcher();
            if (matcher.match(skipCheckTokenPath, url)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否跳过参数验证
     *
     * @param url
     * @return
     */
    public boolean skipCheckParameter(String url) {
        for (String skipCheckTokenPath : gatewayProperty.getCheckSkipParmeterPaths()) {
            PathMatcher matcher = new AntPathMatcher();
            if (matcher.match(skipCheckTokenPath, url)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证请求头的参数 noVerify = true
     *
     * @param originalBody
     * @param noVerify
     * @return
     */
    public boolean checkParameter(String originalBody, String noVerify) {
        return (!(VERIFY_VALUE.equals(noVerify))) && StringUtil.isNotEmpty(originalBody);
    }

    /**
     * 判断请求的url是否包含参数userId
     *
     * @param url
     * @return
     */
    private boolean checkNeedUserId(String url) {
        for (String userIdPath : gatewayProperty.getUserIdPaths()) {
            PathMatcher matcher = new AntPathMatcher();
            // 路径匹配
            if (matcher.match(userIdPath, url)) {
                return true;
            }
        }
        return false;
    }
}
