# 真题题库导入指南

## 页面入口

- 管理员：`/admin/past-papers`
- 学生：`/student/past-papers`

管理员可上传和删除共享试卷。学生、普通用户和教师可读取已发布试卷。

## 支持题型

- `single_choice`：四选一
- `cloze`：选词填空，支持 A-Z 词库答案
- `matching`：段落匹配，支持 A-Z 段落答案
- `translation`：翻译文本作答和参考译文
- `writing`：写作文本作答和参考范文

翻译和写作不自动判分，交卷后展示参考答案供学生自评。

## 网页导入

管理员登录后打开 `/admin/past-papers`，点击“导入”。选择 JSON 或标准 Markdown 文件，检查预览后提交。导入内容保存到 MySQL，所有学生共享。

导入弹窗提供两种模板：

- 选择题 Markdown 模板
- 全题型 JSON 模板

## 批量上传

```powershell
cd frontend
$env:LANGHACK_ADMIN_TOKEN="ADMIN_JWT"
node .\scripts\import-past-papers.mjs `
  --input "C:\path\to\papers" `
  --server "https://your-langhack-server"
Remove-Item Env:LANGHACK_ADMIN_TOKEN
```

文件名包含“四级/cet4”“六级/cet6”或“考研/postgraduate”时可自动判断分类。也可使用 `--category cet4|cet6|postgraduate` 强制指定。

## MinerU 四级转换

MinerU 的 `full.md` 是 OCR 结果，不能保证符合标准 Markdown 模板。对于当前支持的四级整套版式，可先运行：

```powershell
cd frontend
node .\scripts\convert-mineru-cet4.mjs `
  "C:\path\to\full.md" `
  "C:\path\to\paper.json"
```

转换器覆盖写作、听力、选词填空、段落匹配、仔细阅读和翻译，并校验各部分题目数量。由于不同来源的 OCR 结构可能变化，上传前必须抽查章节交界处、选项顺序、答案表和参考译文。

## 服务端要求

- 后端必须部署 `PastPaperController`
- Flyway 必须成功执行 `V38__past_paper_library.sql`
- 上传接口要求 `ADMIN` 权限
- 前端与后端必须同时部署，否则可能出现接口 404 或题型不兼容

不要把管理员 Token、真实数据库配置或未经许可公开发布的真题数据提交到 Git。
