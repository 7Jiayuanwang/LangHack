-- 英语学习平台数据库表创建脚本（无deleted字段版本）
-- 使用物理删除替代软删除

-- ================================
-- 1. 任务表 (assignments) - 无deleted字段
-- ================================
CREATE TABLE IF NOT EXISTS assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    title VARCHAR(255) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    unit_id BIGINT COMMENT '单元ID',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    deadline DATETIME COMMENT '截止时间',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态: active/ended',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_class_id (class_id),
    INDEX idx_teacher_id (teacher_id),
    INDEX idx_status (status),
    INDEX idx_created_time (created_time),
    FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE,
    FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE SET NULL,
    FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- ================================
-- 2. 学生任务表 (student_assignments) - 无deleted字段
-- ================================
CREATE TABLE IF NOT EXISTS student_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    assignment_id BIGINT NOT NULL COMMENT '任务ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    completed_at DATETIME COMMENT '完成时间',
    time_spent INT DEFAULT 0 COMMENT '用时（分钟）',
    score INT COMMENT '得分',
    status VARCHAR(20) DEFAULT 'not_started' COMMENT '状态: not_started/in_progress/completed',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_student_id (student_id),
    INDEX idx_status (status),
    INDEX idx_completed_at (completed_at),
    UNIQUE KEY uk_assignment_student (assignment_id, student_id),
    FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生任务表';

-- ================================
-- 3. 单元表 (units) - 无deleted字段
-- ================================
CREATE TABLE IF NOT EXISTS units (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '单元ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    name VARCHAR(255) NOT NULL COMMENT '单元名称',
    description TEXT COMMENT '单元描述',
    order_num INT DEFAULT 0 COMMENT '排序号',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_course_id (course_id),
    INDEX idx_order_num (order_num),
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习单元表';

-- ================================
-- 4. 插入测试数据（可选）
-- ================================

-- 插入测试单元数据（如果units表为空）
INSERT IGNORE INTO units (id, course_id, name, description, order_num) VALUES
(1, 1, '第一单元', '基础词汇学习', 1),
(2, 1, '第二单元', '日常对话练习', 2),
(3, 1, '第三单元', '语法基础', 3),
(4, 2, '第一单元', '商务词汇', 1),
(5, 2, '第二单元', '商务写作', 2);

-- ================================
-- 5. 数据验证查询
-- ================================

-- 验证表是否创建成功
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME IN ('assignments', 'student_assignments', 'units')
ORDER BY TABLE_NAME;

-- 验证表结构
DESCRIBE assignments;
DESCRIBE student_assignments;
DESCRIBE units;

-- 检查是否有deleted字段（应该没有）
SELECT COLUMN_NAME, DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME IN ('assignments', 'student_assignments', 'units')
AND COLUMN_NAME = 'deleted';