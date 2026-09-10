-- =====================================================================
-- V5: 管理员账号
-- 学号 25209100016 / 密码 yanggeng123456（密码在 AuthServiceImpl 里单独特判放行）
-- role=1 管理员，可发布公告等管理操作
-- =====================================================================
INSERT INTO t_user (student_id, name, nickname, role, status)
SELECT '25209100016', '管理员', '管理员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE student_id = '25209100016');