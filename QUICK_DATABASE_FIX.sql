-- 快速修复：创建任务相关表
-- 解决 "Unknown column 'a.deleted'" 错误
-- 请在数据库中执行以下SQL语句

-- 1. 创建任务表
CREATE TABLE IF NOT EXISTS assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    unit_id BIGINT COMMENT '单元ID',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    deadline DATETIME COMMENT '截止时间',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态',
    deleted INT DEFAULT 0 COMMENT '删除标记',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_class_id (class_id),
    INDEX idx_teacher_id (teacher_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 2. 创建学生任务表
CREATE TABLE IF NOT EXISTS student_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL COMMENT '任务ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    completed_at DATETIME COMMENT '完成时间',
    time_spent INT DEFAULT 0 COMMENT '用时（分钟）',
    score INT COMMENT '得分',
    status VARCHAR(20) DEFAULT 'not_started' COMMENT '状态',
    deleted INT DEFAULT 0 COMMENT '删除标记',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_student_id (student_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted),
    UNIQUE KEY uk_assignment_student (assignment_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生任务表';

-- 3. 确保units表存在
CREATE TABLE IF NOT EXISTS units (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL COMMENT '课程ID',
    name VARCHAR(255) NOT NULL COMMENT '单元名称',
    description TEXT COMMENT '单元描述',
    order_num INT DEFAULT 0 COMMENT '排序号',
    deleted INT DEFAULT 0 COMMENT '删除标记',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_course_id (course_id),
    INDEX idx_deleted (deleted),
    INDEX idx_order_num (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习单元表';

-- 4. 插入基础的单元测试数据
INSERT IGNORE INTO units (id, course_id, name, description, order_num) VALUES
(1, 1, '第一单元', '基础词汇学习', 1),
(2, 1, '第二单元', '日常对话练习', 2),
(3, 1, '第三单元', '语法基础', 3);

-- 5. 验证表创建成功
SHOW TABLES LIKE 'assignments';
SHOW TABLES LIKE 'student_assignments';
SHOW TABLES LIKE 'units';

-- 6. 检查表结构
DESCRIBE assignments;