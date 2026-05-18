#!/bin/bash
# 检查所有 CDSP 接口文档的请求方法

DOC_DIR="E:/项目/兴趣小组/智能与安全社团/服务器使用/数据资源/接口文档"

echo "=== CDSP 接口文档请求方法检查 ==="
echo ""

# 9 个接口文档
docs=(
    "行政班基本信息接口文档.pdf"
    "本专科生课程信息接口文档.pdf"
    "课程基本信息接口文档.pdf"
    "教师授课接口文档.pdf"
    "课程安排信息接口文档.pdf"
    "学生选课接口文档.pdf"
    "本专科生等级考试成绩接口文档.pdf"
    "教学计划信息接口文档.pdf"
    "学生课表接口文档.pdf"
)

for doc in "${docs[@]}"; do
    echo "文档: $doc"
    # 提取第一页，查找"请求方法"行
    pdftotext -f 1 -l 1 "$DOC_DIR/$doc" - 2>/dev/null | grep -A 1 "请求方法" | tail -1 | sed 's/^[[:space:]]*//'
    echo ""
done
