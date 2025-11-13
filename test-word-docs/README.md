# 测试 Word 文档说明

此文件夹包含两个文本文件，用于测试课文导入功能。

## 文件说明

- `Lesson1_Excuse_me.txt` - 新概念英语第一册 Lesson 1
- `Lesson2_Is_this_your.txt` - 新概念英语第一册 Lesson 2

## 如何转换为 Word 文档

### 方法 1：使用 Microsoft Word

1. 打开 Microsoft Word
2. 点击"文件" -> "打开"
3. 选择 `.txt` 文件
4. 点击"文件" -> "另存为"
5. 在"文件类型"中选择"Word 文档 (*.docx)"
6. 保存文件

### 方法 2：使用记事本/文本编辑器

1. 用记事本打开 `.txt` 文件
2. 复制全部内容
3. 打开 Microsoft Word 或 WPS Office
4. 粘贴内容
5. 保存为 `.docx` 格式

### 方法 3：在线转换工具

访问以下任意在线转换网站：
- https://convertio.co/zh/txt-docx/
- https://www.online-convert.com/
- https://www.zamzar.com/

上传 `.txt` 文件并转换为 `.docx` 格式。

## 测试步骤

转换完成后，按照 `WORD_IMPORT_TEST_GUIDE.md` 中的说明进行测试：

1. 运行 `TEST_DATA_SETUP.sql` 脚本创建测试数据
2. 启动后端和前端服务
3. 登录管理员账号
4. 进入"课文管理"页面
5. 选择"新概念英语第一册"课程
6. 选择对应的单元
7. 导入转换好的 Word 文档
8. 测试点击查词功能

## 注意事项

- 确保 Word 文档使用 `.docx` 或 `.doc` 格式
- 文件名会自动成为课文标题
- 建议保持原文件名以便识别
- 上传前请确保已经创建了对应的课程和单元
