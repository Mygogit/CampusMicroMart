-- 初始化管理员账号
USE campus_user;

-- 插入管理员账号 (用户名: admin, 密码: admin123)
-- BCrypt hash 由 bcryptjs 10轮生成，前缀统一为 $2a$
INSERT INTO t_user (username, password, nickname, phone, email, status, role, credit_score, deposit, create_time, update_time, deleted)
VALUES (
    'admin',
    '$2a$10$ei01juyyWyQh7hSmbCaHf.SDS47aOto/VPkWWKKPQjE6RDq85x5/2',
    '系统管理员',
    '13800138000',
    'admin@campus.edu',
    1,
    'ADMIN',
    100,
    0.00,
    NOW(),
    NOW(),
    0
)
ON DUPLICATE KEY UPDATE
    role = 'ADMIN',
    status = 1,
    update_time = NOW();

-- 检查结果
SELECT id, username, nickname, role, status, create_time
FROM t_user
WHERE username = 'admin';
