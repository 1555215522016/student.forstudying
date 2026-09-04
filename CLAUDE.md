# SCUPLUS 校园网站后端

> 本文件记录项目当前的技术栈、模块规划、关键决策与进度。规划随开发调整，非最终定稿。

## 项目定位
用 Spring Boot 重写 SCUPLUS 后端（原微信小程序形态 → **网站形态**），作为面试备战项目，突出后端深入与高并发场景。

## 技术栈
- Spring Boot 3.5 + JDK 17 + Maven（Wrapper 3.9.15）
- MyBatis-Plus 3.5.7 + MySQL 8（Docker）
- Redis 7 + Caffeine（分层缓存）
- MinIO（本地对象存储，兼容 OSS/S3，前端直传预签名）
- Spring Security + Session/Cookie（弃用 JWT）
- Spring WebSocket（实时通知/私信，非 Netty —— 量级决定）
- RocketMQ（异步通知：抢课成功 → 微信/站内信，解耦慢外部调用；**不用来削峰**，见关键设计决策）
- Lombok + Hutool + MapStruct + SpringDoc（OpenAPI）

## 本地环境
- MySQL: `localhost:3306`，用户 `root` / 密码 `root123456`，库 `scuplus`（docker compose）
- Redis: `localhost:6379`
- MinIO: `localhost:9000`(API) / `localhost:9001`(控制台)，账号 `minioadmin`/`minioadmin`
- 应用: `localhost:8080`，Swagger: `/swagger-ui.html`
- 构建: `mvnw.cmd compile` / `mvnw.cmd spring-boot:run` / `mvnw.cmd test`

## 当前进度（2026-09-02）
- [x] Step 0 脚手架：pom、Maven Wrapper、docker-compose、多环境配置、启动类
- [x] Step 1 基础设施：统一响应4件套 + Security(Session/白名单) + MybatisPlusConfig分页 + CorsConfig + SwaggerConfig
- [x] Step 2 认证模块：t_user/t_user_credential 表 + 实体/Mapper + AuthService(模拟认证/自动建档) + AuthController(login/logout/me)，全链路测试通过
- [x] 日常分享模块（替代公告，大部分完成）
  - [x] 表结构 V2（t_post/t_comment/t_like + is_anonymous + role 列）+ MinIO
  - [x] Entity/Mapper（Post/Comment/Like）
  - [x] LikeService 点赞/取消/切换 + MySQL权威判断 + Redis Set双写（测试通过）
  - [x] Post 发布 / 列表 / 详情（匿名、软删过滤）
  - [x] 评论模块（发/看，含匿名，SQL原子更新评论数）
  - [x] ShareJob 点赞/点踩延迟落库（@Scheduled 30s）
  - [x] 管理员删帖（角色 role + 软删 status=1）
  - [x] OSS 预签名上传（MinIO + bucket自动创建/公开读 + 前端直传，链路测试通过）
  - [x] 分片上传/断点续传（前端直传分片：presign-parts 发每片签名 URL，文件不经后端；listParts 断点续传；complete 合并前校验数量，合并直接用 MinIO 记录的 etag —— 前端无法伪造；测试通过）
