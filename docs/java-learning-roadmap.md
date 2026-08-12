# SkyTrace 定制化 Java / 后端架构学习路线图

> 适用对象：盛克思当前背景
>
> 画像基线：资深前端开发工程师，熟悉 Vue / React / TypeScript / Cesium / Three.js，懂一些 Node.js，NestJS 未学完整，SQL 会基础 CRUD，但对事务、锁、JPA、Spring 容器、分布式后端链路理解不够扎实。
>
> 目标仓库：`skytrace-platform`
>
> 文档更新时间：2026-08-11

这份路线图不是给“零基础 Java 初学者”准备的，而是给你这种已经有多年复杂前端和部分全栈经验、现在要把这个仓库背后的 Java 与后端架构真正吃透的人准备的。

你的优势非常明显：

- 你已经懂复杂业务系统，不缺工程感觉。
- 你已经懂前后端联调，不缺 HTTP、JSON、权限、BFF 这些概念。
- 你已经做过 Node.js、NestJS、FastAPI、Temporal 整合，不是纯前端视角。
- 你已经在真实项目里碰过 IoT、视频流、数字孪生、AI、工作流。

你现在真正缺的不是“会不会写一个 Controller”，而是这几件事：

- 为什么 Spring 里很多对象不用 `new` 也能工作。
- 为什么同样是查库，JPA 有时一条 SQL、有时十几条 SQL。
- 为什么事务一旦和锁、隔离级别、并发更新碰到一起就会变难。
- 为什么后端架构里要拆成 Gateway、BFF、Java Service、Workflow、MQ、对象存储。
- 为什么有些链路要同步返回，有些链路必须异步重试。
- 为什么“代码能跑”不等于“系统能上线”。

所以这份路线图的核心思路是：

**不从语法入门，而是从你熟悉的前端/Node 认知出发，把 Java 后端中最关键、最难、最值得补的骨架补齐。**

---

## 1. 先给你结论：你应该怎么学

你最适合的路线不是：

1. 从头把 Java 语法书啃完
2. 再学 Spring
3. 再学数据库
4. 最后看仓库

这条路线太慢，而且会把你学废。

你真正适合的是这条路线：

1. 先建立“整个仓库的系统地图”
2. 再把 Java 后端最关键的运行机制补上
3. 再用仓库里的真实模块反复对照
4. 再补事务、锁、JPA、工作流这些深水区
5. 最后回到架构层，把系统闭环看透

一句话概括：

**先看骨架，再补原理，再借仓库反复验证。**

---

## 2. 你的学习目标，不是学 Java，而是做到这 8 件事

当你完成这份路线图后，应该能独立做到：

1. 从前端一个按钮点击，一路追到 Gateway、Node BFF、Java Controller、Service、Repository、MySQL、MinIO、Temporal。
2. 看懂一个 Spring Boot 模块的分层结构，并判断代码应该写在哪一层。
3. 理解一个事务什么时候提交、什么时候回滚、为什么会死锁、为什么会锁等待。
4. 看懂 JPA 实体、Repository、DTO、迁移脚本之间的关系。
5. 理解同步接口、消息队列、工作流、对象存储分别解决什么问题。
6. 能独立为当前仓库增加字段、接口、状态流转、数据库迁移和测试。
7. 能判断一个后端方案有没有幂等性、并发一致性、内存风险、重试风险。
8. 能把证据归档这条链路完整讲给别人听。

如果这 8 件事能做到，你就不是“会一点 Java”，而是真的开始吃透这套架构了。

---

## 3. 你当前最该补的知识，不是平均补，而是优先补短板

### 3.1 你已经相对有优势的部分

这些不用从零学：

- HTTP / JSON / REST 基本认知
- 前后端接口联调
- BFF 思路
- 权限、角色、路由、状态流转
- 工程化、Git、Docker 基础
- 复杂业务抽象能力
- 可视化、IoT、AI 场景理解

### 3.2 你最需要补的部分

这些才是你当前的真正短板：

