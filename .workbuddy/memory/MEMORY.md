# CampusMicroMart 项目记忆

## 项目概况
- 微服务化校园二手交易平台
- 技术栈：Spring Boot 3.2.5 + Spring Cloud + Nacos + Seata + Sentinel + RocketMQ
- 后端：user-service(8084), product-service(8081), order-service(8083), payment-service(8085), gateway(8080)
- 前端：Node.js/Vite SPA + Nginx
- 部署：支持 standalone（嵌入式 MariaDB+Redis+JDK）和 Docker Compose

## 团队
- 单人开发，初级水平
- 最关注：服务稳定性

## 关键约定
- standalone 模式密码：campus123
- dev 模式密码：root
- JVM 内存：standalone/dev 模式每个服务 -Xms128m -Xmx256m，prod 模式 -Xms256m -Xmx512m
- 追踪默认关闭（sampling 0.0），避免 Jaeger 未启动时日志轰炸
- Seata 默认关闭（standalone 不依赖 Seata）

## 已知问题
- 中文路径 `E:\数据\CampusMicroMart` 会导致 MariaDB 11.4 初始化失败，需自动切换到 `C:\CampusMart\data`
- FrontendServer 有时因端口 80 被占用而启动失败