- [ ] 抢课模块 ★（P1 核心+浏览列表已完成，P2 限流进行中，P3 通知待做）
  - [x] 表结构 V3：t_course(选修课) + t_course_selection(选课记录，status 状态机 0待处理/1已选/2已退/3失败，UNIQUE(user_id,course_id) 幂等)
  - [x] 初始数据 200 门选修课（40门×5班×100人，总名额 20000，匹配 1.5w 人抢课场景）
  - [x] 抢课 Lua 三态原子判定（防重复-1 / 防时冲-2 / 防超卖-4）；"no slot" 哨兵区分"不占时间"与"缓存没接上"(nil→-5)
  - [x] 退课 Lua 原子回补名额 + 释放已选/时间槽标记；MySQL 条件更新(status=1→2)防双退
  - [x] 启动初始化 initCourseCache：setIfAbsent 灌 stock/cap/course_slot（重启不重置库存=防超卖）+ ensureCourseStudents 建空集（修首单恒-3 bug）
  - [x] 提交点架构：Redis 裁决 → MySQL 同步落库 = 提交点 → 幂等(uk 唯一索引 + DuplicateKeyException 当成功) → 原子补偿(compensate 复用 cancel 脚本) → 对账兜底(设计留存，待实现)
  - [x] 防穿透：Valid_CourseIds 本地精确白名单（200课量级，比布隆零误判）+ -3/-5 缓存自愈 reloadCourseCache（DCL 三重检查 + 完整性看三 key stock/course_slot/course_students + count 重算防超卖）
  - [x] 日志规范：热路径 DEBUG / 异常 WARN / 严重 ERROR / INFO 只给低频节点；慢调用告警 Redis>50ms、MySQL>100ms
  - [x] 全链路追踪：TraceIdFilter(MDC + X-Trace-Id 响应头) + logging.pattern.console 输出 [%X{traceId}]
  - [ ] P2 滑动窗口限流 AOP @RateLimit（注解+切面骨架已建，待完成）
  - [ ] P3 RocketMQ 异步通知（抢课成功 → 微信/站内信）+ 消费端幂等去重 + MDC 透传
  - [x] 对账任务 CourseReconcileJob @Scheduled 5min：方向1(Redis 有标 DB 无行→复用 cancel 脚本清幽灵标记+还名额，用户看到的失败是终局不翻案)、方向2(DB 有行 Redis 无标→补标记 + 按"容量-已选"重算库存)；复用 ShareJob 模式
  - [ ] JMeter 压测报告（QPS / 延迟 / 超卖验证：500 人抢 10 座）
  - [x] 抢课页浏览列表：CourseView/Impl + GET /api/v1/course
    - [x] 静态/动态分离：静态(课名/老师等)走 Caffeine LoadingCache refreshAfterWrite(30s)+expireAfterWrite(24h)硬兜底（刷新失败留旧值=读脏降级）；动态(已选人数/我已选)走 Redis，一次 Lua 批量取本页 SCARD+SISMEMBER
    - [x] 降级三级：Redis 挂→DB GROUP BY 兜底；DB 也挂→selectedCount=-1(名额紧张)，库存绝不返回旧数字；只有静态弱一致数据才允许读脏
    - [x] 关键词搜索(className 课程名 / teacherName 老师名，StrUtil 忽略大小写)；CourseVO 补 id + ifchoosen(前端置灰)
- [ ] 通知模块（点赞/评论提醒，WebSocket/SSE）
- [ ] 私信模块（WebSocket 双向，可选）
（已砍：成绩绩点、课程表 —— 爬取难验证、技术一般、面试回报率低）

## 模块规划（4 核心）
| 模块 | 重点技术 | 备注 |
|------|---------|------|
| 抢课 ★ | Redis 原子扣减(防超卖)、幂等(防重复)、滑动窗口限流+AOP(防刷)、@Async 通知 | 核心，最重，集中展示高并发 |
| 日常分享 | OSS(MinIO)图片/视频上传(分片)、前端直传(预签名)、评论、点赞/点踩(Redis计数延迟落库)、分页热度列表 | 内容社区，覆盖对象存储+高频交互 |
| 通知 | WebSocket/SSE 实时推送（点赞/评论提醒）、已读未读 | 很多人不会，面试加分 |
| 私信 | WebSocket 双向聊天、会话+消息表、未读 | 可选，工作量较大 |

砍掉：公告(无技术含量)、发帖(与日常分享重叠)、评价课程(降级为课程热度，点赞数并入抢课)、关注(社交关系链/Feed流，复杂度高，作演进方向)、成绩绩点+课程表(爬取难验证、技术一般、面试回报率低)。

## 模块待办纵深点（做到对应模块时必须提醒用户）
（日常分享模块已全部完成，含 OSS 前端直传分片 + 断点续传）

