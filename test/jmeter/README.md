# CampusMicroMart JMeter 三场景性能测试指南

> 目标：产出系统性能**实测数据**（响应时间、TPS、错误率），
> 并可与 Grafana 监控数据互相印证。所有接口均基于当前 standalone 部署实测通过（2026-10-12）。

## 一、目录内容

| 文件 | 说明 |
|---|---|
| `plan-read.jmx` | 场景一：商品检索（读，公开接口） |
| `plan-login.jmx` | 场景二：用户登录（认证链路，CSV 参数化） |
| `plan-order.jmx` | 场景三：创建订单（写，核心交易链路） |
| `run-perf-test.ps1` | CLI 运行器：一键跑场景并生成 HTML 报告 |
| `prepare-perf-data.sql` | 压测数据准备/清理 SQL（专用大库存商品 + 测试订单清理） |
| `../data/test-users.csv` | 30 个测试用户（密码统一 `campus123`，来自种子数据） |

> 旧文件 `order-create-test.jmx` 已废弃（端口 8080 错误、下单路径 `/api/order/create` 不存在、
> 登录与下单分属不同线程组导致 Token 不共享），保留仅作历史参考。

## 二、环境准备（一次性）

1. **安装 JMeter 5.6.3**（本机未安装）：
   - 下载 <https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.zip>，解压到如 `C:\apache-jmeter-5.6.3`
   - 设置环境变量 `JMETER_HOME=C:\apache-jmeter-5.6.3`（运行脚本会自动探测常见路径）
   - 依赖 JDK 17，本机已具备（`dist-standalone\jdk` 或系统 JDK 均可）
2. **确认服务已启动**：`start-background-wmi.ps1`，网关入口 `http://localhost:8090`
3. **准备压测数据**（已完成，重装数据库时重跑）：
   ```bash
   # 已执行：插入专用压测商品 id=1031（在售/已审核/库存 999999/价格 0.01）
   dist-standalone\mariadb\bin\mysql.exe -h127.0.0.1 -P3306 -uroot -pcampus123 campus_product < test\jmeter\prepare-perf-data.sql
   ```
   > 下单必须打到**有库存的在售商品**。普通种子商品库存仅 1，几单就售罄，
   > 所以统一打专用商品 1031（`-JPRODUCT_ID` 可改）。

## 三、三场景设计

| 场景 | 接口 | 鉴权 | 默认参数 | 考察链路 |
|---|---|---|---|---|
| 一 商品检索 | `GET /api/product/search?page=1&size=10` | 无 | 100 并发 × 30s ramp × 10 轮 | 网关 → product-service → MyBatis-Plus 分页 → MariaDB |
| 二 用户登录 | `POST /api/user/login` | 无 | 50 并发 × 30s ramp × 5 轮 | user-service → BCrypt 校验 → JWT 签发 → Redis Token 缓存 |
| 三 创建订单 | `POST /api/order` | Bearer Token | 50 并发 × 30s ramp × 5 轮 | 网关鉴权 → order-service → Feign 扣库存 → 订单双表写入 |

实现要点（都已按真实契约处理）：
- **下单路径是 `/api/order`**（OrderController `@RequestMapping("/order")` + 无参 `@PostMapping`），不是 `/api/order/create`
- **请求体必须带 `userId`**：`CreateOrderDTO.userId` 有 `@NotNull` 校验；服务端实际买家以 JWT 为准，故脚本用 JSR223 从登录返回的 JWT 载荷解出 `userId` 填入（base64url 自动补 pad）
- **登录与下单在同一线程组**：JMeter 变量按线程隔离，分两个线程组会导致 `${token}` 取不到
- **断言查 body 的 `"code":200`**：平台业务错误也返回 HTTP 200（如库存不足返回 `{"code":400,...}`），只断言 HTTP 状态码会把失败算成成功
- CSV 用户 `shareMode.all` + 循环复用，30 个用户足够 250+ 次登录

## 四、运行方式

### 方式 A：CLI 一键跑（推荐，产出正式数据）