- Java 对象模型与类型系统
- Spring IoC / Bean / 自动装配
- Spring MVC 请求处理链
- JPA / Hibernate 持久化模型
- SQL 的事务、锁、隔离级别、索引命中
- Flyway 迁移与数据库演进
- Spring Security 过滤链
- Temporal 工作流的确定性、重试、幂等
- 后端“状态 + 副作用 + 审计”的设计方式

### 3.3 你现在最容易掉进去的坑

你学习时要刻意避免下面这些误区：

- 用前端组件思维硬套 Spring Bean 生命周期。
- 只看 Controller，不看 Service、Repository、事务边界。
- 只会写 SQL，不理解并发下 SQL 的锁行为。
- 只把 Temporal 当“异步任务框架”，看不到它和普通消息队列的区别。
- 只看接口 happy path，不看失败、重试、补偿、恢复。
- 只看语法，不画链路图。

---

## 4. 这个仓库你应该怎么看

你可以把这个仓库先理解成 5 层：

```text
前端层
  admin-frontend / frontend

接入层
  gateway-java

聚合层
  backend-node

核心业务层
  backend-java

基础设施层
  MySQL / Redis / RabbitMQ / MQTT / MinIO / Temporal / Keycloak
```

其中你当前主修重点不是 `gateway-java`，也不是 AI 服务，而是：

- `backend-java`
- `backend-node`
- `frontend` 或 `admin-frontend`
- `deploy/docker-compose.yml`

原因很简单：

- 你从前端过来，先看 `frontend -> backend-node -> backend-java` 最容易形成闭环。
- `backend-java` 是这套业务规则、事务、JPA、工作流、对象存储的核心。
- `gateway-java` 偏网关与响应式，适合后置学习，不适合最先学。

---

## 5. 你最推荐的学习顺序

我建议你按下面 6 个阶段推进，而不是按语言章节推进。

| 阶段 | 主题 | 目标 |
| --- | --- | --- |
| 阶段 A | 建立全局地图 | 先搞清整个系统怎么流转 |
| 阶段 B | Java 与 Spring 最小必需知识 | 能读懂普通 Controller / Service / DTO |
| 阶段 C | 数据库、JPA、事务、锁 | 能解释数据为什么这样存、这样改 |
| 阶段 D | 证据中心全链路 | 用仓库真实业务把前面知识串起来 |
| 阶段 E | Temporal、消息、对象存储 | 理解复杂后端为什么这么设计 |
| 阶段 F | 安全、部署、可运维性 | 形成“能上线”的后端视角 |

---

## 6. 分阶段路线图

## 阶段 A：先建立系统地图

### 目标

先别急着啃 Java。你先要回答：

- 浏览器请求为什么不直接打到 Java？
- Node BFF 在这里到底干什么？
- Java 服务为什么还要接 Temporal、MinIO、MQ？
- 证据文件为什么不直接存在数据库？

### 你要读的仓库内容

1. [`README.md`](../README.md)
2. [`docs/architecture.md`](./architecture.md)
3. [`deploy/docker-compose.yml`](../deploy/docker-compose.yml)
4. [`backend-node`](../backend-node/)
5. [`backend-java`](../backend-java/)

### 你要产出的东西

你自己画一张图，至少包含这些箭头：

```text
Browser
  -> Nginx
  -> Gateway
  -> Node BFF
  -> Java Service
  -> MySQL
  -> MinIO
  -> Temporal
```

### 这一阶段你要回答的问题

1. 哪些请求是前端直接调 Node？
2. Node 是转发，还是会做业务聚合？
3. Java 是单体服务，还是多个服务？
4. 哪些数据进 MySQL，哪些文件进 MinIO？
5. 哪些动作要立即返回，哪些动作可以异步执行？

### 通过标准

你能不看代码，口述一遍“证据归档从页面点击到 ZIP 下载”的主流程。

---

## 阶段 B：补 Java 与 Spring 的最小核心

这一阶段不是把 Java 学完，而是学到“足够看懂仓库”。

### B1. Java 只学这些

你当前最需要的 Java 语法点：

