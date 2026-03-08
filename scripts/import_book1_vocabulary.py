#!/usr/bin/env python3
"""
导入 book1/vocabulary 目录下的词汇 JSON 文件到数据库

使用方法:
    python3 scripts/import_book1_vocabulary.py

功能:
    1. 读取 vocabulary 目录下的 JSON 文件
    2. 根据文件名匹配对应的 text (reading)
    3. 将词汇导入到 words 表
    4. 创建 text 和 word 的关联（通过 unit_words 表）
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

# 文件路径
VOCABULARY_DIR = Path(__file__).parent.parent / "book1" / "vocabulary"


def connect_db():
    """连接数据库"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        print(f"✓ 成功连接到数据库: {DB_CONFIG['database']}")
        return conn
    except mysql.connector.Error as e:
        print(f"✗ 数据库连接失败: {e}")
        raise


def parse_filename(filename):
    """
    解析文件名，提取单元号和阅读序号
    例如: "1-2 iExplore 1 Reading_vocabulary.json" -> (1, 2)
    """
    match = re.match(r'(\d+)-(\d+)\s+.*_vocabulary\.json', filename)
    if match:
        unit_num = int(match.group(1))
        reading_num = int(match.group(2))
        return unit_num, reading_num
    return None, None


def find_text_id(cursor, course_name, unit_num, reading_num):
    """
    根据课程名、单元号和阅读序号查找对应的 text_id
    """
    cursor.execute("""
        SELECT t.id, t.title, u.id as unit_id, u.name as unit_name
        FROM texts t
        JOIN units u ON t.unit_id = u.id
        JOIN courses c ON u.course_id = c.id
        WHERE c.name = %s 
          AND u.order_num = %s 
          AND t.order_num = %s
          AND t.deleted = 0
        LIMIT 1
    """, (course_name, unit_num, reading_num))
    
    result = cursor.fetchone()
    if result:
        return {
            'text_id': result[0],
            'title': result[1],
            'unit_id': result[2],
            'unit_name': result[3]
        }
    return None


def create_or_get_word(cursor, word_data):
    """
    创建或获取单词
    返回 word_id
    """
    word = word_data.get('word', '').strip()
    if not word:
        return None
    
    # 检查单词是否已存在
    cursor.execute(
        "SELECT id FROM words WHERE word = %s AND deleted = 0 LIMIT 1",
        (word,)
    )
    existing = cursor.fetchone()
    
    if existing:
        return existing[0]
    
    # 解析数据
    explanation = word_data.get('explanation', '')
    example = word_data.get('example', '')
    phonetic = word_data.get('phonetic', '')
    
    # 从 explanation 中提取词性和释义
    # 格式: "n. [C] the land and buildings... 校园"
    part_of_speech = 'unknown'
    definition = explanation
    
    # 尝试提取词性
    pos_match = re.match(r'^([a-z]+\.)\s+', explanation)
    if pos_match:
        pos_abbr = pos_match.group(1).rstrip('.')
        # 映射词性缩写
        pos_map = {
            'n': 'noun',
            'v': 'verb',
            'vt': 'verb',
            'vi': 'verb',
            'a': 'adjective',
            'ad': 'adverb',
            'adv': 'adverb',
            'prep': 'preposition',
            'conj': 'conjunction',
            'pron': 'pronoun',
            'int': 'interjection',
            'interj': 'interjection'
        }
        part_of_speech = pos_map.get(pos_abbr, 'unknown')
    
    # 音频 URL（如果有）
    audio_url = f"/audio/vocabulary/{phonetic}" if phonetic else None
    
    # 插入单词
    cursor.execute("""
        INSERT INTO words (
            word, pronunciation, definition, example_sentence, audio_url,
            part_of_speech, difficulty, source, deleted,
            created_time, updated_time
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 0, NOW(), NOW())
    """, (
        word,
        phonetic.replace('.mp3', '') if phonetic else None,
        explanation,
        example,
        audio_url,
        part_of_speech,
        'MEDIUM',  # 默认难度
        'book1_vocabulary'
    ))
    
    return cursor.lastrowid


def link_word_to_unit(cursor, unit_id, word_id, order_num):
    """
    将单词关联到单元
    """
    # 检查关联是否已存在
    cursor.execute(
        "SELECT id FROM unit_words WHERE unit_id = %s AND word_id = %s LIMIT 1",
        (unit_id, word_id)
    )
    existing = cursor.fetchone()
    
    if existing:
        return existing[0]
    
    # 创建关联
    cursor.execute("""
        INSERT INTO unit_words (unit_id, word_id, order_num, created_time)
        VALUES (%s, %s, %s, NOW())
    """, (unit_id, word_id, order_num))
    
    return cursor.lastrowid