## 演进方案（设计留档，暂不实现）
- **MD5 秒传（日常分享）**：前端先算文件 md5 → 后端查表（t_file：file_md5 唯一索引 + url + 引用计数）→ 命中则直接返回已存在的 url，跳过整段上传。真实大厂去重/秒传原理，面试可讲。注意点：md5 碰撞理论风险（可用 sha256 增强）、文件被多用户引用需引用计数（删除时减引用，归零才真删）。
- **抢课模块**：完成后用 JMeter 压测出报告（QPS / 延迟 / 超卖验证：模拟 500 人抢 10 个名额），面试才有数据背书
- **通知/私信模块**：WebSocket 用 Spring 自带（非 Netty）——连接量/自定义协议到达百万级才考虑 Netty
- **AOP 日志切面**：@RateLimit(注解+切面已建)之外，加统一请求日志切面（时间/耗时/参数），让 AOP 有 2 个真实落点；异步线程/MQ 消费端补 MDC 透传(TaskDecorator)

## 关键设计决策
- 认证：**Session + Cookie（非 JWT）**——可服务端踢人/注销；JWT 无状态做不到。单机 HttpSession 够用，多实例可平滑迁到 Redis Session
- 登录方式：**学号 + 学校统一认证**（Demo 阶段模拟，后续对接真实学校系统）
- Security 角色：**门卫**（自定义 SessionAuthFilter + 业务自管会话），非 UserDetailsService
- 统一响应：`status == 0` 成功，错误码分段（40xxx 客户端 / 50xxx 服务端）
- HTTP 语义：401/404 用真实状态码，业务错误 200 + 业务码
- 抢课：**裁决在 Redis(Lua 原子扣减)**，**提交点在 MySQL(同步落库，通知一定在落库后发)**，**幂等靠唯一索引**，**补偿复用退课脚本**，对账兜底残余窗口(5min)
- 抢课日志：热路径只让 WARN/ERROR 出声（DEBUG 记细节、慢调用 >50ms/>100ms 告警）；全链路 = 每请求 traceId 进 MDC + 响应头回写，日志 pattern 输出 [%X{traceId}]
- 防穿透：200 课量级用**本地精确白名单**（非布隆，零误判零内存代价）；攻击分两类——假 id 洪水白名单挡、真 id 洪水靠 @RateLimit 限流挡
- 缓存降级红线：**强一致数据（库存）绝不返回旧值**——只能"重算准"或"模糊信号(-1 名额紧张)"；只有弱一致静态数据才允许读脏
- 列表缓存：静态/动态分离；**静态 Caffeine refreshAfterWrite 单飞刷新**（200课单实例，比"L1 Caffeine+L2 Redis+DB"两级更省——L2 在单实例是纯负债）

## 技术点取舍（面试的"技术判断力"）
- 用：Caffeine(refreshAfterWrite 单飞/读脏降级)、Redis(Lua 原子扣减/幂等/缓存自愈/批量 SCARD+SISMEMBER)、同步落库提交点+原子补偿、滑动窗口限流+AOP @RateLimit、RocketMQ(异步通知 **非**削峰)、traceId 全链路、OSS 预签名(MinIO) 前端直传、Spring WebSocket
- 砍（能讲清理由）：MQ削峰(量级算过：峰值 ~3000/s，单机 MySQL 同步就够)、布隆过滤器(200课用本地白名单平替零误判)、ShardingSphere、Gateway、zstd、Pod监控、hprof、Jedis Pipeline
- 可选：IDEA Profiler、GC 日志

## 常用命令
- 启动依赖环境：`docker compose up -d`
- 执行迁移：`docker exec -i scuplus-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 scuplus < src/main/resources/db/migration/V3__course_grab.sql`（务必加 --default-character-set=utf8mb4 防中文乱码）
- 编译：`mvnw.cmd compile`
- 运行：`mvnw.cmd spring-boot:run`
- 测试：`mvnw.cmd test`
