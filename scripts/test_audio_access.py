#!/usr/bin/env python3
"""
测试音频文件访问

使用方法:
    python3 scripts/test_audio_access.py

功能:
    1. 检查音频文件是否存在
    2. 测试后端API是否可以访问音频文件
"""

import os
import requests
from pathlib import Path

# 配置
BACKEND_URL = "http://localhost:8080"
AUDIO_DIR = Path(__file__).parent.parent / "book1" / "audio"


def test_audio_files_exist():
    """测试音频文件是否存在"""
    print("=" * 70)
    print("  测试音频文件")
    print("=" * 70)
    print()
    
    if not AUDIO_DIR.exists():
        print(f"✗ 音频目录不存在: {AUDIO_DIR}")
        return False
    
    audio_files = list(AUDIO_DIR.glob("*.mp3"))
    print(f"✓ 找到 {len(audio_files)} 个音频文件")
    
    # 显示前5个文件
    print("\n前5个文件:")
    for audio_file in audio_files[:5]:
        size_kb = audio_file.stat().st_size / 1024
        print(f"  - {audio_file.name} ({size_kb:.1f} KB)")
    
    return True


def test_backend_audio_access():
    """测试后端音频访问"""
    print("\n" + "=" * 70)
    print("  测试后端音频访问")
    print("=" * 70)
    print()
    
    # 测试几个音频文件
    test_words = ["campus", "hello", "world", "book", "study"]
    
    print(f"后端URL: {BACKEND_URL}")
    print()
    
    success_count = 0
    fail_count = 0
    
    for word in test_words:
        audio_url = f"{BACKEND_URL}/audio/vocabulary/{word}.mp3"
        
        try:
            response = requests.head(audio_url, timeout=5)
            
            if response.status_code == 200:
                print(f"  ✓ {word}.mp3 - 可访问 ({response.headers.get('Content-Length', 'unknown')} bytes)")
                success_count += 1
            elif response.status_code == 404:
                print(f"  ⚠ {word}.mp3 - 文件不存在 (404)")
                fail_count += 1
            else:
                print(f"  ✗ {word}.mp3 - HTTP {response.status_code}")
                fail_count += 1
        
        except requests.exceptions.ConnectionError:
            print(f"  ✗ 无法连接到后端服务")
            print(f"     请确保后端服务正在运行: mvn spring-boot:run")
            return False
        except Exception as e:
            print(f"  ✗ {word}.mp3 - 错误: {e}")
            fail_count += 1
    
    print()
    print(f"测试结果: {success_count} 成功, {fail_count} 失败")
    
    return success_count > 0


def main():
    """主函数"""
    # 测试文件是否存在
    if not test_audio_files_exist():
        return
    
    # 测试后端访问
    print("\n提示: 请确保后端服务正在运行")
    print("      cd backend && mvn spring-boot:run")
    print()
    
    input("按 Enter 继续测试后端访问...")
    
    test_backend_audio_access()
    
    print("\n" + "=" * 70)
    print("  测试完成")
    print("=" * 70)


if __name__ == "__main__":
    main()
