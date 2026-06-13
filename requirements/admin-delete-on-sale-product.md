# 需求规格说明：管理员删除在售商品

> **文档编号**：PRD-ADMIN-DEL-001  
> **版本**：v1.0  
> **创建日期**：2026-06-13  
> **所属项目**：微服务化校园二手交易平台（CampusMicroMart）  
> **关联服务**：product-service、order-service、campus-gateway  

---

## 1. 需求概述

### 1.1 背景

当前平台已有"商品审核"（管理员审批商品上架）和"卖家下架/取消"功能，但缺少管理员强制删除违规在售商品的能力。在校园二手交易场景中，管理员需要能够及时清理违规商品（如发布违禁品、虚假信息、恶意刷单等），以维护平台交易秩序和校园安全合规要求。

### 1.2 目标

为**管理员（ADMIN 角色）**提供对**在售商品（product_status = 1，即 ON_SALE）**的强制删除能力，包含完整的操作确认机制、业务约束校验、关联数据联动处理、操作日志记录，以及卖家通知。

### 1.3 适用范围

- **操作角色**：仅限 `ADMIN` 角色的已认证管理员
- **目标商品**：`product_status = ON_SALE (1)` 且 `audit_status = APPROVED (1)` 且在逻辑上未被删除（`deleted = 0`）的商品
- **不在范围内**：已售出商品（SOLD）通过订单纠纷流程处理；待审核商品（PENDING）通过审核拒绝流程处理；已下架（OFF_SHELF）和已取消（CANCELLED）商品已有对应处理流程

---

## 2. 功能需求

### FR-1：管理员查看所有在售商品列表

**描述**：管理员能够在管理后台查看当前平台全部在售商品，并支持搜索和筛选。

**前端入口**：管理后台 Dashboard → 新增"商品管理"快捷入口（或左侧导航新增"商品管理"菜单项），路由 `/admin/products`，需要 `requiresAdmin: true` 路由守卫。

**后端接口**：`GET /api/product/admin/list`

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 10 | 每页条数 |
| keyword | String | 否 | - | 模糊搜索关键词（匹配商品名称、描述、课程代码） |
| userId | Long | 否 | - | 按卖家 ID 筛选 |
| dormitory | String | 否 | - | 按宿舍楼栋筛选 |

