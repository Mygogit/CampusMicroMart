-- 创建数据库
CREATE DATABASE IF NOT EXISTS campus_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS campus_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS campus_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS campus_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用用户服务数据库
USE campus_user;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    nickname VARCHAR(64) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(128) COMMENT '邮箱',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-正常',
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT' COMMENT '角色 STUDENT/ADMIN',
    credit_score INT NOT NULL DEFAULT 100 COMMENT '信用分',
    deposit DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '保证金',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记 0-未删除 1-已删除',
    INDEX idx_username (username),
    INDEX idx_phone (phone),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 信用日志表
CREATE TABLE IF NOT EXISTS t_credit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    change_type VARCHAR(32) NOT NULL COMMENT '变更类型 REGISTER/SELL/BUY/CANCEL_ORDER/VIOLATION',
    score_change INT NOT NULL COMMENT '分数变化',
    score_after INT NOT NULL COMMENT '变更后分数',
    reason VARCHAR(255) COMMENT '原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用日志表';

-- 使用用户服务数据库 - Seata undo_log
USE campus_user;
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id BIGINT NOT NULL COMMENT 'branch transaction id',
    xid VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    context VARCHAR(128) NOT NULL COMMENT 'undo_log context',
    rollback_info LONGBLOB NOT NULL COMMENT 'rollback info',
    log_status INT NOT NULL COMMENT '0:normal,1:defense',
    log_created DATETIME(6) NOT NULL COMMENT 'create datetime',
    log_modified DATETIME(6) NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table';

-- 使用商品服务数据库
USE campus_product;

-- 商品分类表
CREATE TABLE IF NOT EXISTS t_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    name VARCHAR(64) NOT NULL COMMENT '分类名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父分类ID',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记 0-未删除 1-已删除',
    INDEX idx_parent_id (parent_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 商品表
CREATE TABLE IF NOT EXISTS t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    name VARCHAR(255) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    price DECIMAL(10,2) NOT NULL COMMENT '价格',
    stock INT NOT NULL DEFAULT 0 COMMENT '库存',
    stock_threshold INT NOT NULL DEFAULT 5 COMMENT '库存预警阈值',
    product_status TINYINT NOT NULL DEFAULT 0 COMMENT '商品状态 0-待审核 1-在售 2-已售出 3-已下架 4-已取消',
    audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态 0-待审核 1-已通过 2-已拒绝',
    category_id BIGINT COMMENT '分类ID',
    user_id BIGINT COMMENT '发布用户ID',
    images TEXT COMMENT '商品图片',
    course_code VARCHAR(32) COMMENT '课程代码',
    dormitory VARCHAR(64) COMMENT '宿舍楼栋',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记 0-未删除 1-已删除',
    INDEX idx_category_id (category_id),
    INDEX idx_user_id (user_id),
    INDEX idx_product_status (product_status),
    INDEX idx_audit_status (audit_status),
    INDEX idx_course_code (course_code),
    INDEX idx_dormitory (dormitory)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 使用订单服务数据库
USE campus_order;

-- 订单表
CREATE TABLE IF NOT EXISTS t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '订单编号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(255) COMMENT '商品名称',
    price DECIMAL(10,2) NOT NULL COMMENT '商品单价',
    quantity INT NOT NULL DEFAULT 1 COMMENT '数量',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    shipping_address VARCHAR(255) COMMENT '收货地址',
    buyer_phone VARCHAR(20) COMMENT '收货人电话',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记 0-未删除 1-已删除',
    INDEX idx_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单商品明细表
CREATE TABLE IF NOT EXISTS t_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '商品单价',
    total_price DECIMAL(10,2) NOT NULL COMMENT '小计金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品明细表';

-- 物流信息表
CREATE TABLE IF NOT EXISTS t_shipping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '物流ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
    tracking_no VARCHAR(128) COMMENT '物流单号',
    carrier VARCHAR(64) COMMENT '快递公司',
    address VARCHAR(255) COMMENT '收货地址',
    shipped_time DATETIME COMMENT '发货时间',
    delivered_time DATETIME COMMENT '签收时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_id (order_id),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流信息表';

-- 使用订单服务数据库 - Seata undo_log
USE campus_order;
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id BIGINT NOT NULL COMMENT 'branch transaction id',
    xid VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    context VARCHAR(128) NOT NULL COMMENT 'undo_log context',
    rollback_info LONGBLOB NOT NULL COMMENT 'rollback info',
    log_status INT NOT NULL COMMENT '0:normal,1:defense',
    log_created DATETIME(6) NOT NULL COMMENT 'create datetime',
    log_modified DATETIME(6) NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table';

-- 使用商品服务数据库 - Seata undo_log
USE campus_product;
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id BIGINT NOT NULL COMMENT 'branch transaction id',
    xid VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    context VARCHAR(128) NOT NULL COMMENT 'undo_log context',
    rollback_info LONGBLOB NOT NULL COMMENT 'rollback info',
    log_status INT NOT NULL COMMENT '0:normal,1:defense',
    log_created DATETIME(6) NOT NULL COMMENT 'create datetime',
    log_modified DATETIME(6) NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table';

-- Seata Server 数据库
CREATE DATABASE IF NOT EXISTS seata DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用支付服务数据库
USE campus_payment;

-- 支付表
CREATE TABLE IF NOT EXISTS t_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '支付ID',
    payment_no VARCHAR(64) NOT NULL UNIQUE COMMENT '支付编号',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    payment_method VARCHAR(32) COMMENT '支付方式 SIMULATE/DEPOSIT',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态 0-待支付 1-处理中 2-支付成功 3-支付失败 4-已过期 5-重试中',
    third_party_no VARCHAR(255) COMMENT '第三方支付流水号',
    user_id BIGINT COMMENT '用户ID',
    expire_time DATETIME COMMENT '支付过期时间',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    callback_status VARCHAR(32) COMMENT '回调状态 SUCCESS/FAIL',
    fail_reason VARCHAR(255) COMMENT '失败原因',
    callback_time DATETIME COMMENT '回调时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记 0-未删除 1-已删除',
    INDEX idx_payment_no (payment_no),
    INDEX idx_user_id (user_id),
    INDEX idx_order_id (order_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付表';

-- 使用支付服务数据库 - Seata undo_log
USE campus_payment;
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id BIGINT NOT NULL COMMENT 'branch transaction id',
    xid VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    context VARCHAR(128) NOT NULL COMMENT 'undo_log context',
    rollback_info LONGBLOB NOT NULL COMMENT 'rollback info',
    log_status INT NOT NULL COMMENT '0:normal,1:defense',
    log_created DATETIME(6) NOT NULL COMMENT 'create datetime',
    log_modified DATETIME(6) NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table';