- 类、对象、构造器、访问修饰符
- 接口与实现类
- `record`
- `enum`
- 泛型
- `List` / `Set` / `Map`
- `Optional`
- Lambda
- Stream 基础
- 异常
- `try-with-resources`
- `LocalDateTime` / `Instant`
- 注解

### B2. 你不用过度深挖的内容

先不要陷进去：

- JVM 调优
- 字节码细节
- 高级并发包
- 复杂设计模式大全
- WebFlux / Reactor 深水区

这些不是你当前第一优先级。

### B3. 你要怎么类比理解

把 Java / Spring 先用你熟悉的前端思维做一个映射：

| 你熟悉的东西 | 对应到后端里的近似概念 |
| --- | --- |
| Vue 组件 / React 组件 | Spring Bean，但生命周期和职责完全不同 |
| 前端路由入口 | Controller |
| composable / hooks 中的业务逻辑 | Service 的部分职责 |
| 请求封装层 | Feign / RestClient / Service 调用 |
| Zustand / Pinia 中状态变更 | 后端里的数据库状态变更，但持久化且有事务 |
| 前端接口类型定义 | DTO / Entity / VO |

注意这里只是帮助你建立桥梁，不是完全等价。

### B4. 你在仓库里先看这些文件

1. [`BackendJavaApplication.java`](../backend-java/src/main/java/com/skytrace/backend/BackendJavaApplication.java)
2. [`ApiResponse.java`](../backend-java/src/main/java/com/skytrace/backend/common/ApiResponse.java)
3. 任意一个简单模块的 `controller/service/repository/domain/dto`

建议先从 `device` 模块开始，因为它比 evidence 更平缓。

### B5. 这一阶段推荐资料

#### Java 官方

- Dev.java Learn Java：<https://dev.java/learn/>  
  来源：Oracle 官方 Java 学习入口。适合你补语言最小核心。citeturn0search5

- Java Language Basics：<https://dev.java/learn/language-basics/>  
  适合快速扫一遍语法缺口。citeturn0search8

#### Spring 官方

- Spring Framework Reference：<https://docs.spring.io/spring-framework/reference/index.html>  
  这是 Spring 官方总参考。重点看 IoC、AOP、Transactions、Web MVC。citeturn0search0

- Spring Core Technologies：<https://docs.spring.io/spring/reference/core.html>  
  重点补 IoC 容器和依赖注入。citeturn0search4

### B6. 这一阶段的核心任务

你要能解释：

- `@RestController` 为什么会变成 HTTP 接口。
- `@Service` 为什么可以被注入。
- `@Autowired` / 构造器注入到底发生了什么。
- `record` 为什么适合做 DTO。
- 为什么 Java 项目里会有 Entity、DTO、Repository 这些层次。

---

## 阶段 C：这是你的重灾区，要重点补数据库、JPA、事务、锁

这部分是最关键的，因为你自己已经明确提到：

- SQL 会一些基础操作
- 事务和锁的深度概念不明
- 后台架构读起来像天书

其实后端“像天书”的根本原因，很多都不是 Java 语法，而是：

**数据一致性、事务边界、并发控制、状态演进。**

### C1. 你必须真正搞懂的数据库知识

不是会写 `SELECT` 就够了，你必须吃透：

- ACID 到底是什么
- 事务何时开始、何时提交、何时回滚
- 脏读、不可重复读、幻读是什么
- MySQL InnoDB 为什么会有行锁、间隙锁、Next-Key Lock
- `SELECT ... FOR UPDATE` 为什么能锁数据
- 为什么索引没命中时锁范围会变大
- 为什么两个事务更新顺序不一致会死锁

### C2. 你要学的 JPA 重点

你在这个仓库里不是直接手写所有 SQL，而是大量通过 JPA：

- `@Entity`
- `@Table`
- `@Column`
- `@Enumerated`
- `@OneToMany` / `@ManyToOne`
- `JpaRepository`
- 派生查询方法
- `@Query`
- 事务传播

你要理解：

