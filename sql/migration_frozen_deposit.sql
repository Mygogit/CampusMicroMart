-- 迁移：为用户表新增冻结保证金列（配合保证金托管改造）
USE campus_user;
ALTER TABLE t_user
    ADD COLUMN frozen_deposit DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '保证金（冻结余额）' AFTER deposit;
