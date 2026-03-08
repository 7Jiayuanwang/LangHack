# 音频文件配置指南

## 概述

本指南说明如何配置和使用词汇音频文件。

## 📁 文件结构

```
learn-english/
├── book1/
│   └── audio/          # 音频文件目录
│       ├── campus.mp3
│       ├── hello.mp3
│       └── ...         # 641 个音频文件
├── backend/
│   └── src/main/java/com/english/config/
│       └── WebMvcConfig.java  # 音频路径映射配置
└── scripts/
    ├── link_audio_to_words.py  # 音频关联脚本
    └── test_audio_access.py    # 音频访问测试
```

## 🎵 音频文件

### 位置
- **物理路径**: `book1/audio/*.mp3`
- **访问URL**: `http://localhost:8080/audio/vocabulary/{filename}.mp3`

### 统计
- **总数**: 641 个音频文件
- **格式**: MP3
- **命名**: 与单词名称对应（如 `campus.mp3`）

## 🔗 音频关联

### 数据库字段
- **表**: `words`
- **字段**: `audio_url`
- **格式**: `/audio/vocabulary/{filename}.mp3`

### 关联状态
- **已关联**: 631 个单词
- **未关联**: 11 个单词（没有对应的音频文件）

### 示例
```sql
SELECT word, audio_url FROM words LIMIT 5;
```

| 单词 | 音频URL |
|------|---------|
| campus | /audio/vocabulary/campus.mp3 |
| stain | /audio/vocabulary/stain.mp3 |
| mattress | /audio/vocabulary/mattress.mp3 |
| heartbeat | /audio/vocabulary/heartbeat.mp3 |
| literally | /audio/vocabulary/literally.mp3 |

## ⚙️ 后端配置

### WebMvcConfig.java

后端通过 `WebMvcConfig` 配置静态资源映射：

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String projectRoot = Paths.get("").toAbsolutePath().getParent().toString();
        String audioPath = "file:" + projectRoot + "/book1/audio/";
        
        registry.addResourceHandler("/audio/vocabulary/**")
                .addResourceLocations(audioPath)
                .setCachePeriod(3600); // 缓存1小时
    }
}
```

### 路径映射
- **URL模式**: `/audio/vocabulary/**`
- **文件系统**: `{project_root}/book1/audio/`
- **缓存**: 1小时

## 🧪 测试音频访问

### 方法1: 使用测试脚本

```bash
python3 scripts/test_audio_access.py
```

### 方法2: 浏览器访问

启动后端服务后，在浏览器中访问：
```
http://localhost:8080/audio/vocabulary/campus.mp3
```

### 方法3: curl 命令

```bash
# 测试音频文件是否可访问
curl -I http://localhost:8080/audio/vocabulary/campus.mp3

# 下载音频文件
curl -O http://localhost:8080/audio/vocabulary/campus.mp3
```

## 🔄 重新关联音频

如果需要重新关联音频文件（例如添加了新的音频文件）：

```bash
python3 scripts/link_audio_to_words.py
```

脚本功能：
1. 扫描 `book1/audio/` 目录
2. 匹配数据库中的单词
3. 更新 `audio_url` 字段

## 📊 查询统计

### 查看所有有音频的单词

```sql
SELECT COUNT(*) as 有音频的单词数
FROM words
WHERE deleted = 0 
  AND source = 'book1_vocabulary'
  AND audio_url IS NOT NULL 
  AND audio_url != '';
```

### 查看没有音频的单词

```sql
SELECT word
FROM words
WHERE deleted = 0 
  AND source = 'book1_vocabulary'
  AND (audio_url IS NULL OR audio_url = '');
```

### 按单元统计音频

```sql
SELECT 
    u.name AS 单元,
    COUNT(DISTINCT w.id) AS 单词总数,
    COUNT(DISTINCT CASE WHEN w.audio_url IS NOT NULL AND w.audio_url != '' 
          THEN w.id END) AS 有音频的单词数
FROM units u
JOIN unit_words uw ON u.id = uw.unit_id
JOIN words w ON uw.word_id = w.id
JOIN courses c ON u.course_id = c.id
WHERE c.name = '大学英语1' AND w.deleted = 0
GROUP BY u.id, u.name
ORDER BY u.order_num;
```

## 🎯 前端使用

### React 示例

```jsx
function WordAudio({ word }) {
  const audioUrl = word.audio_url;
  
  if (!audioUrl) {
    return null;
  }
  
  return (
    <audio controls>
      <source src={`http://localhost:8080${audioUrl}`} type="audio/mpeg" />
      您的浏览器不支持音频播放。
    </audio>
  );
}
```

### HTML5 Audio 标签

```html
<audio controls>
  <source src="http://localhost:8080/audio/vocabulary/campus.mp3" type="audio/mpeg">
  您的浏览器不支持音频播放。
</audio>
```

### JavaScript 播放

```javascript
function playAudio(audioUrl) {
  const audio = new Audio(`http://localhost:8080${audioUrl}`);
  audio.play();
}

// 使用
playAudio('/audio/vocabulary/campus.mp3');
```

## 🐛 故障排查

### 问题1: 音频文件无法访问

**症状**: 浏览器访问音频URL返回404

**解决方案**:
1. 检查后端服务是否运行
2. 检查 `WebMvcConfig.java` 是否正确配置
3. 检查音频文件是否存在于 `book1/audio/` 目录
4. 查看后端日志，确认路径映射是否成功

### 问题2: 音频URL为空

**症状**: 数据库中 `audio_url` 字段为空

**解决方案**:
```bash
# 重新运行关联脚本
python3 scripts/link_audio_to_words.py
```

### 问题3: 跨域问题

**症状**: 前端无法播放音频，控制台显示CORS错误

**解决方案**: 在 `SecurityConfig.java` 中添加CORS配置

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.addAllowedOrigin("http://localhost:3000");
    configuration.addAllowedMethod("*");
    configuration.addAllowedHeader("*");
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

## 📝 注意事项

1. **音频文件大小**: 平均每个文件约 20KB
2. **缓存策略**: 音频文件缓存1小时，减少服务器负载
3. **文件命名**: 音频文件名必须与单词完全匹配（不区分大小写）
4. **特殊字符**: 包含空格的单词（如 "stand out"）对应的文件名也包含空格
5. **URL编码**: 后端会自动处理URL编码

## 🔐 安全考虑

1. **路径遍历**: Spring Boot 自动防止路径遍历攻击
2. **文件类型**: 只允许访问 `.mp3` 文件
3. **访问控制**: 音频文件公开访问，无需认证
4. **缓存控制**: 设置合理的缓存时间，避免过度请求

## 📈 性能优化

1. **CDN**: 生产环境建议使用CDN分发音频文件
2. **压缩**: 音频文件已经是压缩格式（MP3）
3. **懒加载**: 前端按需加载音频，不要预加载所有音频
4. **缓存**: 利用浏览器缓存和HTTP缓存头