- Java 对象是怎么映射成表的
- Repository 方法为什么能自动生成查询
- 为什么有时 JPA 很方便，有时又会埋性能坑
- DTO 和 Entity 为什么不能乱混

### C3. 这一阶段推荐资料

#### SQL 入门巩固

- SQLBolt：<https://sqlbolt.com/>  
  适合快速补齐查询、连接、聚合、更新、建表基础。citeturn0search2

#### MySQL 锁与事务官方资料

- InnoDB Locking：<https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html>  
  重点理解共享锁、排它锁、意向锁、记录锁、间隙锁、Next-Key Lock。citeturn2search0

- Locks Set by Different SQL Statements：<https://dev.mysql.com/doc/refman/8.0/en/innodb-locks-set.html>  
  重点理解不同 SQL 到底会加什么锁。citeturn2search1

- InnoDB Multi-Versioning：<https://dev.mysql.com/doc/refman/8.0/en/innodb-multi-versioning.html>  
  帮你理解 MVCC。citeturn2search5

- Deadlocks in InnoDB：<https://dev.mysql.com/doc/mysql/8.0/en/innodb-deadlocks.html>  
  这是你后面理解并发问题的关键。citeturn2search6

#### Spring Data JPA 官方资料

- Spring Data JPA Reference：<https://docs.spring.io/spring-data/jpa/reference/>  
  重点看 Repository、Transactionality、Locking、Auditing。citeturn3search1

### C4. 你在仓库里要重点看什么

1. `backend-java/src/main/resources/db/migration`
2. 各模块的 `domain` 实体
3. 各模块的 `repository`
4. 带 `@Transactional` 的 `service`

### C5. 这一阶段最重要的学习动作

不是看，而是验证。

你要亲手做这些事：

1. 找一个实体，看它对应哪张表。
2. 找一个 Repository 方法，推断它会生成什么 SQL。
3. 找一个 `@Transactional` 方法，判断它里面哪些改动属于同一个事务。
4. 故意制造一次并发更新，观察锁等待或死锁。
5. 看一次 Flyway migration 如何把 Java 改动落到数据库结构上。

### C6. 通过标准

你能自己讲明白：

- “为什么归档清理一定要先校验 manifest，再删源文件”
- “为什么一个批处理需要 claim，而不能直接扫全表逐条删”
- “为什么事务不能跨 MinIO 文件上传一直持有数据库锁”

如果这三句能讲明白，你后端水平就会上一个台阶。

---

## 阶段 D：用证据中心把前面的知识全部串起来

这一步最适合你，因为你已经参与了 `phase 3`，不是陌生业务。

证据中心是这个仓库里最适合拿来做综合学习样本的模块，因为它同时包含：

- 前端页面
- Node BFF
- Java Controller / Service / Repository
- 数据库状态
- MinIO 文件
- Temporal 工作流
- 哈希校验
- 清单 manifest
- 审计
- 定时任务

### 你应该按这个顺序读

1. 页面入口
2. 前端 API 调用
3. Node BFF 路由
4. Java Controller
5. Java Service
6. Repository / Entity
7. Temporal Workflow / Activity
8. MinIO 读写
9. 数据表和迁移脚本
10. 测试

### 你要掌握的 5 个问题

1. 归档任务为什么不是同步打包完再返回？
2. `contentHash` 为什么重要？
3. manifest 为什么要独立存在，还要嵌入 ZIP？
4. 清理为什么要先验证归档完整性？
5. 为什么恢复、清理、回填都需要独立状态和审计？

### 建议你做的练习

你已经比普通学习者更适合做“带问题读代码”：

- 把“创建归档任务”这条链路画出来
- 把“历史哈希回填”这条链路画出来
- 把“归档后清理”这条链路画出来

每条链路都写清楚：

- 入口在哪
- 改了哪些表
- 访问了哪些外部系统
- 哪些地方可能失败
- 失败后怎么恢复

---

## 阶段 E：补你最容易觉得“玄”的那部分，Temporal、消息、对象存储

这是很多前端转后端最容易发懵的地方。

### E1. 你要先建立一个判断标准

问自己：

