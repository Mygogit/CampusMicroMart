-- ============================================
-- 数据迁移脚本：订单商品表 (t_order_item)
-- 版本：v1.1
-- 说明：将 t_order 表中的单商品数据迁移到 t_order_item 明细表
-- ============================================

USE campus_order;

-- 1. 创建订单商品明细表
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

-- 2. 将现有订单数据迁移到商品明细表（每笔订单一行记录）
INSERT INTO t_order_item (order_id, product_id, quantity, unit_price, total_price, create_time, update_time)
SELECT
    id AS order_id,
    product_id,
    quantity,
    price AS unit_price,
    total_amount AS total_price,
    create_time,
    update_time
FROM t_order
WHERE product_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_order_item oi WHERE oi.order_id = t_order.id
  );