**响应格式**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1001,
        "name": "高等数学（第七版）",
        "description": "九成新，几乎没写过字",
        "price": 25.00,
        "stock": 1,
        "productStatus": 1,
        "auditStatus": 1,
        "categoryId": 3,
        "userId": 42,
        "userName": "张三",
        "images": "[\"url1\",\"url2\"]",
        "courseCode": "MATH101",
        "dormitory": "北苑3号楼",
        "createTime": "2026-06-10 14:30:00",
        "updateTime": "2026-06-10 14:30:00"
      }
    ],
    "total": 156,
    "size": 10,
    "current": 1,
    "pages": 16
  }
}
```

**权限要求**：`ADMIN` 角色

**注意**：该接口不同于面向普通用户的 `/product/list`（后者只返回 `ON_SALE + APPROVED` 的商品）。管理员列表接口需要额外返回卖家信息（`userName`），以便管理员进行违规判断。

---

### FR-2：管理员删除在售商品

#### FR-2.1 核心删除流程

**描述**：管理员对指定在售商品执行删除操作，系统执行一系列业务校验后，执行逻辑删除。

**后端接口**：`DELETE /api/product/{id}`（复用现有端点，增强业务逻辑）

**前置校验（按顺序执行，任意一步不通过则拒绝操作并返回对应错误）**：

| 序号 | 校验项 | 条件 | 不通过时的响应 |
|------|--------|------|----------------|
| 1 | 操作者身份 | `UserContext.getCurrentUserId()` 非空，且角色为 `ADMIN` | `403 FORBIDDEN` — "仅管理员可执行此操作" |
| 2 | 商品是否存在 | `productService.getById(id)` 不为 null，且 `deleted = 0` | `404 NOT FOUND` — "商品不存在或已被删除" |
| 3 | 商品是否在售 | `product.getProductStatus() == ON_SALE (1)` | `400 BAD_REQUEST` — "仅可删除在售商品，当前状态：{状态描述}" |
| 4 | 是否存在进行中订单 | 调用 order-service 查询是否有与此商品关联的、状态为 `WAITING_PAY(0)` 或 `PAID(1)` 或 `SHIPPED(2)` 的订单 | `409 CONFLICT` — "该商品存在 {N} 个进行中的订单，无法直接删除。建议先处理关联订单。" |
| 5 | 重复删除防护 | 使用 Redis 分布式锁（key=`product:delete:lock:{id}`，TTL=30s）防止并发重复删除 | `409 CONFLICT` — "该商品正在被处理，请稍后重试" |

**执行删除**（所有校验通过后）：

1. **获取分布式锁**（Redis `SETNX`，key=`product:delete:lock:{id}`）
2. **执行逻辑删除**：`productService.removeById(id)`（MyBatis-Plus 的 `@TableLogic` 自动将 `deleted` 置为 1，同时触发 `updateTime` 自动填充）
3. **发送卖家通知**：通过 RocketMQ 发送异步消息（Topic: `PRODUCT_DELETED`），包含 `productId`、`productName`、`adminId`、`reason`、`deleteTime`
4. **记录操作日志**：通过 OpenTelemetry 添加 Span 事件，记录 `admin.id`、`product.id`、`product.name`、`product.userId`（卖家）、`reason`、`timestamp`、操作结果
5. **释放分布式锁**

**响应格式（成功）**：

```json
{
  "code": 200,
  "message": "商品 [高等数学（第七版）] 已被管理员删除",
  "data": null
}
```

#### FR-2.2 删除原因记录

**描述**：管理员删除商品时必须填写删除原因，以便审计追溯和卖家申诉处理。

**请求参数扩展**：`DELETE /api/product/{id}` 请求体（JSON）：

```json
{
  "reason": "违规商品：发布盗版教材"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| reason | String | 是 | 删除原因，1-500 字符 |

- 若 `reason` 为空或空白，返回 `400 BAD_REQUEST` — "请填写删除原因"
- 若超过 500 字符，返回 `400 BAD_REQUEST` — "删除原因长度不能超过 500 字符"

#### FR-2.3 关联订单处理策略

**描述**：当被删除商品存在进行中订单时的处理逻辑。

**订单状态映射**：

| 订单状态 | 状态值 | 处理策略 |
|----------|--------|----------|
| WAITING_PAY（待支付） | 0 | 自动取消订单（状态→CANCELLED），退还冻结的保证金（若支付服务已冻结），通知买家"商品已被管理员下架" |
| PAID（已支付） | 1 | **阻断删除**，提示管理员"存在已支付订单，请先联系买卖双方协商处理" |
| SHIPPED（已发货） | 2 | **阻断删除**，提示管理员"存在已发货订单，请等待交易完成后处理" |
| COMPLETED（已完成） | 3 | 允许删除（交易已完结，不影响任何进行中业务） |
| CANCELLED（已取消） | 4 | 允许删除 |

**阶段说明**：本期 V1.0 仅实现"阻断策略"——存在进行中订单（WAITING_PAY / PAID / SHIPPED）时直接拒绝删除并提示数量。WAITING_PAY 订单的自动取消和通知逻辑留到 V1.1 迭代。

---

### FR-3：前端操作确认机制

#### FR-3.1 确认对话框

**描述**：管理员在前端点击"删除"按钮后，必须经过两步确认才能执行删除。

**第一步 — 确认对话框**（使用 Element Plus `ElMessageBox.confirm`）：

- 标题："确认删除商品"
- 内容提示："确定要删除商品「{商品名称}」吗？此操作将永久下架该商品，且不可恢复。请填写删除原因："
- 包含一个必填的 `el-input type="textarea"` 文本框，用于填写删除原因
- 按钮：`取消`（取消操作）、`确认删除`（type="danger"）

**第二步 — 二次确认**（仅在第一步确认后触发）：

- 如果该商品存在关联的 **已完成** 或 **已取消** 订单，弹出提示："该商品有 {N} 个历史订单记录。删除商品不会影响已完成交易，但商品信息将不再对买家可见。确定继续？"
- 按钮：`取消`、`仍然删除`

**前端校验**：
- 删除原因不能为空（在确认按钮点击时校验，而非服务端校验后）
- 删除原因长度不超过 500 字符（实时字数统计显示）

#### FR-3.2 删除结果反馈

- **成功**：`ElMessage.success("商品「{商品名称}」已被删除")`，并从列表中移除该商品行
- **失败（业务拒绝）**：`ElMessage.error(后端返回的具体错误消息)`，如"该商品存在2个进行中的订单，无法直接删除"
- **失败（网络/系统错误）**：`ElMessage.error("网络异常，请稍后重试")`
- **超时**：设置请求超时时间为 10 秒

---

### FR-4：权限控制

#### FR-4.1 网关层认证（已有，复核）

**位置**：`campus-gateway` 的 `JwtAuthFilter`

`DELETE /api/product/**` 不在 `PUBLIC_PATHS` 白名单内，因此：
- 无 Token 请求 → `401 UNAUTHORIZED` — "缺少认证令牌"
- 无效/过期 Token → `401 UNAUTHORIZED` — "无效的认证令牌" / "令牌已过期"

#### FR-4.2 服务层授权（增强）

**位置**：`NacosPermissionLoader.java`（已有配置，需确认）

```java
// 已有配置（第121行）
rules.add(new PermissionRule("/product/**", "DELETE", Set.of("ADMIN"), false));
```

此配置确保：只有携带 `ROLE_ADMIN` 权限的已认证用户才能访问 DELETE 方法的 `/product/**` 路径。普通用户（STUDENT 角色）访问 → `403 FORBIDDEN`。

#### FR-4.3 业务层二次校验（新增）

在 `ProductController.delete()` 方法内，通过 `UserContext.getCurrentUserId()` 确认操作者身份，确保即便网关层权限被绕过，业务层仍有最后一道防线。

---

## 3. 业务逻辑约束

### BC-1：商品状态约束

| 商品状态 | 状态值 | 是否允许管理员删除 | 说明 |
|----------|--------|-------------------|------|
| PENDING（待审核） | 0 | **否** | 应通过审核拒绝流程处理 |
| ON_SALE（在售） | 1 | **是** | 本需求的核心场景 |
| SOLD（已售出） | 2 | **否** | 交易已完结，走订单纠纷流程 |
| OFF_SHELF（已下架） | 3 | **否** | 卖家已自行下架，无需管理员干预 |
| CANCELLED（已取消） | 4 | **否** | 卖家已取消发布 |

### BC-2：幂等性约束

- 重复删除同一商品：第二次请求返回 `404 NOT FOUND` — "商品不存在或已被删除"
- Redis 分布式锁确保同一商品在同一时刻只有一个删除操作被执行

### BC-3：数据一致性约束

- 删除商品不影响已有订单记录的历史数据（订单表保留对 `product_id` 的引用，前端展示时标记"商品已下架"）
- 支付记录保持不变
- 用户信誉/信用评分不因商品被管理员删除而自动扣减（后续可扩展违规扣分机制）

### BC-4：数据库约束

- MyBatis-Plus `@TableLogic` 逻辑删除：`deleted` 字段从 `0` 变为 `1`
- 不触发物理删除（数据库行保留，仅标记为删除）
- `update_time` 自动更新为删除操作时间
- 已有 `UNIQUE KEY` 或 `INDEX` 约束不被破坏

---

## 4. 非功能需求

### NFR-1：性能要求

| 指标 | 要求 |
|------|------|
| 删除操作响应时间（P95） | < 500ms（不含网络延迟） |
| 在售商品列表查询响应时间（P95） | < 200ms（含分页） |
| 关联订单查询响应时间（P95） | < 300ms |
| 并发删除支持 | 不同商品可并发删除；同一商品受分布式锁保护 |

### NFR-2：安全要求

- 敏感操作（删除）100% 记录操作日志（含管理员 ID、商品 ID、删除原因、时间戳）
- 删除原因不包含在 URL 中（通过 Request Body 传输，避免被代理/网关日志明文记录）
- 不支持批量删除（防止误操作导致大规模数据删除）

### NFR-3：可观测性要求

- 通过 OpenTelemetry SDK 在删除操作的 Span 上添加自定义属性：
  ```
  product.deleted.id = {商品ID}
  product.deleted.name = {商品名称}
  product.deleted.by_admin = {管理员ID}
  product.deleted.reason = {删除原因（脱敏：仅前20字符）}
  product.deleted.result = SUCCESS / FAILED
  ```
- 关键指标：`product_admin_delete_total`（Counter）、`product_admin_delete_duration_seconds`（Histogram）
- Grafana 仪表盘新增"管理员删除商品"监控面板

---

## 5. 接口设计摘要

| 接口 | 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|------|
| 管理员商品列表 | GET | `/api/product/admin/list` | 查看所有在售商品（含卖家信息） | ADMIN |
| 管理员删除商品 | DELETE | `/api/product/{id}` | 删除在售商品（需 Request Body 含 reason） | ADMIN |
| 查询商品关联订单 | GET | `/api/order/by-product/{productId}` | 内部调用，查询某商品关联的订单状态分布 | ADMIN（内部） |

> **注**：`DELETE /api/product/{id}` 复用现有端点路径，但功能需增强为上述完整逻辑。

---

## 6. 前端页面需求

### 6.1 新增页面：`/admin/products`（管理后台 → 商品管理）

**布局**：
- 顶部：搜索栏（关键词输入 + 宿舍筛选 + 搜索按钮 + 重置按钮）
- 中部：商品表格（`el-table`），列定义如下：

| 列名 | 宽度 | 说明 |
|------|------|------|
| ID | 70 | 商品 ID |
| 商品名称 | min-width: 150 | 可点击跳转详情 |
| 价格 | 100 | 格式：¥XX.XX |
| 库存 | 70 | |
| 卖家 | 100 | 显示卖家昵称/用户名 |
| 课程代码 | 100 | 无则显示 "-" |
| 宿舍 | 100 | 无则显示 "-" |
| 发布时间 | 150 | 格式：YYYY-MM-DD HH:mm |
| 操作 | 120 | "查看" + "删除" 按钮 |

- 底部：分页组件（`el-pagination`）

**交互**：
- "删除"按钮点击 → 触发 FR-3.1 确认流程
- 搜索支持防抖（300ms）
- 删除成功后表格自动刷新

### 6.2 导航入口

在管理后台 Dashboard（`/admin/dashboard`）的"快捷操作"区域新增"商品管理"按钮，或在 Layout 组件中为 ADMIN 角色新增"商品管理"侧边导航项。

### 6.3 路由配置

```typescript
{
  path: 'admin/products',
  name: 'AdminProducts',
  component: () => import('../views/admin/ProductManagement.vue'),
  meta: { requiresAuth: true, requiresAdmin: true }
}
```

---

## 7. 错误码定义

| HTTP 状态码 | 错误消息 | 场景 |
|-------------|----------|------|
| 401 | 缺少认证令牌 / 令牌已过期 / 无效的认证令牌 | 网关层鉴权失败 |
| 403 | 仅管理员可执行此操作 | 非 ADMIN 角色调用 |
| 400 | 仅可删除在售商品，当前状态：{状态} | 商品状态不符 |
| 400 | 请填写删除原因 | reason 为空 |
| 400 | 删除原因长度不能超过 500 字符 | reason 超长 |
| 404 | 商品不存在或已被删除 | 商品 ID 无效或已逻辑删除 |
| 409 | 该商品存在 {N} 个进行中的订单，无法直接删除 | 存在 WAITING_PAY/PAID/SHIPPED 状态的关联订单 |
| 409 | 该商品正在被处理，请稍后重试 | Redis 分布式锁已被持有 |
| 500 | 系统内部错误，请稍后重试 | 未预期的服务异常 |

---

## 8. 测试场景

### 8.1 功能测试

| 编号 | 场景 | 前置条件 | 操作 | 预期结果 |
|------|------|----------|------|----------|
| TC-01 | 正常删除在售商品 | 管理员登录，商品状态=ON_SALE，无进行中订单 | 填写删除原因后确认删除 | 200 成功，商品 deleted=1 |
| TC-02 | 非管理员删除 | 学生用户登录 | 调用删除接口 | 403 FORBIDDEN |
| TC-03 | 未登录删除 | 无 Token | 调用删除接口 | 401 UNAUTHORIZED |
| TC-04 | 删除不存在的商品 | 管理员登录 | 传入不存在的商品 ID | 404 NOT FOUND |
| TC-05 | 删除已售出商品 | 管理员登录，商品状态=SOLD | 调用删除接口 | 400 状态不符合 |
| TC-06 | 删除存在进行中订单的商品 | 管理员登录，商品关联的订单状态=WAITING_PAY | 调用删除接口 | 409 CONFLICT |
| TC-07 | 删除已下架商品 | 管理员登录，商品状态=OFF_SHELF | 调用删除接口 | 400 状态不符合 |
| TC-08 | 不填删除原因 | 管理员登录 | 确认删除但不填 reason | 400 "请填写删除原因" |
| TC-09 | 删除原因超长 | 管理员登录 | reason 超过 500 字符 | 400 "删除原因长度不能超过 500 字符" |
| TC-10 | 重复删除同一商品 | 管理员登录，商品已被删除 | 再次调用删除接口 | 404 "商品不存在或已被删除" |
| TC-11 | 商品有关联已完成订单 | 管理员登录，商品关联订单状态=COMPLETED | 删除商品 | 200 成功（仅前端二次确认） |

### 8.2 权限测试

| 编号 | 场景 | 预期结果 |
|------|------|----------|
| TC-12 | 无 Token 访问 | 401 |
| TC-13 | 无效 Token 访问 | 401 |
| TC-14 | 过期 Token 访问 | 401 |
| TC-15 | STUDENT 角色访问 | 403 |
| TC-16 | ADMIN 角色访问 | 200（若其他条件满足） |

### 8.3 性能测试

| 编号 | 场景 | 预期结果 |
|------|------|----------|
| TC-17 | 5 个管理员并发删除不同商品 | 全部成功，无锁冲突 |
| TC-18 | 2 个管理员并发删除同一商品 | 仅一个成功，另一个返回 409 |
| TC-19 | 在售商品列表查询（总商品量 > 10,000 条） | P95 < 200ms |

---

## 9. 实施计划

| 阶段 | 任务 | 预估工作量 | 依赖 |
|------|------|-----------|------|
| 第1步 | 增强 `ProductController.delete()` 业务逻辑 | 0.5 人天 | - |
| 第2步 | 新增 `ProductService.adminDelete(id, adminId, reason)` | 0.5 人天 | 第1步 |
| 第3步 | 新增 order-service 接口 `GET /order/by-product/{productId}`（内部接口，返回关联订单状态统计） | 0.5 人天 | - |
| 第4步 | 新增 `GET /product/admin/list` 管理员商品列表接口 | 0.5 人天 | - |
| 第5步 | 编写单元测试（ProductServiceImpl 删除逻辑） | 0.5 人天 | 第2步 |
| 第6步 | 开发前端页面 `/admin/products` 商品管理页 | 1 人天 | 第4步 |
| 第7步 | 前端确认对话框 + 删除交互 | 0.5 人天 | 第6步 |
| 第8步 | 集成测试 + 端到端测试 | 0.5 人天 | 全部 |
| **合计** | | **4 人天** | |

---

## 10. 附录

### A. 现有相关代码位置

| 组件 | 文件路径 |
|------|----------|
| Product 实体 | `product-service/.../entity/Product.java` |
| ProductController | `product-service/.../controller/ProductController.java`（delete 方法在第107行） |
| ProductService 接口 | `product-service/.../service/ProductService.java` |
| ProductServiceImpl | `product-service/.../service/impl/ProductServiceImpl.java` |
| 权限规则配置 | `campus-common/.../security/NacosPermissionLoader.java`（第121行已有 "/product/**" DELETE → ADMIN） |
| 动态权限管理器 | `campus-common/.../security/DynamicPermissionAuthorizationManager.java` |
| JWT 网关过滤器 | `campus-gateway/.../filter/JwtAuthFilter.java` |
| 商品状态常量 | `campus-common/.../constant/ProductStatusConstant.java` |
| 订单状态常量 | `campus-common/.../constant/OrderStatusConstant.java` |
| 前端 API 层 | `campus-frontend/src/api/product.ts`（delete 方法已存在） |
| 前端路由 | `campus-frontend/src/router/index.ts` |
| 管理员 Dashboard | `campus-frontend/src/views/admin/Dashboard.vue` |
| 管理员商品审核页 | `campus-frontend/src/views/admin/ProductAudit.vue` |

### B. 数据库字段参考（t_product 表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| name | VARCHAR | 商品名称 |
| description | VARCHAR | 商品描述 |
| price | DECIMAL | 价格 |
| stock | INT | 库存 |
| product_status | INT | 商品状态：0=PENDING, 1=ON_SALE, 2=SOLD, 3=OFF_SHELF, 4=CANCELLED |
| audit_status | INT | 审核状态：0=PENDING, 1=APPROVED, 2=REJECTED |
| user_id | BIGINT | 卖家用户 ID |
| deleted | INT | 逻辑删除标记：0=正常, 1=已删除（@TableLogic） |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

---

*本需求规格说明已与项目现有代码架构（Spring Boot 3.2 + Spring Cloud Alibaba + MyBatis-Plus + Vue 3 + Element Plus + RBAC 权限模型）对齐，可直接作为开发实施依据。*
