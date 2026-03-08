import os

# 1. 强制最先打印
print("========================================")
print("脚本已启动！")

# 2. 这里的路径请务必改为你真实的绝对路径！！
# 如果不确定，就在终端输入 pwd 看看
SOURCE_DIR = "/home/luosc/LangHack/frontend" 
OUTPUT_FILE = "/home/luosc/LangHack/frontend_code.md"

print(f"正在检查目录是否存在: {SOURCE_DIR}")

if not os.path.exists(SOURCE_DIR):
    print(f"错误：找不到目录！请确认路径是否正确。")
    print(f"当前脚本所在位置: {os.getcwd()}")
else:
    print(f"目录已找到，开始扫描...")
    
    collected = []
    # 递归扫描
    for root, dirs, files in os.walk(SOURCE_DIR):
        # 排除 node_modules
        if 'node_modules' in dirs:
            dirs.remove('node_modules')
            
        for file in files:
            if file.endswith('.vue') or file.endswith('.js'):
                full_path = os.path.join(root, file)
                collected.append(full_path)
                print(f"发现文件: {file}")

    if not collected:
        print("扫描完成，但没有找到任何 .vue 或 .js 文件。")
    else:
        print(f"正在写入文件，共 {len(collected)} 个...")
        with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
            f.write("# 代码收集\n\n")
            for p in collected:
                f.write(f"### {p}\n\n")
        print(f"写入成功！请查看: {OUTPUT_FILE}")

print("脚本执行结束。")
print("========================================")