- 这个动作是不是耗时很长？
- 这个动作是不是可能失败重试？
- 这个动作是不是不能让用户一直等 HTTP？
- 这个动作是不是涉及文件、第三方系统、跨服务副作用？

如果答案是“是”，那它通常就不该只是一个普通同步接口。

### E2. Temporal 你要学什么

不是学 API，而是学它解决的问题：

- 为什么普通定时任务/消息队列不够
- Workflow 和 Activity 的职责差异
- 为什么 Workflow 要求确定性
- 为什么 Activity 可以做 I/O、副作用和重试
- 什么叫 heartbeat
- 什么叫可恢复执行
- 什么叫幂等

### E3. 对象存储你要理解什么

你得理解：

- 为什么证据文件和归档包不放 MySQL
- MinIO 和本地磁盘的使用差异
- 预签名下载链接为什么有时效
- 文件哈希为什么能提升可追溯性
- ZIP 很大时为什么要流式处理、不能整包进内存

### E4. 推荐资料

- Temporal 官方文档：<https://docs.temporal.io/>  
  Temporal 官方文档明确强调它解决的是“可恢复、可靠执行”的问题，非常适合结合你仓库里的工作流实现来看。citeturn4search0

- Spring Boot Reference：<https://docs.spring.io/spring-boot/docs/current/reference/>  
  当前官方文档入口可从 `current/reference` 进入，适合补配置、数据访问、消息、容器化与生产化。搜索结果也明确说明最新文档以 `current/reference` 为准。citeturn5search0

### E5. 这一阶段通过标准

你能说清楚：

- “为什么证据归档更适合 Temporal，而不是普通接口 + 数据库轮询”
- “为什么打包这种 I/O 工作必须考虑重试和幂等”
- “为什么 MinIO 出故障时不能让数据库状态先假装成功”

---

## 阶段 F：补齐真正的后端架构视角

当你完成前面几阶段后，最后要形成的是“架构理解力”。

### 你要重点看这些主题

- 认证与鉴权
- Spring Security 过滤链
- 审计日志
- 配置分环境
- 数据迁移
- 健康检查
- 定时任务
- Docker Compose 部署关系
- 可观测性与失败恢复

### 这一阶段你要问的问题

1. 这个系统怎么知道当前用户是谁？
2. 为什么有些接口 401，有些 403？
3. 为什么生产环境不能随便跳过 Flyway validate？
4. 为什么定时清理默认要 `dry-run`？
5. 为什么“删文件”这种操作一定要有审计？

---

## 7. 你应该怎么看书

你不是缺书，而是要避免“看书很多，代码没吃透”。

所以我给你的建议是：书分 3 类。

### 7.1 第一类：立刻能用的工具型资料

这类资料优先级最高：

- Dev.java 官方教程
- Spring 官方文档
- Spring Data JPA 官方文档
- MySQL 官方锁与事务文档
- Temporal 官方文档

原因是：

- 它们贴近你正在看的代码
- 遇到问题能立刻查
- 不会过多偏离当前仓库

### 7.2 第二类：用来建立系统底层认知的书

这类书不是马上解决代码细节，而是帮你理解“为什么”。

#### 1. 《Designing Data-Intensive Applications》

- 官方页（第二版）：<https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/> citeturn1search0
- 第一版入口：<https://www.oreilly.com/library/view/designing-data-intensive-applications/9781491903063/> citeturn1search5

这本书对你有没有用？

**有用，而且很有用，但不是现在第一本。**

为什么有用：

- 它能补你“后端架构像天书”的那部分根因。
- 它讲的是数据系统、存储、复制、事务、一致性、流处理、分布式权衡。
- 这些正是你从前端走向后端架构时最缺的底层认知。

为什么不是你现在第一本：

- 它偏原理，不是项目上手书。
- 如果你现在直接从头硬啃，容易看懂名词，看不懂落地。

你最适合的读法：

1. 先把本仓库主链路看懂
2. 再带着问题读 DDIA
3. 重点读和你当前痛点直接相关的章节

最适合你当前的章节优先级：

