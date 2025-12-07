## 项目介绍
大麦订票服务系统提供了可以在线上对相应的节目（包括：演唱会、话剧歌剧、体育比赛、儿童亲子）进行订票的功能，用户可以进行注册，登录，然后选择节目和座位后进行购票，支付，以及查询自己的订单功能


## 技术结构

- 使用了 **SpringCloud+SpringCloudAlibaba** 的微服务结构

- 使用了 **Nacos** 作为注册中心

- 使用 **Redis** 不仅仅作为缓存，还使用了`Lua脚本`/`延迟队列`/`Stream消息队列` 等高级特性

- 引入了 **Kafka** 消息中间件，**SpringBootAdmin** 作为服务的监控通知

- **ELK** 作为日志的记录，**ElasticSearch**提供搜索和展示功能，

- **Sentinel/Hystrix** 作为熔断保护层

- 使用 **ShardingSphere** 实现分库分表，来存储海量的数据

通过以上设计，来实现应对高并发、高吞吐的能力，以及海量数据的存储和服务状态的监控

![](https://multimedia-javaup.cn/%E6%9E%B6%E6%9E%84%E5%9B%BE/%E9%A1%B9%E7%9B%AE%E6%9E%B6%E6%9E%84%E5%9B%BE%28%E5%8E%8B%E7%BC%A9%E5%90%8E%29.jpg)

## 业务结构

通过此业务结构图进一步详细的介绍项目中的功能，包括：**服务配置**、**技术选型**、**核心业务**、**设计组件**、**中间件的使用**、**监控方式**、**高并发解决策略** 等各个方面，方便大家能够对大麦项目的整体架构和设计有一个清晰的认知

![](https://multimedia-javaup.cn/%E6%9E%B6%E6%9E%84%E5%9B%BE/%E9%A1%B9%E7%9B%AE%E4%B8%9A%E5%8A%A1%E7%9A%84%E7%BB%93%E6%9E%84%E5%9B%BE%28%E4%BC%98%E5%8C%96%29.png)

## 技术选型

| 技术                 | 说明               | 官网                                                         |
| -------------------- | ------------------ | ------------------------------------------------------------ |
| Spring-Boot          | Web服务框架        | [https://spring.io/projects/spring-boot](https://spring.io/projects/spring-boot) |
| Spring-Cloud         | 微服务框架         | [https://spring.io/projects/spring-cloud](https://spring.io/projects/spring-cloud) |
| Spring-Cloud-alibaba | alibaba微服务框架  | [https://github.com/alibaba/spring-cloud-alibaba](https://github.com/alibaba/spring-cloud-alibaba) |
| Spring-Cloud-Gateway | 微服务网关         | [https://spring.io/projects/spring-cloud-gateway](https://spring.io/projects/spring-cloud-gateway) |
| Nacos                | 服务注册中心       | [https://nacos.io/zh-cn/index.html](https://nacos.io/zh-cn/index.html) |
| Sentinel             | 服务熔断           | [https://sentinelguard.io/zh-cn/](https://sentinelguard.io/zh-cn/) |
| Log4j2               | 日志框架           | [https://github.com/apache/logging-log4j2](https://github.com/apache/logging-log4j2) |
| Mysql                | 数据库             | [https://www.mysql.com/](https://www.mysql.com/)             |
| MyBatis-Plus         | ORM框架            | [https://baomidou.com](https://baomidou.com)                 |
| MyBatisGenerator     | 数据层代码生成器   | [http://www.mybatis.org/generator/index.html](http://www.mybatis.org/generator/index.html) |
| AJ-Captcha           | 图形验证码         | [https://gitee.com/anji-plus/captcha](https://gitee.com/anji-plus/captcha) |
| Kafka                | 消息队列           | [https://github.com/apache/kafka/](https://github.com/apache/kafka/) |
| Redis                | 分布式缓存         | [https://redis.io/](https://redis.io/)                       |
| Redisson             | 分布式Redis工具    | [https://redisson.org](https://redisson.org)                 |
| Elasticsearch        | 搜索引擎           | [https://github.com/elastic/elasticsearch](https://github.com/elastic/elasticsearch) |
| LogStash             | 日志收集工具       | [https://github.com/elastic/logstash](https://github.com/elastic/logstash) |
| Kibana               | 日志可视化查看工具 | [https://github.com/elastic/kibana](https://github.com/elastic/kibana) |
| Nginx                | 静态资源服务器     | [https://www.nginx.com/](https://www.nginx.com/)             |
| Docker               | 应用容器引擎       | [https://www.docker.com](https://www.docker.com)             |
| Jenkins              | 自动化部署工具     | [https://github.com/jenkinsci/jenkins](https://github.com/jenkinsci/jenkins) |
| Hikari               | 数据库连接池       | [https://github.com/brettwooldridge/HikariCP](https://github.com/brettwooldridge/HikariCP) |
| JWT                  | JWT登录支持        | [https://github.com/jwtk/jjwt](https://github.com/jwtk/jjwt) |
| Lombok               | Java语言增强插件   | [https://github.com/rzwitserloot/lombok](https://github.com/rzwitserloot/lombok) |
| Hutool               | Java工具类库       | [https://github.com/looly/hutool](https://github.com/looly/hutool) |
| Swagger-UI           | API文档生成工具    | [https://github.com/swagger-api/swagger-ui](https://github.com/swagger-api/swagger-ui) |
| Knife4j              | Swagger 增强框架   | [https://doc.xiaominfo.com](https://doc.xiaominfo.com)       |
| Hibernator-Validator | 验证框架           | [http://hibernate.org/validator](http://hibernate.org/validator) |
| XXL-Job              | 分布式定时任务框架 | [http://www.xuxueli.com/xxl-job](http://www.xuxueli.com/xxl-job) |
| ShardingSphere       | 分库分表           | [https://shardingsphere.apache.org](https://shardingsphere.apache.org) |

## 架构和组件设计

针对于分布式和微服务的项目来说，随着业务的发展，项目的数量上千个都是很正常的，但如何要把这些项目做好配置，做好架构设计，设计出组件库，都是要考虑的因素

既然组件库是要给其他服务提供使用，所以在设计时要考虑的细节非常的多，**设计模式和高内聚低耦合的思想更加的重要**，而且代码的健壮性和高效率的执行也是同样重要，而在大麦项目中，使用了SpringBoot的自动装配机制来设计组件库

除了组件库外，还有对**异常的处理、数据的封装格式、多线程的使用等等也都要进行相应的封装设计**，这些在项目中同样具备

![](https://multimedia-javaup.cn/%E6%9E%B6%E6%9E%84%E5%9B%BE/%E7%BB%84%E4%BB%B6%E6%9E%B6%E6%9E%84%E5%9B%BE.png)

## 业务流程

对于大麦项目来说，核心的业务就是用户选择节目然后进行购票功能了，项目中不仅完整了对整个业务流程的完整闭环，而且考虑到既然设计此项目是为了应对高并发的特点，那么在从业务的角度上也做了很多的优化设计

![](https://multimedia-javaup.cn/%E6%9E%B6%E6%9E%84%E5%9B%BE/%E4%B8%9A%E5%8A%A1%E5%9B%BE%E4%BC%98%E5%8C%96.png)	

## 项目的亮点质量高吗

我也是一路过来的，所以很清楚大家的心态，希望是能和实际业务结合起来进行学习项目的架构设计技巧以及解决高并发的解决方案，

#### 这里有一点要注意 就是不要让大家过于的陷入项目中的复杂业务中

一方面其实面试官并不会对你做过的业务太感兴趣，太复杂了也听不懂，**最好是你能把业务和解决问题的能力结合起来给他讲**，

另一方面项目的目的是让你提供技术能力，业务固然重要，但还是希望以后能把学到的技术应用到自己的项目中做到融会贯通。

而本项目争取让小伙伴尽可能快速理解业务的前提下，充分的来提供技术能力，尽量减少不必要的时间和精力

### 项目中的各个亮点部分

项目的亮点可以分成多个部分，比如涉及到用户服务的业务时，项目在海量并发的场景下的问题：

- **用户服务如何设计分库分表，存在用户邮箱、用户手机号多种方式登录，要怎么设计不会发生读扩散问题？**

- **当一瞬间有大量的用户注册请求时，如何防止 缓存穿透问题？网上说的那些方案到底靠谱吗？到底要怎么解决并且不影响用户体验？**

- **用户和购票人数据为了应对高并发的场景下，在缓存中要怎么设计？把所有数据都放进去吗？**

- **如何设计缓存策略？采取哪种结构来存储？采取哪种维度来存储？哪些数据适合放入缓存？哪些不适合？**

  

在用户进行浏览的过程中，对于问题的存在同样也不少：

- **如何应对高并发下的用户查询请求？在主页列表、类型列表、的请求查看下，如何将设计分库分表的数据查询方案？**

- **节目详情要怎么设计缓存？有了Redis就可以了吗？突发性流量激增的问题怎么解决？**

- **如何设计多级缓存来应对几十万，甚至几百万的访问压力？如果发生了缓存雪崩要如何解决和提前预防？**

  

而在用户购票的流程中，为了解决高并发的压力，需要考虑的问题和细节就会更多：

- **如何应对高并发下的用户购票压力？在购票流程中怎么考虑缓存和数据库的交互？**

- **库存数量在缓存中应该如何设计？用户购票和支付过程中，要怎么正确的扣除库存？异常了怎么回滚？数据库中的余票数量一致性要如何解决？**

- **分布式锁使用起来的细节到底有哪些？只要加上一行锁就可以了吗？**

- **高并发下的分布式锁如何进一步的优化？锁的粒度？网络请求的性能？**

- **幂等功能如何实现？有哪些维度需要考虑？**

- **经典的缓存数据库一致性的问题实际生产环境中到底如何解决？直接删除缓存、延迟双删 这些方案到底可行吗？**



而在整个项目的架构设计上，也有很多的问题存在：

- **高并发下订单延迟关闭功能如何实现？使用中间件作为延迟队列的问题？使用redis作为延迟队列可以吗？如何提高性能？**

- **分布式id如何生成？经典的雪花算法？直接使用MybatisPlus中的生成策略可以吗？有什么问题？**

- **订单的分库分表如何设计？既要支持订单详情查询、又要支持订单列表查询而不发生读扩散？**

- **如何执行灵活的限流规则？能支持到某个时间段、某个请求、并能记录下异常行为信息？**

- **项目的架构配置、服务配置、数据结构要如何统一设计和管理？异常如何捕获？**


## 项目展示

为了尽可能的还原，本项目尽可能贴近官网的页面设计和业务流程，小伙伴可以通过前端项目一边来学习业务，一边体会业务中调取了哪些后端接口，这种学习方式是简单且高效的，也建议小伙伴在学习公司的业务时也使用这种方式

### 主页列表
![](https://multimedia-javaup.cn/%E5%A4%A7%E9%BA%A6%E9%A1%B9%E7%9B%AE%E6%88%AA%E5%9B%BE%2F%E4%B8%BB%E9%A1%B5%E5%88%97%E8%A1%A8.jpg)
### 分类列表
![](https://multimedia-javaup.cn/%E5%A4%A7%E9%BA%A6%E9%A1%B9%E7%9B%AE%E6%88%AA%E5%9B%BE%2F%E5%88%86%E7%B1%BB%E5%88%97%E8%A1%A8.jpg)
### 节目详情
![](https://multimedia-javaup.cn/%E5%A4%A7%E9%BA%A6%E9%A1%B9%E7%9B%AE%E6%88%AA%E5%9B%BE%2F%E8%8A%82%E7%9B%AE%E8%AF%A6%E6%83%85.jpg)
### 生成订单
![](https://multimedia-javaup.cn/%E5%A4%A7%E9%BA%A6%E9%A1%B9%E7%9B%AE%E6%88%AA%E5%9B%BE%2F%E6%8F%90%E4%BA%A4%E8%AE%A2%E5%8D%95.jpg)
### 订单列表
![](https://multimedia-javaup.cn/%E5%A4%A7%E9%BA%A6%E9%A1%B9%E7%9B%AE%E6%88%AA%E5%9B%BE%2F%E8%AE%A2%E5%8D%95%E5%88%97%E8%A1%A8.jpg)
### 订单详情
![](https://multimedia-javaup.cn/%E5%A4%A7%E9%BA%A6%E9%A1%B9%E7%9B%AE%E6%88%AA%E5%9B%BE%2F%E8%AE%A2%E5%8D%95%E8%AF%A6%E6%83%85.jpg)
## 欢迎联系我

小伙伴想要实时关心大麦项目更新的进展情况的话，可以关注公众号：**阿星不是程序员**



