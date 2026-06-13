# 微服务化校园二手交易平台

## 项目简介

基于Spring Cloud Alibaba构建的微服务化校园二手交易平台，具有高可用、易扩展、可观测的特点。

## 技术栈

- **后端框架**: Spring Boot 3.2 + Spring Cloud Alibaba
- **服务注册发现**: Nacos
- **服务治理**: Sentinel
- **分布式事务**: Seata (待集成)
- **消息队列**: RocketMQ (待集成)
- **数据库**: MySQL 8.0 + Redis 7.0
- **API网关**: Spring Cloud Gateway
- **可观测性**: OpenTelemetry + Prometheus + Grafana
- **API文档**: Knife4j + OpenAPI 3.0
- **ORM**: MyBatis-Plus

## 项目结构

```
campus-micro-mart/
├── campus-common/          # 公共模块
├── campus-gateway/         # API网关服务 (8080)
├── product-service/        # 商品服务 (8081)
├── order-service/          # 订单服务 (8082)
├── payment-service/        # 支付模拟服务 (8083)
├── sql/                    # 数据库脚本
├── docker-compose.yml      # Docker Compose配置
└── pom.xml                 # 父项目POM
```

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.9+
- Docker + Docker Compose (可选)

### 1. 启动基础设施

```bash
# 使用Docker Compose启动所有依赖
docker-compose up -d

# 或手动启动已有的WSL2中的Redis
```

### 2. 初始化数据库

执行 `sql/init.sql` 脚本创建数据库和表结构。

### 3. 启动Nacos

访问: http://localhost:8848/nacos (默认账号密码: nacos/nacos)

### 4. 启动服务

按以下顺序启动服务:

1. campus-gateway
2. product-service
3. order-service
4. payment-service

### 5. 访问API文档

- 商品服务: http://localhost:8081/doc.html
- 订单服务: http://localhost:8082/doc.html
- 支付服务: http://localhost:8083/doc.html

## 模块说明

### campus-common
公共模块，包含:
- 统一响应结果 Result
- Redis常量 RedisConstant
- 通用工具类

### campus-gateway
API网关服务，提供:
- 服务路由
- 负载均衡
- 统一入口 (8080端口)

### product-service
商品服务，提供:
- 商品CRUD
- 商品分类管理
- 库存管理

### order-service
订单服务，提供:
- 订单创建与管理
- 订单状态流转
- Feign调用商品服务

### payment-service
支付模拟服务，提供:
- 支付流程模拟
- 支付状态管理

## 可观测性

### Prometheus
访问: http://localhost:9090

### Grafana
访问: http://localhost:3000 (默认账号密码: admin/admin)

### Sentinel
访问: http://localhost:8858

## 开发指南

### 环境要求
- JDK 17
- Maven 3.9+
- IDE: IntelliJ IDEA (推荐)

### 代码规范
- 遵循阿里巴巴Java开发规范
- 使用Lombok简化代码
- MyBatis-Plus提供CRUD基础能力

## License
MIT License

---

## 📦 打包部署

详见 `deploy/PACKAGE_README.md`

### 快速打包（JAR）
```bash
# Windows 双击运行
package-jar.bat
```

### Docker 部署
```bash
# WSL2 环境构建镜像
wsl bash build-all-images.sh

# 启动完整环境
wsl docker compose -f docker-compose-prod.yml up -d
```