1. 可靠性、可扩展性、可维护性
2. 数据模型与查询语言
3. 存储与检索
4. 编码与演化
5. 复制
6. 事务
7. 数据系统的正确性与流处理

#### 2. 《Spring in Action》

如果你想配一本偏 Spring 实战书，这类书会比纯语法书更适合你，因为你需要的是“框架如何工作、怎么组织业务代码”。

我没有为这本书额外联网核验具体最新购买页，所以这里不给你放不够稳妥的销售链接；你可以优先以 Spring 官方文档为准，再决定是否补一本中文或英文实战书。

#### 3. 《Head First Design Patterns》

这本不是让你立刻套设计模式，而是帮助你理解“为什么后端喜欢抽接口、分职责、解耦副作用”。如果你看 Java 代码时总觉得层很多、对象很多，这本会有帮助。

### 7.3 第三类：可以后置的内容

这些先不用投入太多：

- JVM 深入书
- Netty / NIO 深入
- Java 并发编程全套
- WebFlux 专项书

不是没用，而是现在投入产出比不高。

---

## 8. 给你一份真正适合你的 12 周学习节奏

考虑到你不是初级开发，我给你的是 12 周“高密度定制路线”，不是 20 周大众版。

每周建议投入：

- 工作日每天 1 到 1.5 小时
- 周末半天做系统梳理或动手验证

### 第 1 周：系统地图

- 读 README、架构文档、docker-compose
- 画服务调用图
- 走通前端到 Java 的一条普通查询接口

通过标准：

- 你能讲明白 Gateway、Node、Java、MySQL、MinIO、Temporal 的分工

### 第 2 周：Java 最小语法 + Spring 入口

- 看 Dev.java 最核心部分
- 看启动类、Controller、DTO、ApiResponse
- 理解 Bean、依赖注入、注解

通过标准：

- 你能独立读懂一个简单 Controller 到 Service 的调用

### 第 3 周：Spring MVC 请求链

- 看请求映射、参数绑定、返回 JSON、异常处理
- 跟一条 `GET` 和一条 `POST` 接口

通过标准：

- 你能解释“一个 HTTP 请求进来后，Spring 做了什么”

### 第 4 周：JPA 与实体映射

- 看 Entity、Repository、Flyway
- 学会对象到表的映射
- 推断简单 Repository 会生成什么 SQL

通过标准：

- 你能从一个实体类推断出表结构的大致形态

### 第 5 周：事务、锁、隔离级别

- 看 MySQL InnoDB 官方文档
- 做并发更新实验
- 理解锁等待、死锁、MVCC

通过标准：

- 你能解释为什么“同样是 update，锁行为可能完全不同”

### 第 6 周：Node BFF 与 Java 的职责边界

- 看 `backend-node`
- 找几条前端实际调用链
- 区分 BFF 聚合逻辑和 Java 核心业务逻辑

通过标准：

- 你能说清楚哪层该做参数组装，哪层该做事务和状态变更

### 第 7 周：证据中心基础链路

- 看 evidence 模块的实体、状态、接口
- 理解 evidence、archiveJob、manifest 的关系

通过标准：

- 你能把证据中心的核心对象关系讲清楚

### 第 8 周：Temporal 与归档打包

- 看 Workflow、Activity、重试、心跳
- 看 ZIP 打包与 MinIO 上传逻辑

通过标准：

- 你能解释“为什么归档任务要异步执行”

### 第 9 周：历史回填与清理策略

- 看 contentHash 回填任务
- 看 purge claim、完整性校验、审计

通过标准：

- 你能解释“为什么不能简单写个定时任务把老文件删掉”

### 第 10 周：安全、权限、审计

- 看 Spring Security、过滤器、鉴权
- 看审计记录链路

通过标准：

- 你能追踪 401/403 产生的位置

### 第 11 周：部署与运维视角

- 看 Docker Compose、配置文件、健康检查
- 看多环境配置和启动依赖

通过标准：

- 你能解释一个服务为什么启动失败，以及它依赖谁

