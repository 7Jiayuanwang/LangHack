#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
班级和学生导入脚本

功能：
1. 创建一个班级
2. 从Excel文件中读取学生名单
3. 为每个学生创建用户账号（如果不存在）
4. 将学生关联到班级

使用方法：
python3 scripts/import_class_students.py --excel "25土管名单新.xls" --class-name "25土管3班" --course-id 1 --teacher-id 1
"""

import argparse
import sys
import pandas as pd
import pymysql
from datetime import datetime
import hashlib

# 尝试导入bcrypt，如果不可用则使用SHA256
try:
    import bcrypt
    BCRYPT_AVAILABLE = True
except ImportError:
    BCRYPT_AVAILABLE = False
    print("警告: bcrypt未安装，将使用SHA256哈希密码（不推荐用于生产环境）")
    print("建议安装bcrypt: pip3 install bcrypt")


class ClassStudentImporter:
    """班级学生导入器"""
    
    def __init__(self, db_config):
        """初始化数据库连接"""
        self.conn = pymysql.connect(
            host=db_config['host'],
            port=db_config['port'],
            user=db_config['user'],
            password=db_config['password'],
            database=db_config['database'],
            charset='utf8mb4',
            cursorclass=pymysql.cursors.DictCursor
        )
        self.cursor = self.conn.cursor()
    
    def __del__(self):
        """关闭数据库连接"""
        if hasattr(self, 'cursor') and self.cursor:
            self.cursor.close()
        if hasattr(self, 'conn') and self.conn:
            self.conn.close()
    
    def create_class(self, class_name, course_id, teacher_id, description=""):
        """创建班级"""
        try:
            # 检查班级是否已存在
            sql_check = "SELECT id FROM classes WHERE name = %s"
            self.cursor.execute(sql_check, (class_name,))
            existing_class = self.cursor.fetchone()
            
            if existing_class:
                print(f"班级 '{class_name}' 已存在，ID: {existing_class['id']}")
                return existing_class['id']
            
            # 创建新班级
            sql_insert = """
                INSERT INTO classes (name, course_id, teacher_id, description, created_time)
                VALUES (%s, %s, %s, %s, %s)
            """
            now = datetime.now()
            self.cursor.execute(sql_insert, (class_name, course_id, teacher_id, description, now))
            self.conn.commit()
            
            class_id = self.cursor.lastrowid
            print(f"成功创建班级 '{class_name}'，ID: {class_id}")
            return class_id
            
        except Exception as e:
            self.conn.rollback()
            print(f"创建班级失败: {e}")
            raise
    
    def generate_username(self, student_id):
        """根据学号生成用户名"""
        return f"student_{student_id}"
    
    def generate_default_password(self, student_id):
        """生成默认密码（使用完整学号）"""
        return student_id
    
    def hash_password(self, password):
        """使用BCrypt或SHA256哈希密码"""
        if BCRYPT_AVAILABLE:
            # 使用BCrypt（与Spring Security兼容）
            salt = bcrypt.gensalt()
            hashed = bcrypt.hashpw(password.encode('utf-8'), salt)
            return hashed.decode('utf-8')
        else:
            # 备选方案：使用SHA256（不推荐用于生产环境）
            return hashlib.sha256(password.encode()).hexdigest()
    
    def create_or_get_user(self, student_id, real_name, update_password=False):
        """创建或获取用户，可选更新密码"""
        try:
            username = self.generate_username(student_id)
            default_password = self.generate_default_password(student_id)
            
            # 检查用户是否已存在
            sql_check = "SELECT id FROM users WHERE username = %s"
            self.cursor.execute(sql_check, (username,))
            existing_user = self.cursor.fetchone()
            
            if existing_user:
                user_id = existing_user['id']
                if update_password:
                    # 更新密码
                    hashed_password = self.hash_password(default_password)
                    sql_update = """
                        UPDATE users 
                        SET password = %s, updated_time = %s
                        WHERE id = %s
                    """
                    now = datetime.now()
                    self.cursor.execute(sql_update, (hashed_password, now, user_id))
                    self.conn.commit()
                    print(f"  用户 '{username}' 已存在，ID: {user_id}，已更新密码为: {default_password}")
                else:
                    print(f"  用户 '{username}' 已存在，ID: {user_id}")
                return user_id
            
            # 创建新用户
            hashed_password = self.hash_password(default_password)
            
            sql_insert = """
                INSERT INTO users (username, password, real_name, nickname, role, status, deleted, created_time, updated_time)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            """
            now = datetime.now()
            self.cursor.execute(sql_insert, (
                username,
                hashed_password,
                real_name,
                real_name,  # nickname默认使用真实姓名
                'student',
                'active',
                0,
                now,
                now
            ))
            self.conn.commit()
            
            user_id = self.cursor.lastrowid
            print(f"  创建用户 '{username}' (姓名: {real_name})，ID: {user_id}，默认密码: {default_password}")
            return user_id
            
        except Exception as e:
            self.conn.rollback()
            print(f"  创建用户失败: {e}")
            raise
    
    def add_student_to_class(self, student_id, class_id):
        """将学生添加到班级"""
        try:
            # 检查是否已经在班级中
            sql_check = "SELECT id FROM student_classes WHERE student_id = %s AND class_id = %s"
            self.cursor.execute(sql_check, (student_id, class_id))
            existing_relation = self.cursor.fetchone()
            
            if existing_relation:
                print(f"  学生已在班级中")
                return existing_relation['id']
            
            # 添加学生到班级
            sql_insert = """
                INSERT INTO student_classes (student_id, class_id, join_time)
                VALUES (%s, %s, %s)
            """
            now = datetime.now()
            self.cursor.execute(sql_insert, (student_id, class_id, now))
            self.conn.commit()
            
            relation_id = self.cursor.lastrowid
            print(f"  成功添加学生到班级")
            return relation_id
            
        except Exception as e:
            self.conn.rollback()
            print(f"  添加学生到班级失败: {e}")
            raise
    
    def import_students_from_excel(self, excel_file, class_id, filter_class=None, update_password=False):
        """从Excel文件导入学生"""
        try:
            # 读取Excel文件（第二行是表头）
            df = pd.read_excel(excel_file, header=1)
            
            # 检查必需的列
            required_columns = ['学号', '姓名']
            for col in required_columns:
                if col not in df.columns:
                    raise ValueError(f"Excel文件缺少必需的列: {col}")
            
            # 过滤掉空行
            df = df.dropna(subset=['学号', '姓名'])
            
            # 如果指定了班级过滤，只导入该班级的学生
            if filter_class:
                if '班级' not in df.columns:
                    print(f"警告: Excel文件中没有'班级'列，无法进行班级过滤")
                else:
                    original_count = len(df)
                    df = df[df['班级'] == filter_class]
                    filtered_count = len(df)
                    print(f"\n班级过滤: '{filter_class}'")
                    print(f"  原始记录数: {original_count}")
                    print(f"  过滤后记录数: {filtered_count}")
                    print(f"  已过滤掉: {original_count - filtered_count} 条记录")
            
            print(f"\n开始导入 {len(df)} 个学生...")
            success_count = 0
            fail_count = 0
            
            for index, row in df.iterrows():
                student_id_num = str(row['学号'])
                real_name = str(row['姓名']).strip()
                
                # 跳过无效数据
                if not student_id_num or not real_name or student_id_num == 'nan':
                    continue
                
                # 确保学号是字符串格式
                student_id_num = student_id_num.split('.')[0]  # 移除可能的小数点
                
                print(f"\n处理学生 {index + 1}: 学号={student_id_num}, 姓名={real_name}")
                
                try:
                    # 创建或获取用户
                    user_id = self.create_or_get_user(student_id_num, real_name, update_password)
                    
                    # 添加到班级
                    self.add_student_to_class(user_id, class_id)
                    
                    success_count += 1
                    
                except Exception as e:
                    print(f"  处理失败: {e}")
                    fail_count += 1
            
            print(f"\n导入完成！成功: {success_count}, 失败: {fail_count}")
            
        except Exception as e:
            print(f"导入学生失败: {e}")
            raise


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='导入班级和学生数据')
    parser.add_argument('--excel', required=True, help='Excel文件路径')
    parser.add_argument('--class-name', required=True, help='班级名称')
    parser.add_argument('--course-id', type=int, required=True, help='课程ID')
    parser.add_argument('--teacher-id', type=int, required=True, help='教师ID')
    parser.add_argument('--description', default='', help='班级描述')
    parser.add_argument('--filter-class', default=None, help='只导入指定班级的学生（根据Excel中的班级列过滤）')
    parser.add_argument('--update-password', action='store_true', help='更新已存在用户的密码')
    parser.add_argument('--db-host', default='localhost', help='数据库主机')
    parser.add_argument('--db-port', type=int, default=3306, help='数据库端口')
    parser.add_argument('--db-user', default='root', help='数据库用户名')
    parser.add_argument('--db-password', default='Dao030916', help='数据库密码')
    parser.add_argument('--db-name', default='english_learning', help='数据库名称')
    
    args = parser.parse_args()
    
    # 数据库配置
    db_config = {
        'host': args.db_host,
        'port': args.db_port,
        'user': args.db_user,
        'password': args.db_password,
        'database': args.db_name
    }
    
    try:
        # 创建导入器
        importer = ClassStudentImporter(db_config)
        
        # 创建班级
        class_id = importer.create_class(
            args.class_name,
            args.course_id,
            args.teacher_id,
            args.description
        )
        
        # 导入学生
        importer.import_students_from_excel(args.excel, class_id, args.filter_class, args.update_password)
        
        print("\n所有操作完成！")
        
    except Exception as e:
        print(f"\n错误: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()
