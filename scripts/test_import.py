#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试导入脚本 - 仅读取Excel文件并显示将要导入的数据，不实际写入数据库
"""

import pandas as pd
import sys


def test_excel_reading(excel_file):
    """测试读取Excel文件"""
    try:
        print(f"正在读取Excel文件: {excel_file}")
        print("=" * 60)
        
        # 读取Excel文件（第二行是表头）
        df = pd.read_excel(excel_file, header=1)
        
        print(f"\n文件信息:")
        print(f"  总行数: {len(df)}")
        print(f"  列名: {df.columns.tolist()}")
        
        # 检查必需的列
        required_columns = ['学号', '姓名']
        missing_columns = [col for col in required_columns if col not in df.columns]
        
        if missing_columns:
            print(f"\n❌ 错误: Excel文件缺少必需的列: {missing_columns}")
            return False
        
        print(f"\n✓ Excel文件格式正确")
        
        # 过滤掉空行
        df = df.dropna(subset=['学号', '姓名'])
        
        print(f"\n有效学生记录数: {len(df)}")
        print("\n将要导入的学生列表:")
        print("-" * 60)
        
        for index, row in df.iterrows():
            student_id = str(row['学号'])
            real_name = str(row['姓名']).strip()
            class_name = str(row.get('班级', 'N/A'))
            
            # 跳过无效数据
            if not student_id or not real_name or student_id == 'nan':
                continue
            
            # 确保学号是字符串格式
            student_id = student_id.split('.')[0]  # 移除可能的小数点
            
            # 生成用户名和默认密码
            username = f"student_{student_id}"
            default_password = student_id  # 使用完整学号作为默认密码
            
            print(f"{index + 1:3d}. 学号: {student_id:15s} | 姓名: {real_name:10s} | 班级: {class_name:10s}")
            print(f"     → 用户名: {username:25s} | 默认密码: {default_password}")
        
        print("-" * 60)
        print(f"\n✓ 测试完成！共 {len(df)} 条有效记录")
        print("\n如果数据正确，可以运行实际导入命令:")
        print(f"python3 scripts/import_class_students.py \\")
        print(f"  --excel \"{excel_file}\" \\")
        print(f"  --class-name \"班级名称\" \\")
        print(f"  --course-id 1 \\")
        print(f"  --teacher-id 1")
        
        return True
        
    except Exception as e:
        print(f"\n❌ 错误: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("用法: python3 scripts/test_import.py <Excel文件路径>")
        print("示例: python3 scripts/test_import.py 25土管名单新.xls")
        sys.exit(1)
    
    excel_file = sys.argv[1]
    success = test_excel_reading(excel_file)
    sys.exit(0 if success else 1)
