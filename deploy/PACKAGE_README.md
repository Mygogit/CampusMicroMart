============================================================
  校园二手交易平台 - 打包与部署方案
============================================================

## 📦 方式一：JAR 包部署（Windows 直接运行）

前提：JDK 17+, MySQL 8.0, Redis 7.0 已安装且运行

### 打包
  双击运行: package-jar.bat
  产物在: dist\libs\*.jar

### 部署
  1. 将 dist\ 目录复制到目标机器
  2. 确保 MySQL 已导入 sql\init.sql 和 sql\init_admin.sql
  3. 双击运行: deploy\start-all.bat
  4. 前端访问: http://目标IP

## 🐳 方式二：Docker Compose 部署（推荐）

### 构建镜像（WSL2 环境）
  # 逐个构建（稳定）
  wsl docker build -f Dockerfile.user-service -t user-service:latest .
  wsl docker build -f Dockerfile.product-service -t product-service:latest .
  wsl docker build -f Dockerfile.order-service -t order-service:latest .
  wsl docker build -f Dockerfile.payment-service -t payment-service:latest .
  wsl docker build -f Dockerfile.gateway -t campus-gateway:latest .

  # 或一键构建
  wsl bash build-all-images.sh

### 导出镜像（发给他人）
  mkdir deploy\images
  wsl docker save user-service:latest -o deploy\images\user-service.tar
  # ... 逐个导出

### 接收方导入
  wsl docker load -i deploy\images\user-service.tar
  # ... 逐个导入

### 启动
  # 完整部署（含 MySQL, Redis, 5个服务, 前端, 监控）
  wsl docker compose -f docker-compose-prod.yml up -d

  # 前端访问: http://localhost

### 停止
  wsl docker compose -f docker-compose-prod.yml down

## 📂 文件清单

├── package-jar.bat              JAR 打包脚本
├── build-docker.bat             Docker 构建脚本(Windows)
├── build-all-images.sh          Docker 构建脚本(WSL/Linux)
├── docker-compose-prod.yml      生产环境编排文件
├── deploy/
│   ├── nginx.conf               前端 Nginx 配置
│   └── images/                  导出的 Docker 镜像
└── dist/                        打包产物
    ├── libs/                    5个 JAR 包
    └── frontend/                前端静态文件
