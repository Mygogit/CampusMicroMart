-- ============================================================
-- CampusMicroMart JMeter 三场景压测 —— 数据准备 / 清理脚本
-- 用法：mysql -h127.0.0.1 -P3306 -uroot -pcampus123 campus_product < prepare-perf-data.sql
-- 或在 GitFox 等客户端中手动执行对应库（商品在 campus_product，订单在 campus_order）
-- ============================================================

-- ---------- 1. 压测前：准备专用大库存商品（已于 2026-10-12 执行，ID=1031） ----------
-- 状态常量：product_status 1=在售 2=已售出 3=已下架 4=已取消 0=待审核；audit_status 1=已审核
INSERT INTO t_product (name, description, price, stock, stock_threshold, product_status, audit_status, category_id, user_id, deleted)
SELECT 'JMeter压测专用商品', '性能测试专用：在售、已审核、大库存，可随意反复下单', 0.01, 999999, 0, 1, 1, category_id, user_id, 0
FROM t_product WHERE id = 1;

-- 若上面插入时状态值有误，用这句修正（ON_SALE=1）：
-- UPDATE t_product SET product_status = 1, audit_status = 1 WHERE name = 'JMeter压测专用商品';

-- ---------- 2. 压测前：确认测试用户存在（登录场景用，密码统一 campus123） ----------
-- 用户来自 testdata/seed_data.json，30 个，清单见 test/data/test-users.csv
-- SELECT COUNT(*) FROM t_user;  -- 库：campus_user

-- ---------- 3. 压测后：清理测试产生的订单（避免污染演示数据） ----------
-- 库：campus_order；先查后删，确认 remark 匹配再执行
-- SELECT COUNT(*) FROM t_order WHERE remark = 'JMeter性能测试订单';
-- DELETE oi FROM t_order_item oi JOIN t_order o ON oi.order_id = o.id WHERE o.remark = 'JMeter性能测试订单';
-- DELETE FROM t_order WHERE remark = 'JMeter性能测试订单';

-- ---------- 4. 压测后：恢复/删除专用压测商品 ----------
-- DELETE FROM t_product WHERE name = 'JMeter压测专用商品';
