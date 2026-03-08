#!/usr/bin/env python3
"""
导入 book1/readings 目录下的 JSON 文件到数据库

使用方法:
    python3 scripts/import_book1_readings.py

功能:
    1. 创建一个新课程 "iExplore 1"
    2. 根据 JSON 文件创建单元（Unit）
    3. 将每个 JSON 文件的内容导入为文本（Text）
"""

import json
import os
import re
import mysql.connector
from pathlib import Path
from datetime import datetime

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': 'Dao030916',
    'database': 'english_learning',
    'charset': 'utf8mb4'
}

# 课程配置
COURSE_NAME = "大学英语1"
COURSE_DESCRIPTION = "大学英语1，大一上学期"
COURSE_STATUS = "active"
CREATED_BY = 1  # 管理员用户ID

# 文件路径
READINGS_DIR = Path(__file__).parent.parent / "book1" / "readings"


def connect_db():
    """连接数据库"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        print(f"✓ 成功连接到数据库: {DB_CONFIG['database']}")
        return conn
    except mysql.connector.Error as e:
        print(f"✗ 数据库连接失败: {e}")
        raise


def create_course(cursor):
    """创建课程"""
    # 检查课程是否已存在
    cursor.execute(
        "SELECT id FROM courses WHERE name = %s AND deleted = 0",
        (COURSE_NAME,)
    )
    existing = cursor.fetchone()
    
    if existing:
        course_id = existing[0]
        print(f"✓ 课程已存在: {COURSE_NAME} (ID: {course_id})")
        return course_id
    
    # 创建新课程
    cursor.execute("""
        INSERT INTO courses (name, description, status, created_by, deleted, created_time, updated_time)
        VALUES (%s, %s, %s, %s, 0, NOW(), NOW())
    """, (COURSE_NAME, COURSE_DESCRIPTION, COURSE_STATUS, CREATED_BY))
    
    course_id = cursor.lastrowid
    print(f"✓ 创建课程: {COURSE_NAME} (ID: {course_id})")
    return course_id


def create_unit(cursor, course_id, unit_number, unit_name):
    """创建单元"""
    # 检查单元是否已存在
    cursor.execute(
        "SELECT id FROM units WHERE course_id = %s AND name = %s",
        (course_id, unit_name)
    )
    existing = cursor.fetchone()
    
    if existing:
        unit_id = existing[0]
        print(f"  ✓ 单元已存在: {unit_name} (ID: {unit_id})")
        return unit_id
    
    # 创建新单元
    cursor.execute("""
        INSERT INTO units (course_id, name, description, order_num, created_time, updated_time)
        VALUES (%s, %s, %s, %s, NOW(), NOW())
    """, (course_id, unit_name, f"Unit {unit_number}", int(unit_number)))
    
    unit_id = cursor.lastrowid
    print(f"  ✓ 创建单元: {unit_name} (ID: {unit_id})")
    return unit_id


def create_text(cursor, unit_id, title, content, order_num, url=None):
    """创建文本"""
    # 检查文本是否已存在
    cursor.execute(
        "SELECT id FROM texts WHERE unit_id = %s AND title = %s AND deleted = 0",
        (unit_id, title)
    )
    existing = cursor.fetchone()
    
    if existing:
        text_id = existing[0]
        print(f"    ✓ 文本已存在: {title} (ID: {text_id})")
        return text_id
    
    # 创建新文本
    cursor.execute("""
        INSERT INTO texts (
            unit_id, title, content, audio_url, difficulty, order_num,
            uploaded_by, tags, view_count, like_count, is_public, status, deleted,
            created_time, updated_time
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 0, 0, 1, %s, 0, NOW(), NOW())
    """, (
        unit_id, title, content, url or '', 'MEDIUM', order_num,
        CREATED_BY, 'reading,iexplore', 'APPROVED'  # 修改为 APPROVED
    ))
    
    text_id = cursor.lastrowid
    print(f"    ✓ 创建文本: {title} (ID: {text_id})")
    return text_id


def parse_filename(filename):
    """
    解析文件名，提取单元号和阅读序号
    例如: "1-2 iExplore 1 Reading.json" -> (1, 2, "iExplore 1 Reading")
    """
    match = re.match(r'(\d+)-(\d+)\s+(.+)\.json', filename)
    if match:
        unit_num = int(match.group(1))
        reading_num = int(match.group(2))
        reading_name = match.group(3)
        return unit_num, reading_num, reading_name
    return None, None, None


def import_readings(cursor, course_id):
    """导入所有阅读文件"""
    if not READINGS_DIR.exists():
        print(f"✗ 目录不存在: {READINGS_DIR}")
        return
    
    # 获取所有 JSON 文件并排序
    json_files = sorted(READINGS_DIR.glob("*.json"))
    
    if not json_files:
        print(f"✗ 未找到 JSON 文件: {READINGS_DIR}")
        return
    
    print(f"\n找到 {len(json_files)} 个阅读文件")
    print("=" * 60)
    
    # 按单元分组
    units = {}
    for json_file in json_files:
        unit_num, reading_num, reading_name = parse_filename(json_file.name)
        
        if unit_num is None:
            print(f"⚠ 跳过无效文件名: {json_file.name}")
            continue
        
        if unit_num not in units:
            units[unit_num] = []
        
        units[unit_num].append({
            'file': json_file,
            'reading_num': reading_num,
            'reading_name': reading_name
        })
    
    # 处理每个单元
    for unit_num in sorted(units.keys()):
        unit_name = f"Unit {unit_num}"
        print(f"\n处理单元 {unit_num}: {unit_name}")
        print("-" * 60)
        
        # 创建单元
        unit_id = create_unit(cursor, course_id, unit_num, unit_name)
        
        # 处理该单元的所有阅读
        readings = sorted(units[unit_num], key=lambda x: x['reading_num'])
        
        for reading in readings:
            json_file = reading['file']
            reading_num = reading['reading_num']
            reading_name = reading['reading_name']
            
            try:
                # 读取 JSON 文件
                with open(json_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                title = data.get('title', reading_name)
                content = data.get('content', '')
                url = data.get('url', '')
                
                # 创建文本
                create_text(cursor, unit_id, title, content, reading_num, url)
                
            except Exception as e:
                print(f"    ✗ 处理文件失败 {json_file.name}: {e}")


def main():
    """主函数"""
    print("=" * 60)
    print("  iExplore 1 阅读材料导入工具")
    print("=" * 60)
    print()
    
    try:
        # 连接数据库
        conn = connect_db()
        cursor = conn.cursor()
        
        # 创建课程
        print("\n创建课程...")
        print("-" * 60)
        course_id = create_course(cursor)
        
        # 导入阅读材料
        import_readings(cursor, course_id)
        
        # 提交事务
        conn.commit()
        
        print("\n" + "=" * 60)
        print("  导入完成！")
        print("=" * 60)
        
        # 显示统计信息
        cursor.execute("SELECT COUNT(*) FROM units WHERE course_id = %s", (course_id,))
        unit_count = cursor.fetchone()[0]
        
        cursor.execute("""
            SELECT COUNT(*) FROM texts t
            JOIN units u ON t.unit_id = u.id
            WHERE u.course_id = %s AND t.deleted = 0
        """, (course_id,))
        text_count = cursor.fetchone()[0]
        
        print(f"\n统计信息:")
        print(f"  课程: {COURSE_NAME} (ID: {course_id})")
        print(f"  单元数: {unit_count}")
        print(f"  文本数: {text_count}")
        print()
        
    except Exception as e:
        print(f"\n✗ 导入失败: {e}")
        if 'conn' in locals():
            conn.rollback()
        raise
    
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()
            print("✓ 数据库连接已关闭")


if __name__ == "__main__":
    main()
