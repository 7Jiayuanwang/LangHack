#!/bin/bash

# 安装 Python 依赖
echo "安装 Python 依赖..."
pip3 install mysql-connector-python

echo ""
echo "✓ 依赖安装完成"
echo ""
echo "现在可以运行导入脚本:"
echo "  python3 scripts/import_book1_readings.py"
