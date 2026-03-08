# 英语学习平台 API 文档

## 课文习题功能

### 1. 创建课文并同时创建习题

**接口地址：** `POST /api/admin/texts`

**请求权限：** ADMIN

**请求体示例：**
```json
{
  "title": "Hello World",
  "content": "Hello! My name is Tom. What is your name? Nice to meet you!",
  "unitId": 1,
  "difficulty": "EASY",
  "audioUrl": "",
  "exercises": [
    {
      "question": "What is the name of the person in the text?",
      "exerciseType": "MULTIPLE_CHOICE",
      "orderNum": 1,
      "options": [
        {
          "optionContent": "Tom",
          "optionKey": "A",
          "isCorrect": true,
          "orderNum": 1
        },
        {
          "optionContent": "John",
          "optionKey": "B",
          "isCorrect": false,
          "orderNum": 2
        },
        {
          "optionContent": "Mike",
          "optionKey": "C",
          "isCorrect": false,
          "orderNum": 3
        },
        {
          "optionContent": "David",
          "optionKey": "D",
          "isCorrect": false,
          "orderNum": 4
        }
      ]
    },
    {
      "question": "What does the text say?",
      "exerciseType": "TRUE_FALSE",
      "orderNum": 2,
      "options": [
        {
          "optionContent": "The text is a greeting conversation",
          "optionKey": "A",
          "isCorrect": true,
          "orderNum": 1
        },
        {
          "optionContent": "The text is about business",
          "optionKey": "B",
          "isCorrect": false,
          "orderNum": 2
        }
      ]
    }
  ]
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "textId": 123,
    "message": "课文和习题创建成功",
    "exerciseCount": 2
  }
}
```

### 2. 获取课文详情（包含习题）

**接口地址：** `GET /api/admin/texts/{textId}`

**请求权限：** ADMIN

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 123,
    "unitId": 1,
    "title": "Hello World",
    "content": "Hello! My name is Tom. What is your name? Nice to meet you!",
    "difficulty": "EASY",
    "audioUrl": "",
    "orderNum": 1,
    "createdTime": "2023-11-23T10:30:00",
    "exercises": [
      {
        "id": 456,
        "question": "What is the name of the person in the text?",
        "exerciseType": "MULTIPLE_CHOICE",
        "orderNum": 1,
        "options": [
          {
            "id": 789,
            "optionContent": "Tom",
            "optionKey": "A",
            "isCorrect": true,
            "orderNum": 1
          },
          {
            "id": 790,
            "optionContent": "John",
            "optionKey": "B",
            "isCorrect": false,
            "orderNum": 2
          }
        ]
      }
    ],
    "exerciseCount": 1
  }
}
```

### 3. 批量创建习题（已存在课文的情况下）

**接口地址：** `POST /api/exercises/batch`

**请求权限：** ADMIN, TEACHER

**请求体示例：**
```json
{
  "textId": 123,
  "exercises": [
    {
      "question": "What is the main topic of this text?",
      "exerciseType": "MULTIPLE_CHOICE",
      "orderNum": 3,
      "options": [
        {
          "optionContent": "Introduction",
          "optionKey": "A",
          "isCorrect": true,
          "orderNum": 1
        },
        {
          "optionContent": "Farewell",
          "optionKey": "B",
          "isCorrect": false,
          "orderNum": 2
        }
      ]
    }
  ]
}
```

## 习题类型说明

### 1. MULTIPLE_CHOICE（选择题）
- 必须提供至少2个选项
- 必须指定一个正确答案（isCorrect: true）
- 选项标识通常是 A, B, C, D

### 2. TRUE_FALSE（判断题）
- 只需要提供2个选项
- 选项标识通常是 A, B 或 TRUE, FALSE
- 必须指定一个正确答案

### 3. FILL_BLANK（填空题）
- 选项字段可以留空或使用学生填写的内容
- 主要用于需要学生输入答案的题目

### 4. SHORT_ANSWER（简答题）
- 选项字段可以留空
- 用于需要学生输入简短回答的题目

## 学生答题功能

### 1. 提交答案

**接口地址：** `POST /api/student/exercises/submit`

**请求权限：** STUDENT, USER

**请求体示例：**
```json
{
  "exerciseId": 456,
  "answerContent": "A",
  "timeSpentSeconds": 30,
  "assignmentId": 789
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "success": true,
    "isCorrect": true,
    "message": "回答正确！"
  }
}
```

### 2. 获取课文习题完成进度

**接口地址：** `GET /api/student/exercises/text/{textId}/completed`

**请求权限：** STUDENT, USER

**请求参数：**
- `assignmentId`（可选）：任务ID

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "totalExercises": 3,
    "completedExercises": 2,
    "correctAnswers": 1,
    "progress": 67,
    "isCompleted": false,
    "accuracy": 50.0
  }
}
```

## 数据库结构

### text_exercises 表（课文习题表）
- `id`: 主键
- `text_id`: 课文ID
- `question`: 题目内容
- `exercise_type`: 习题类型（MULTIPLE_CHOICE, TRUE_FALSE, FILL_BLANK, SHORT_ANSWER）
- `order_num`: 显示顺序
- `created_by`: 创建者ID
- `created_time`: 创建时间
- `updated_time`: 更新时间

### exercise_options 表（习题选项表）
- `id`: 主键
- `exercise_id`: 习题ID
- `option_content`: 选项内容
- `option_key`: 选项标识（A, B, C, D等）
- `is_correct`: 是否正确答案
- `order_num`: 显示顺序
- `created_time`: 创建时间

### student_exercise_answers 表（学生答题记录表）
- `id`: 主键
- `exercise_id`: 习题ID
- `student_id`: 学生ID
- `assignment_id`: 任务ID（可选）
- `answer_content`: 学生答案
- `is_correct`: 是否正确
- `answer_time`: 答题时间
- `time_spent_seconds`: 答题用时（秒）
- `created_time`: 创建时间