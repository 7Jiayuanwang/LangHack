#!/usr/bin/env python3
"""
关联音频文件到单词

使用方法:
    python3 scripts/link_audio_to_words.py

功能:
    1. 扫描 book1/audio 目录下的音频文件
    2. 根据文件名匹配数据库中的单词
    3. 更新单词的 audio_url 字段
"""

import os
import mysql.connector
from pathlib import Path
import urllib.parse

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': 'Dao030916',
    'database': 'english_learning',
    'charset': 'utf8mb4'
}

# 音频文件目录
AUDIO_DIR = Path(__file__).parent.parent / "book1" / "audio"

# 音频文件的 URL 前缀（用于前端访问）
AUDIO_URL_PREFIX = "/audio/vocabulary"


def connect_db():
    """连接数据库"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        print(f"✓ 成功连接到数据库: {DB_CONFIG['database']}")
        return conn
    except mysql.connector.Error as e:
        print(f"✗ 数据库连接失败: {e}")
        raise


def normalize_word(word):
    """
    标准化单词，用于匹配
    - 转小写
    - 去除空格
    """
    return word.lower().strip()


def get_audio_files():
    """
    获取所有音频文件
    返回: {标准化的文件名: 原始文件名}
    """
    if not AUDIO_DIR.exists():
        print(f"✗ 音频目录不存在: {AUDIO_DIR}")
        return {}
    
    audio_files = {}
    
    for audio_file in AUDIO_DIR.glob("*.mp3"):
        # 文件名（不含扩展名）
        filename = audio_file.stem
        # 标准化（用于匹配）
        normalized = normalize_word(filename)
        # 原始文件名（用于URL）
        audio_files[normalized] = audio_file.name
    
    return audio_files


def update_word_audio(cursor, word_id, word, audio_filename):
    """
    更新单词的音频URL
    """
    # URL编码文件名（处理空格等特殊字符）
    encoded_filename = urllib.parse.quote(audio_filename)
    audio_url = f"{AUDIO_URL_PREFIX}/{encoded_filename}"
    
    cursor.execute("""
        UPDATE words
        SET audio_url = %s, updated_time = NOW()
        WHERE id = %s
    """, (audio_url, word_id))
    
    return cursor.rowcount > 0


def link_audio_to_words(cursor, audio_files):
    """
    关联音频文件到单词
    """
    # 获取所有单词
    cursor.execute("""
        SELECT id, word, audio_url
        FROM words
        WHERE deleted = 0 AND source = 'book1_vocabulary'
        ORDER BY word
    """)
    
    words = cursor.fetchall()
    
    if not words:
        print("✗ 未找到需要关联的单词")
        return 0, 0, 0
    
    print(f"\n找到 {len(words)} 个单词")
    print(f"找到 {len(audio_files)} 个音频文件")
    print("=" * 70)
    
    matched_count = 0
    updated_count = 0
    already_has_audio = 0
    
    for word_id, word, current_audio_url in words:
        normalized_word = normalize_word(word)
        
        # 检查是否有对应的音频文件
        if normalized_word in audio_files:
            audio_filename = audio_files[normalized_word]
            
            # 检查是否已经有音频URL
            if current_audio_url and current_audio_url.strip():
                already_has_audio += 1
                # print(f"  ⚪ {word}: 已有音频 {current_audio_url}")
            else:
                # 更新音频URL
                if update_word_audio(cursor, word_id, word, audio_filename):
                    updated_count += 1
                    if updated_count % 50 == 0:
                        print(f"  已更新 {updated_count} 个单词...")
            
            matched_count += 1
        else:
            # 没有找到对应的音频文件
            pass
            # print(f"  ⚠ {word}: 未找到音频文件")
    
    return matched_count, updated_count, already_has_audio


def main():
    """主函数"""
    print("=" * 70)
    print("  音频文件关联工具")
    print("=" * 70)
    print()
    
    try:
        # 连接数据库
        conn = connect_db()
        cursor = conn.cursor()
        
        # 获取音频文件列表
        print("\n扫描音频文件...")
        audio_files = get_audio_files()
        
        if not audio_files:
            print("✗ 未找到音频文件")
            return
        
        # 关联音频到单词
        matched, updated, already_has = link_audio_to_words(cursor, audio_files)
        
        # 提交事务
        conn.commit()
        
        print("\n" + "=" * 70)
        print("  关联完成！")
        print("=" * 70)
        
        # 显示统计信息
        print(f"\n统计信息:")
        print(f"  音频文件总数: {len(audio_files)}")
        print(f"  匹配的单词数: {matched}")
        print(f"  新增音频链接: {updated}")
        print(f"  已有音频链接: {already_has}")
        print(f"  未匹配单词数: {len(audio_files) - matched}")
        
        # 显示一些示例
        if updated > 0:
            print(f"\n示例（前5个更新的单词）:")
            cursor.execute("""
                SELECT word, audio_url
                FROM words
                WHERE deleted = 0 
                  AND source = 'book1_vocabulary'
                  AND audio_url IS NOT NULL
                  AND audio_url != ''
                ORDER BY updated_time DESC
                LIMIT 5
            """)
            
            for word, audio_url in cursor.fetchall():
                print(f"  ✓ {word}: {audio_url}")
        
        print()
        
    except Exception as e:
        print(f"\n✗ 关联失败: {e}")
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
