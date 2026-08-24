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
- Lombok + Hutool + MapStruct + SpringDoc（OpenAPI）

## 本地环境
- MySQL: `localhost:3306`，用户 `root` / 密码 `root123456`，库 `scuplus`（docker compose）
- Redis: `localhost:6379`
- MinIO: `localhost:9000`(API) / `localhost:9001`(控制台)，账号 `minioadmin`/`minioadmin`
- 应用: `localhost:8080`，Swagger: `/swagger-ui.html`
- 构建: `mvnw.cmd compile` / `mvnw.cmd spring-boot:run` / `mvnw.cmd test`

## 当前进度（2026-08-16）
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
  - [ ] 分片上传/断点续传（视频场景，讲原理 + 接口占位）
- [ ] 成绩绩点模块（绩点计算+单测）
- [ ] 课程表模块（分层缓存 + 缓存三兄弟纵深点）
- [ ] 抢课模块 ★（原子扣减/幂等/限流/@Async/压测纵深点）

## 模块规划（4 核心）
| 模块 | 重点技术 | 备注 |
|------|---------|------|
| 抢课 ★ | Redis 原子扣减(防超卖)、幂等(防重复)、滑动窗口限流+AOP(防刷)、@Async 通知 | 核心，最重，集中展示高并发 |
| 日常分享 | OSS(MinIO)图片/视频上传、前端直传(预签名)、评论、点赞/点踩(Redis计数延迟落库)、分页热度列表 | 内容社区，覆盖对象存储+高频交互 |
| 成绩绩点 | 绩点计算(纯逻辑)、单元测试 | 可单测，面试友好 |
| 课程表 | Caffeine+Redis 分层缓存、周次解析 | 查询热点 |

砍掉：公告(无技术含量)、发帖(与日常分享重叠)、评价课程(降级为课程热度，点赞数并入抢课)、关注(社交关系链/Feed流，复杂度高，作演进方向)。

## 模块待办纵深点（做到对应模块时必须提醒用户）
- **日常分享模块（剩余）**：视频大文件的分片上传/断点续传讲原理 + 接口占位（init/uploadPart/complete 三接口）
- **抢课模块**：完成后用 JMeter 压测出报告（QPS / 延迟 / 超卖验证：模拟 500 人抢 10 个名额），面试才有数据背书
- **课程表模块**：显式处理缓存三兄弟——空值缓存(防穿透)、互斥锁/逻辑过期(防击穿)、随机 TTL+多级缓存(防雪崩)
- **AOP 日志切面**：抢课模块的 @RateLimit 之外，加统一请求日志切面（时间/耗时/参数），让 AOP 有 2 个真实落点

## 关键设计决策
- 认证：**Session + Cookie（非 JWT）**——可服务端踢人/注销；JWT 无状态做不到。单机 HttpSession 够用，多实例可平滑迁到 Redis Session
- 登录方式：**学号 + 学校统一认证**（Demo 阶段模拟，后续对接真实学校系统）
- Security 角色：**门卫**（自定义 SessionAuthFilter + 业务自管会话），非 UserDetailsService
- 统一响应：`status == 0` 成功，错误码分段（40xxx 客户端 / 50xxx 服务端）
- HTTP 语义：401/404 用真实状态码，业务错误 200 + 业务码

## 技术点取舍（面试的"技术判断力"）
- 用：Redis 原子扣减、幂等、滑动窗口限流、AOP @RateLimit、@Async、并发、OSS 预签名(MinIO) 前端直传
- 砍（能讲清理由）：MQ削峰、布隆过滤器、ShardingSphere、Gateway、zstd、Pod监控、hprof、Jedis Pipeline
- 可选：IDEA Profiler、GC 日志

## 常用命令
- 启动依赖环境：`docker compose up -d`
- 编译：`mvnw.cmd compile`
- 运行：`mvnw.cmd spring-boot:run`
- 测试：`mvnw.cmd test`
