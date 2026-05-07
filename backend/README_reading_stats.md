# 阅读统计功能实现方案

## 功能概述

实现了两种不同的阅读统计：
- **阅读次数**：用户每次打开文章详情都+1
- **阅读篇数**：同一用户同一篇文章，仅第一次阅读时+1，重复打开不增加

## 数据库变更

### 1. 表结构修改

```sql
-- 添加阅读次数字段和最后阅读时间字段
ALTER TABLE user_text_reads 
ADD COLUMN read_count INT DEFAULT 1 COMMENT '阅读次数' AFTER read_at,
ADD COLUMN last_read_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后阅读时间' AFTER read_count;
```

### 2. 索引优化

```sql
-- 删除原有唯一约束，添加新的唯一约束和性能索引
DROP INDEX uk_user_text ON user_text_reads;
ALTER TABLE user_text_reads ADD UNIQUE KEY uk_user_text (user_id, text_id);
CREATE INDEX idx_user_read_count ON user_text_reads(user_id, read_count);
CREATE INDEX idx_text_read_count ON user_text_reads(text_id, read_count);
```

## 代码实现

### 1. Entity层 (UserTextRead.java)

新增字段：
- `readCount`: 阅读次数
- `lastReadAt`: 最后阅读时间

### 2. Mapper层 (UserTextReadMapper.java)

新增方法：
- `countReadArticles(userId)`: 统计用户阅读篇数
- `sumReadCounts(userId)`: 统计用户总阅读次数
- `incrementReadCount(userId, textId)`: 原子递增阅读次数

### 3. Controller层 (PublicTextController.java)

#### 核心接口：

1. **记录阅读行为** `POST /api/public/texts/{textId}/read`
   - 首次阅读：创建记录，阅读篇数+1，阅读次数=1
   - 重复阅读：仅阅读次数+1

2. **获取阅读统计** `GET /api/public/texts/reading-stats`
   - 返回用户的阅读篇数和总阅读次数

3. **文章详情** `GET /api/public/texts/{textId}`
   - 自动记录用户阅读行为
   - 原子递增文章总浏览次数

#### DTO类：

```java
class UserReadingStats {
    private Integer readArticlesCount;  // 阅读篇数
    private Integer totalReadCount;      // 阅读次数
}
```

## 使用示例

### 前端调用

```javascript
// 用户打开文章详情时自动记录
fetch('/api/public/texts/123/read', { method: 'POST' });

// 获取用户阅读统计
fetch('/api/public/texts/reading-stats')
  .then(res => res.json())
  .then(data => {
    console.log('阅读篇数:', data.data.readArticlesCount);
    console.log('阅读次数:', data.data.totalReadCount);
  });
```

### 数据示例

| user_id | text_id | read_count | first_read_at | last_read_at |
|---------|---------|------------|---------------|--------------|
| 1       | 100     | 5          | 2024-01-01    | 2024-01-05   |
| 1       | 101     | 2          | 2024-01-02    | 2024-01-04   |
| 1       | 102     | 1          | 2024-01-03    | 2024-01-03   |

用户1的统计：
- 阅读篇数：3篇
- 阅读次数：5+2+1=8次

## 注意事项

1. **并发安全**：使用数据库原子操作确保并发情况下数据一致性
2. **性能优化**：添加了必要的数据库索引
3. **兼容性**：保持原有API接口不变，仅扩展功能
4. **数据迁移**：现有数据会自动初始化 `read_count=1`