def import_vocabulary_file(cursor, json_file, course_name):
    """
    导入单个词汇文件
    """
    unit_num, reading_num = parse_filename(json_file.name)
    
    if unit_num is None:
        print(f"  ⚠ 跳过无效文件名: {json_file.name}")
        return 0
    
    # 查找对应的 text
    text_info = find_text_id(cursor, course_name, unit_num, reading_num)
    
    if not text_info:
        print(f"  ✗ 未找到对应的阅读材料: Unit {unit_num}, Reading {reading_num}")
        return 0
    
    print(f"\n处理: {json_file.name}")
    print(f"  对应阅读: {text_info['title']} (Text ID: {text_info['text_id']})")
    print(f"  所属单元: {text_info['unit_name']} (Unit ID: {text_info['unit_id']})")
    
    # 读取 JSON 文件
    try:
        with open(json_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except Exception as e:
        print(f"  ✗ 读取文件失败: {e}")
        return 0
    
    words_data = data.get('words', [])
    if not words_data:
        print(f"  ⚠ 文件中没有单词数据")
        return 0
    
    # 导入单词
    imported_count = 0
    skipped_count = 0
    
    for idx, word_data in enumerate(words_data, 1):
        word = word_data.get('word', '').strip()
        
        # 跳过无效数据
        if not word or '已学完' in word:
            skipped_count += 1
            continue
        
        try:
            # 创建或获取单词
            word_id = create_or_get_word(cursor, word_data)
            
            if word_id:
                # 关联到单元
                link_word_to_unit(cursor, text_info['unit_id'], word_id, idx)
                imported_count += 1
                
                if imported_count % 10 == 0:
                    print(f"    已导入 {imported_count} 个单词...")
        
        except Exception as e:
            print(f"    ✗ 导入单词失败 '{word}': {e}")
            skipped_count += 1
    
    print(f"  ✓ 完成: 导入 {imported_count} 个单词, 跳过 {skipped_count} 个")
    return imported_count


def import_all_vocabulary(cursor, course_name):
    """
    导入所有词汇文件
    """
    if not VOCABULARY_DIR.exists():
        print(f"✗ 目录不存在: {VOCABULARY_DIR}")
        return
    
    # 获取所有 JSON 文件并排序
    json_files = sorted(VOCABULARY_DIR.glob("*_vocabulary.json"))
    
    if not json_files:
        print(f"✗ 未找到词汇文件: {VOCABULARY_DIR}")
        return
    
    print(f"\n找到 {len(json_files)} 个词汇文件")
    print("=" * 70)
    
    total_imported = 0
    
    for json_file in json_files:
        imported = import_vocabulary_file(cursor, json_file, course_name)
        total_imported += imported
    
    return total_imported


def main():
    """主函数"""
    print("=" * 70)
    print("  大学英语1 词汇导入工具")
    print("=" * 70)
    print()
    
    try:
        # 连接数据库
        conn = connect_db()
        cursor = conn.cursor()
        
        # 导入词汇
        total_imported = import_all_vocabulary(cursor, COURSE_NAME)
        
        # 提交事务
        conn.commit()
        
        print("\n" + "=" * 70)
        print("  导入完成！")
        print("=" * 70)
        
        # 显示统计信息
        cursor.execute("""
            SELECT COUNT(DISTINCT w.id)
            FROM words w
            JOIN unit_words uw ON w.id = uw.word_id
            JOIN units u ON uw.unit_id = u.id
            JOIN courses c ON u.course_id = c.id
            WHERE c.name = %s AND w.deleted = 0
        """, (COURSE_NAME,))
        word_count = cursor.fetchone()[0]
        
        cursor.execute("""
            SELECT COUNT(DISTINCT u.id)
            FROM units u
            JOIN courses c ON u.course_id = c.id
            WHERE c.name = %s
        """, (COURSE_NAME,))
        unit_count = cursor.fetchone()[0]
        
        print(f"\n统计信息:")
        print(f"  课程: {COURSE_NAME}")
        print(f"  单元数: {unit_count}")
        print(f"  词汇总数: {word_count}")
        print(f"  本次导入: {total_imported}")
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