```powershell
# 全场景顺序跑（读→登录→下单），结果落 test\results\<时间戳>\<场景>\html\index.html
powershell -ExecutionPolicy Bypass -File test\jmeter\run-perf-test.ps1

# 只跑下单场景，100 并发、每线程 10 轮
powershell -ExecutionPolicy Bypass -File test\jmeter\run-perf-test.ps1 -Scenario order -OrderThreads 100 -Loops 10

# 跑完自动打开第一份报告
powershell -ExecutionPolicy Bypass -File test\jmeter\run-perf-test.ps1 -Scenario read -OpenReport
```

### 方式 B：GUI 手动跑（适合截图放进论文）

```powershell
# 必须在项目根目录启动（CSV 相对路径依赖工作目录）
C:\apache-jmeter-5.6.3\bin\jmeter.bat
```
打开 `test\jmeter\plan-*.jmx` → 右键线程组「启动」→ 临时添加监听器「察看结果树 / 聚合报告」即可。
（JMX 内刻意不放监听器：CLI 模式用 `-l result.jtl -e -o html` 生成报告，监听器会白耗内存。）

### 梯度加压（找饱和点，论文「压力测试」用）

```powershell
foreach ($n in 50, 100, 200, 400) {
  powershell -ExecutionPolicy Bypass -File test\jmeter\run-perf-test.ps1 -Scenario order -OrderThreads $n -Loops 5 -Ramp 30
}
```
每次结果独立目录，对比 TPS / P95 / 错误率随并发的变化，即可得出系统容量拐点。

## 五、论文取数指南

HTML 报告（`html\index.html`）的「Statistics」表格直接给出论文需要的全部字段：

| 论文指标 | 报告字段 | 说明 |
|---|---|---|
| 样本数 | #Samples | 有效请求数 |
| 平均响应时间 | Average | ms |
| 中位数 / P90 / P95 / P99 | 报告内 Percentiles 图表 | 延迟分位 |
| 吞吐量 | Throughput | **注意单位是 /秒还是 /分钟**，换算成 TPS（req/s）再写论文 |
| 错误率 | Error % + KO | 断言失败=KO，与业务失败一一对应 |

建议同时做三件事让数据更完整：
1. **压测期间开 Grafana**（http://localhost:3001，「订单服务监控 / SLO 验证仪表盘」）：截图 QPS、P95、成功率曲线，与 JMeter 数据互相印证——两边数字应同量级（Grafana 看服务端、JMeter 看客户端）
2. **记录梯度表**：并发数 50/100/200/400 × (TPS、P95、错误率) 一张表，是性能分析章节的核心证据
3. **同机测试要声明**：JMeter 与 8 个服务同机运行，客户端本身消耗 CPU，论文「测试环境」一节需注明这一点（数据反映单机容量，不代表分布式集群容量）

## 六、压测后清理（避免污染演示数据）

```sql
-- 库 campus_order：删除测试订单（先查后删）
SELECT COUNT(*) FROM t_order WHERE remark = 'JMeter性能测试订单';
DELETE oi FROM t_order_item oi JOIN t_order o ON oi.order_id = o.id WHERE o.remark = 'JMeter性能测试订单';
DELETE FROM t_order WHERE remark = 'JMeter性能测试订单';
-- 库 campus_product：删除专用压测商品（保留也行，库存会自动回补少量）
DELETE FROM t_product WHERE name = 'JMeter压测专用商品';
```

## 七、常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 下单全部 KO，message「用户ID不能为空」 | 用的不是本目录的 plan-order.jmx（旧计划缺少 userId 字段） |
| 下单全部 KO，message「商品不在售 / 扣减库存失败」 | PRODUCT_ID 指向了库存 1 的普通商品，改用 1031 或先执行准备 SQL |
| 登录全部 KO | CSV 路径解析失败：确认从项目根目录运行，或 `-JCSV_PATH=E:\数据\CampusMicroMart\test\data\test-users.csv` |
| 错误率 100% 且 connection refused | 服务没起（start-background-wmi.ps1）或端口不是 8090 |
| JWT 过期（7 天）导致偶发 401 | 每个线程每轮都重新登录，正常不会碰到；批量压测间隔太久时重跑即可 |
