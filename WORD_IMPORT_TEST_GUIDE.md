# 课文管理功能测试指南

## 功能说明

已实现管理员端的 Word 课文导入、展示和点击查词功能。

### 功能特点：
1. **Word 文档导入** - 支持 .doc 和 .docx 格式
2. **课文展示** - 按课程和单元组织展示
3. **点击查词** - 在课文详情页面选中单词即可查看释义

## 测试前准备

### 1. 启动后端服务
```bash
cd backend
mvn spring-boot:run
```

### 2. 启动前端服务
```bash
cd frontend
npm install  # 首次运行需要安装依赖
npm run dev
```

### 3. 准备测试数据

#### 3.1 登录系统
- 访问 http://localhost:3000
- 使用管理员账号登录（根据你的数据库配置）

#### 3.2 创建测试课程和单元

在管理员端操作：
1. 点击"课程管理"
2. 添加一个测试课程，例如：
   - 课程名称：新概念英语第一册
   - 描述：适合英语初学者
   - 状态：启用

3. 创建单元（需要先通过数据库或API创建）
   使用以下 SQL 语句直接插入测试单元：
   ```sql
   INSERT INTO units (course_id, name, description, order_num, created_time, updated_time)
   VALUES
   (1, 'Lesson 1', 'Excuse me!', 1, NOW(), NOW()),
   (1, 'Lesson 2', 'Is this your...?', 2, NOW(), NOW());
   ```
   注意：将 course_id 替换为实际创建的课程ID

#### 3.3 准备测试 Word 文档

创建两个 Word 文档（.docx 格式）：

**文件1: Lesson1_Excuse_me.docx**
```
Excuse me!

Excuse me.
Yes?
Is this your handbag?
Pardon?
Is this your handbag?
Yes, it is.
Thank you very much.

NEW WORDS:
excuse /ɪk'skjuːz/ v. 原谅
me /miː/ pron. 我（宾格）
yes /jes/ adv. 是的
is /ɪz/ v. be动词现在时第三人称单数
this /ðɪs/ pron. 这
your /jɔː/ adj. 你的，你们的
handbag /'hændbæɡ/ n. （女用）手提包
pardon /'pɑːdn/ int. 原谅，请再说一遍
it /ɪt/ pron. 它
thank you 感谢你（们）
very much 非常地
```

**文件2: Lesson2_Is_this_your.docx**
```
Is this your...?

My coat and my umbrella please.
Here is my ticket.
Thank you, sir.
Number five.
Here's your umbrella and your coat.
This is not my umbrella.
Sorry, sir.
Is this your umbrella?
No, it isn't.
Is this it?
Yes, it is.
Thank you very much.

NEW WORDS:
coat /kəʊt/ n. 上衣，外衣
umbrella /ʌm'brelə/ n. 伞
please /pliːz/ int. 请
here /hɪə/ adv. 这里
ticket /'tɪkɪt/ n. 票
number /'nʌmbə/ n. 号码
five /faɪv/ num. 五
sorry /'sɒrɪ/ adj. 对不起的
sir /sɜː/ n. 先生
```

#### 3.4 添加单词到词典

为了测试点击查词功能，需要在数据库中添加一些单词：

```sql
INSERT INTO words (word, pronunciation, definition, part_of_speech, example_sentence, difficulty, deleted, created_time, updated_time)
VALUES
('excuse', '/ɪkˈskjuːz/', '原谅；宽恕', 'v.', 'Excuse me, is this your book?', 'EASY', 0, NOW(), NOW()),
('handbag', '/ˈhændbæɡ/', '（女用）手提包', 'n.', 'This is my handbag.', 'EASY', 0, NOW(), NOW()),
('umbrella', '/ʌmˈbrelə/', '伞', 'n.', 'This is your umbrella.', 'EASY', 0, NOW(), NOW()),
('ticket', '/ˈtɪkɪt/', '票；券', 'n.', 'Here is my ticket.', 'EASY', 0, NOW(), NOW()),
('coat', '/kəʊt/', '上衣；外衣', 'n.', 'My coat and my umbrella please.', 'EASY', 0, NOW(), NOW()),
('thank', '/θæŋk/', '感谢', 'v.', 'Thank you very much.', 'EASY', 0, NOW(), NOW()),
('please', '/pliːz/', '请', 'int.', 'My coat please.', 'EASY', 0, NOW(), NOW()),
('sorry', '/ˈsɒrɪ/', '对不起的；抱歉的', 'adj.', 'Sorry, this is not my umbrella.', 'EASY', 0, NOW(), NOW());
```

## 测试步骤

### 1. 导入 Word 课文

1. 登录管理员账号
2. 点击左侧菜单"课文管理"
3. 在下拉框中选择"新概念英语第一册"
4. 选择"Lesson 1"单元
5. 点击"导入Word课文"按钮
6. 选择 `Lesson1_Excuse_me.docx` 文件
7. 点击"确定上传"

重复以上步骤导入第二个文档。

### 2. 查看课文列表

选择课程后，页面会自动显示该课程下所有已导入的课文，以卡片形式展示。

### 3. 测试点击查词功能

1. 点击任意课文卡片，打开课文详情对话框
2. 在左侧课文内容区域，用鼠标选中一个单词（如 "excuse"）
3. 右侧会自动显示该单词的详细信息：
   - 单词
   - 音标
   - 词性
   - 释义
   - 例句

4. 尝试选中词典中不存在的单词，会显示"词典中暂无此单词"

## 功能亮点

1. **课程组织** - 显示当前选择的是哪本书（课程）
2. **自动解析** - Word 文档自动解析为纯文本
3. **标题提取** - 文件名自动作为课文标题
4. **即时查词** - 选中单词立即显示释义，无需额外点击
5. **优雅展示** - 课文以段落形式展示，阅读体验好

## 注意事项

1. Word 文档应使用标准格式，避免复杂的表格和图片
2. 选中的必须是纯英文单词（只包含字母）
3. 查词功能依赖于 words 表中的数据
4. 单元需要关联到课程才能正常显示

## 技术实现

- **后端**: Apache POI 解析 Word 文档
- **前端**: Element Plus 组件库
- **查词**: 通过 window.getSelection() 获取选中文本
- **实时查询**: 选中单词后立即调用 API 查询

## 故障排查

1. **上传失败**
   - 检查文件格式是否为 .doc 或 .docx
   - 查看后端日志确认错误信息

2. **课文列表为空**
   - 确认已选择课程
   - 检查该课程下是否有关联的单元和课文

3. **查词无反应**
   - 确认选中的是英文单词
   - 检查 words 表中是否有该单词的数据
   - 打开浏览器控制台查看 API 调用情况

4. **单元下拉框为空**
   - 确认数据库中该课程下是否有单元数据
   - 检查 units 表的 course_id 字段
