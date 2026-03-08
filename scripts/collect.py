import os

def collect_files_to_markdown(source_dir, target_file, extensions):
    """
    source_dir: 要扫描的文件夹路径
    target_file: 生成的 md 或 txt 文件名
    extensions: 列表，例如 ['.java', '.sql', '.xml']
    """
    
    # 确保后缀名以点开头
    extensions = [ext if ext.startswith('.') else f'.{ext}' for ext in extensions]
    
    collected_files = []
    
    # 1. 先扫描符合条件的文件列表
    for root, dirs, files in os.walk(source_dir):
        # 排除隐藏目录（如 .git, .m2 等）
        dirs[:] = [d for d in dirs if not d.startswith('.')]
        
        for file in files:
            if any(file.endswith(ext) for ext in extensions):
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, source_dir)
                collected_files.append((full_path, rel_path))

    # 2. 写入目标文件
    with open(target_file, 'w', encoding='utf-8') as outfile:
        outfile.write(f"# 文件收集报告\n\n")
        outfile.write(f"**来源目录**: `{os.path.abspath(source_dir)}`\n")
        outfile.write(f"**包含后缀**: `{', '.join(extensions)}`\n\n")
        
        # 写入目录清单（区分和概览）
        outfile.write("## 处理文件清单\n")
        for _, rel_path in collected_files:
            outfile.write(f"- {rel_path}\n")
        outfile.write("\n---\n\n")
        
        # 写入具体内容（切割和区分）
        for full_path, rel_path in collected_files:
            file_ext = os.path.splitext(full_path)[1][1:] # 获取后缀名作为代码块语言
            
            outfile.write(f"### 文件路径: {rel_path}\n")
            outfile.write(f"文件位置: `{full_path}`\n\n")
            outfile.write(f"```{file_ext}\n")
            
            try:
                with open(full_path, 'r', encoding='utf-8') as infile:
                    outfile.write(infile.read())
            except Exception as e:
                outfile.write(f"读取文件失败: {str(e)}")
            
            outfile.write(f"\n```\n\n")
            outfile.write("---\n\n") # 文件切割线

    print(f"成功！已将 {len(collected_files)} 个文件收集到 {target_file}")

# --- 使用示例 ---
if __name__ == "__main__":
    # 配置参数
    project_path = "./backend"                # 当前目录
    output_name = "backend_code.md"     # 输出文件名
    # target_exts = [".sql", ".xml", ".java"]  # 需要收集的后缀
    target_exts = [".java"]
    # target_exts = [".vue"]
    
    collect_files_to_markdown(project_path, output_name, target_exts)