### 第 12 周：DDIA + 架构复盘

- 带着实际问题读 DDIA 重点章节
- 回头重画整套系统架构图
- 选一个小功能独立完成改动

通过标准：

- 你能把这套系统作为“架构案例”完整讲给别人听

---

## 9. 你每周固定应该做的学习动作

你最适合的节奏不是纯看书，而是每周都做下面 4 件事：

1. 读一个主题的官方资料
2. 在仓库里找对应实现
3. 画一条调用链
4. 做一个很小的改动或验证

建议固定模板：

```text
今天主题：

我以为它是什么：

仓库里它实际怎么实现：

它解决的问题是什么：

如果失败会怎样：

我还没想明白的点：
```

这个模板非常适合你，因为你已经不是记笔记阶段，而是建立架构判断力阶段。

---

## 10. 你现在最该先看的仓库文件清单

我按你的学习价值排序给你列一版：

### 第一批，立刻看

1. [`README.md`](../README.md)
2. [`docs/architecture.md`](./architecture.md)
3. [`deploy/docker-compose.yml`](../deploy/docker-compose.yml)
4. [`backend-java/pom.xml`](../backend-java/pom.xml)
5. [`backend-java/src/main/resources/application.yml`](../backend-java/src/main/resources/application.yml)
6. [`backend-node/package.json`](../backend-node/package.json)

### 第二批，先建立 Java 请求链

1. `BackendJavaApplication`
2. 一个简单 Controller
3. 对应 Service
4. 对应 Repository
5. 对应 Entity
6. 对应 DTO

### 第三批，进入 evidence 主线

1. evidence controller
2. evidence service
3. evidence repository / entity
4. archive workflow / activity
5. archive package / manifest / hash service
6. migration SQL

### 第四批，补完整系统视角

1. security
2. audit
3. scheduler / maintenance
4. MinIO 配置
5. Temporal 配置
6. Node BFF 路由与代理

---

## 11. 如果你问“我到底该先学 Java 还是先看架构”，我的答案是

先看架构主线，
再补 Java 最小必需，
再回到代码，
再补数据库与分布式原理。

原因很简单：

你已经是资深工程师，不缺抽象能力；
你现在缺的是把后端机制和系统原理挂到真实项目上。

如果你反过来只学语法，你会很痛苦，也很慢。

---

## 12. 如果只给你一个最短起步版本

如果你最近很忙，只能先抓最重要的，我建议你先做这 5 件事：

1. 画出 `frontend -> backend-node -> backend-java -> MySQL/MinIO/Temporal` 的系统图
2. 看懂一个普通查询接口的完整链路
3. 看懂 evidence 归档任务创建链路
4. 专门补一轮 MySQL 事务、锁、MVCC
5. 再去读 DDIA 的事务、数据模型、存储章节

---

## 13. 最后给你的判断

### 这份路线最重要的原则

你不是从“写 Java 代码”开始成长，
而是从“能解释这套系统为什么这样设计”开始成长。

### DDIA 对你有没有用

有用，而且是后半程非常重要的一本书。

但它对你的最佳使用姿势不是“现在从第一页硬啃”，而是：

**先把仓库跑通并看懂主链路，再带着问题回头读 DDIA。**

这样你会突然发现：

- 事务不再只是概念
- 一致性不再只是书上名词
- 工作流、对象存储、重试、幂等、审计都会开始串起来

那时你读这本书，收益会非常大。

---

## 14. 给你的下一步建议

如果按这份文档落地，我建议你明天就从下面这个顺序开始：

1. 读 [`README.md`](../README.md) 和 [`docs/architecture.md`](./architecture.md)
2. 画当前系统图
3. 选一条最简单的查询接口，走完整链路
4. 再进入 evidence 模块
5. 同时开始补 MySQL 事务与锁

如果你愿意，我下一步可以继续直接帮你做两份配套文档：

1. 一份“按文件路径拆开的阅读顺序清单”
2. 一份“事务、锁、JPA、Temporal 的中文通俗讲义”

这两份会比纯路线图更适合你马上开始啃仓库。
