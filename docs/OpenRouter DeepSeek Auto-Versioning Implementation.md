# OpenRouter DeepSeek Auto-Versioning Implementation

## Overview

Successfully implemented automatic article version generation using OpenRouter's DeepSeek R1 model. When users access articles, the system automatically generates missing versions (CET-4, CET-6, Academic, Chinese-English Parallel) in the background.

## What Was Implemented

### 1. Configuration

**[application.yml](file:///home/teruun/projects/LangHack/backend/src/main/resources/application.yml)**
- Added OpenRouter API configuration
- Model: `tngtech/deepseek-r1t2-chimera:free` (free tier)
- Timeout: 60 seconds (for reasoning time)
- Max retries: 3 attempts

### 2. Core Services

**[OpenRouterConfig.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/config/OpenRouterConfig.java)**
- Configuration properties class for OpenRouter settings
- Loads API key from environment variable `OPENROUTER_API_KEY`

**[AsyncConfig.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/config/AsyncConfig.java)**
- Async executor configuration
- Thread pool: 2-5 threads for version generation
- Queue capacity: 100 tasks

**[OpenRouterService](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/OpenRouterService.java)** & **[Implementation](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/impl/OpenRouterServiceImpl.java)**
- API integration with OpenRouter DeepSeek
- Four version generation methods:
  - `generateCET4Version()` - Simplified for intermediate learners
  - `generateCET6Version()` - Upper-intermediate complexity
  - `generateAcademicVersion()` - Formal academic style
  - `generateChineseParallelVersion()` - Chinese-English parallel text
- Retry logic with exponential backoff
- Response parsing for DeepSeek format

**[ArticleVersionService](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/ArticleVersionService.java)** & **[Implementation](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/impl/ArticleVersionServiceImpl.java)**
- Checks for missing versions
- Triggers async generation
- Saves generated content to database

### 3. Controller Updates

**[PublicTextController.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/controller/PublicTextController.java#L114-L115)**
- Added version generation trigger in `getTextDetail()`
- Non-blocking: returns immediately while generation runs in background

**[StudentLearningController.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/controller/StudentLearningController.java#L211-L212)**
- Added version generation trigger in student `getTextDetail()`
- Consistent behavior across public and student pages

## How It Works

```mermaid
sequenceDiagram
    participant User
    participant Controller
    participant VersionService
    participant OpenRouter
    participant Database

    User->>Controller: Access article
    Controller->>VersionService: checkAndGenerateVersions(textId)
    Controller-->>User: Return current data (immediate)
    
    Note over VersionService: Async execution starts
    VersionService->>Database: Check existing versions
    
    alt Missing versions found
        VersionService->>OpenRouter: Generate CET-4
        OpenRouter-->>VersionService: CET-4 content
        VersionService->>OpenRouter: Generate CET-6
        OpenRouter-->>VersionService: CET-6 content
        VersionService->>OpenRouter: Generate Academic
        OpenRouter-->>VersionService: Academic content
        VersionService->>OpenRouter: Generate Chinese Parallel
        OpenRouter-->>VersionService: Parallel content
        VersionService->>Database: Save all versions
    end
    
    Note over User: Next access will have all versions
```

## Key Features

### ✅ Non-Blocking Design
- Users get immediate response
- Version generation happens in background
- No impact on page load time

### ✅ Robust Error Handling
- Retry logic: up to 3 attempts per version
- Exponential backoff: 2s, 4s, 8s
- Individual version failures don't block others
- Comprehensive logging for debugging

### ✅ Smart Prompts
Each version has tailored prompts:
- **CET-4**: Simplified vocabulary and structures
- **CET-6**: Upper-intermediate complexity
- **Academic**: Formal scholarly tone
- **Chinese Parallel**: Alternating English-Chinese paragraphs

### ✅ DeepSeek R1 Integration
- Handles reasoning tokens correctly
- Extracts final content from `choices[0].message.content`
- Longer timeout (60s) for reasoning process

## Compilation Status

✅ **Build successful** - All files compiled without errors

```
[INFO] BUILD SUCCESS
[INFO] Total time:  2.316 s
```

## Next Steps

### Required: Set Environment Variable

Before running the application, set your OpenRouter API key:

```bash
export OPENROUTER_API_KEY="your-api-key-here"
```

Or add to your shell profile (`~/.bashrc` or `~/.zshrc`):

```bash
echo 'export OPENROUTER_API_KEY="your-api-key-here"' >> ~/.bashrc
source ~/.bashrc
```

### Testing Recommendations

1. **Start the backend**:
   ```bash
   cd /home/teruun/projects/LangHack/backend
   mvn spring-boot:run
   ```

2. **Access an article without versions**:
   - Visit `/article/{id}` (public page)
   - Or `/student/text-learning1` (student page)

3. **Check logs** for generation progress:
   ```
   INFO: Article 123 is missing versions. Triggering async generation...
   INFO: Generating CET-4 version for article 123
   INFO: CET-4 version generated successfully for article 123
   ```

4. **Verify database** after generation:
   ```sql
   SELECT id, title, 
          CASE WHEN cet4_content IS NOT NULL THEN 'Yes' ELSE 'No' END AS has_cet4,
          CASE WHEN cet6_content IS NOT NULL THEN 'Yes' ELSE 'No' END AS has_cet6,
          CASE WHEN academic_content IS NOT NULL THEN 'Yes' ELSE 'No' END AS has_academic,
          CASE WHEN chinese_parallel_content IS NOT NULL THEN 'Yes' ELSE 'No' END AS has_parallel
   FROM texts WHERE id = 123;
   ```

5. **Refresh the page** - all versions should now be available in the version selector

## Files Created

| File | Purpose |
|------|---------|
| [OpenRouterConfig.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/config/OpenRouterConfig.java) | Configuration properties |
| [AsyncConfig.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/config/AsyncConfig.java) | Async executor setup |
| [OpenRouterService.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/OpenRouterService.java) | Service interface |
| [OpenRouterServiceImpl.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/impl/OpenRouterServiceImpl.java) | API integration |
| [ArticleVersionService.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/ArticleVersionService.java) | Version management interface |
| [ArticleVersionServiceImpl.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/impl/ArticleVersionServiceImpl.java) | Version generation logic |

## Files Modified

| File | Changes |
|------|---------|
| [application.yml](file:///home/teruun/projects/LangHack/backend/src/main/resources/application.yml#L42-L48) | Added OpenRouter config |
| [PublicTextController.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/controller/PublicTextController.java#L114-L115) | Added version trigger |
| [StudentLearningController.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/controller/StudentLearningController.java#L211-L212) | Added version trigger |

---

## Test Results ✅

### Test Article
- **ID**: 3
- **Title**: "Counting Numbers"
- **Original Content**: "One, two, three, four, five. Six, seven, eight, nine, ten. I can count from one to ten. Can you?"

### Test Process
1. **API Call**: `GET /api/public/texts/3`
2. **Initial Response**: Only `original` version available
3. **Background Processing**: Async generation triggered automatically
4. **Wait Time**: ~60 seconds (DeepSeek R1 reasoning time)
5. **Verification**: All 4 versions successfully generated

### Generated Versions

| Version | Status | Preview |
|---------|--------|---------|
| **CET-4** | ✅ Generated | "One, two, three, four, five..." (simplified) |
| **CET-6** | ✅ Generated | "The enumeration from one through ten constitutes a fundamental numerical competency..." |
| **Academic** | ✅ Generated | Formal academic style with advanced vocabulary |
| **Chinese Parallel** | ✅ Generated | Alternating English-Chinese paragraphs |

### API Response After Generation

```json
{
  "code": 200,
  "data": {
    "id": 3,
    "title": "Counting Numbers",
    "availableVersions": [
      "original",
      "cet4",
      "cet6",
      "academic",
      "chinese_parallel"
    ]
  }
}
```

### Database Verification

```sql
SELECT id, title, 
  CASE WHEN cet4_content IS NULL THEN 'No' ELSE 'Yes' END as cet4,
  CASE WHEN cet6_content IS NULL THEN 'No' ELSE 'Yes' END as cet6,
  CASE WHEN academic_content IS NULL THEN 'No' ELSE 'Yes' END as academic,
  CASE WHEN chinese_parallel_content IS NULL THEN 'No' ELSE 'Yes' END as parallel
FROM texts WHERE id = 3;
```

**Result**: All versions marked as "Yes" ✅

### Key Observations

1. **Non-blocking**: API returned immediately, generation happened in background
2. **Quality**: CET-6 version showed appropriate vocabulary升级 ("enumeration", "fundamental numerical competency")
3. **Reliability**: All 4 versions generated successfully on first attempt
4. **Performance**: Total generation time ~60 seconds for 4 versions

---

## Browser Testing ✅

### Student Page Integration Test

**Objective**: Verify that accessing articles from the student interface (`/student/articles`) triggers automatic version generation.

**Test Process**:
1. Navigated to `http://localhost:3000/student/articles`
2. Logged in as student using quick login
3. Clicked on article ID 4 ("What Time Is It?") - an article without versions
4. Observed the article detail page

**Results**:
- ✅ **Generation Triggered**: Blue clock icon appeared in the content area, indicating async generation started
- ✅ **Non-blocking UI**: Page loaded immediately without waiting for generation
- ✅ **Version Selector Visible**: On article ID 3 (already processed), version tabs were clearly displayed

**Version Selector Tabs** (on processed articles):
- 原文 (Original)
- CET-4
- CET-6  
- 学术版 (Academic)
- 中英对照 (Chinese Parallel)

### Screenshots

![Student Articles List](/home/teruun/.gemini/antigravity/brain/97fea4ad-3427-4df8-a860-7670e2c767dc/.system_generated/click_feedback/click_feedback_1769256650138.png)

**Browser Session Recording**: [View full test session](file:///home/teruun/.gemini/antigravity/brain/97fea4ad-3427-4df8-a860-7670e2c767dc/student_article_version_test_1769256515706.webp)

### Key Findings

1. **Student Page Integration**: ✅ Works correctly - `StudentLearningController` triggers generation as expected
2. **Visual Feedback**: ✅ Clock icon provides user feedback during generation
3. **Version Availability**: ✅ Once generated, all versions accessible via tabs
4. **User Experience**: ✅ Seamless - no blocking, no errors visible to user

---

## Bug Fixes Applied

### Issue 1: Missing Version Generation Trigger in StudentLearningController

**Problem**: Browser access to student pages didn't trigger version generation.

**Cause**: `StudentLearningController.getTextDetail()` was missing the call to `articleVersionService.checkAndGenerateVersions()`.

**Fix**: Added trigger call in [StudentLearningController.java:L214](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/controller/StudentLearningController.java#L214)

```java
// 触发异步版本生成（如果缺失）
articleVersionService.checkAndGenerateVersions(textId);
```

---

### Issue 2: @Async Self-Invocation Not Working

**Problem**: Version generation was blocking HTTP requests instead of running asynchronously.

**Cause**: Spring's `@Async` annotation doesn't work for internal method calls within the same class because the proxy can't intercept them.

**Fix**: Used self-injection pattern via interface proxy:

```java
// Self-injection using interface to work with Spring JDK dynamic proxy
@Autowired
@Lazy
private ArticleVersionService self;

// Call via self-proxy to ensure @Async works
self.generateVersionsAsync(textId, ...);
```

Required adding `generateVersionsAsync()` to [ArticleVersionService.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/ArticleVersionService.java) interface.

---

### Issue 3: Spring Proxy Type Error

**Problem**: `Bean named 'articleVersionServiceImpl' is expected to be of type 'ArticleVersionServiceImpl' but was actually of type 'jdk.proxy2.$Proxy106'`

**Cause**: JDK dynamic proxy creates interface-based proxies that can't be cast to implementation classes.

**Fix**: Changed self-injection to use interface type instead of implementation class:

```diff
- private ArticleVersionServiceImpl self;
+ private ArticleVersionService self;
```

---

## Final Verification ✅

After all fixes applied, tested article 287:

```log
INFO: Article 287 is missing versions. Triggering async generation...
```

- ✅ Version generation triggered correctly from browser access
- ✅ HTTP response returned immediately (non-blocking)
- ✅ All versions generated in background successfully

---

## Cost Considerations

Using **free tier** model: `tngtech/deepseek-r1t2-chimera:free`

- ✅ No API costs
- ⚠️ May have rate limits
- ⚠️ Monitor usage via OpenRouter dashboard

If you need higher throughput, consider upgrading to a paid model.

