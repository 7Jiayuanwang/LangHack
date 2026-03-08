# Article Version System - Implementation Walkthrough

## Overview

Successfully implemented a multi-version article system that allows users to view different versions of articles (CET4, CET6, Academic, Chinese-English Parallel) on the article detail page.

## Changes Made

### Database Changes

#### Migration File

**[NEW]** [V9__add_chinese_parallel_content.sql](file:///home/teruun/projects/LangHack/backend/src/main/resources/db/migration/V9__add_chinese_parallel_content.sql)

Added new column to `texts` table:

```sql
ALTER TABLE texts ADD COLUMN chinese_parallel_content TEXT COMMENT '中英对照版本内容';
```

---

### Backend Changes

#### 1. Entity Updates

**[MODIFIED]** [Text.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/entity/Text.java)

Added fields for all version types:
- `cet4Content` - CET-4 level version
- `cet6Content` - CET-6 level version  
- `academicContent` - Academic version
- `chineseParallelContent` - Chinese-English parallel version

Each field includes proper `@TableField` annotations and getter/setter methods.

---

#### 2. DTO Creation

**[NEW]** [TextVersionsDTO.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/dto/TextVersionsDTO.java)

Created DTO to return article with all available versions:
- Contains a `Map<String, String>` for version content
- Includes a `List<String>` of available version keys
- Helper method `addVersion()` to add versions only if content exists

---

#### 3. Controller Updates

**[MODIFIED]** [PublicTextController.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/controller/PublicTextController.java)

Updated `getTextDetail` endpoint:
- Returns `TextVersionsDTO` instead of `Text`
- Builds version map with all available versions
- Only includes versions that have content

---

### Frontend Changes

#### 1. Version Selector Component

**[NEW]** [VersionSelector.vue](file:///home/teruun/projects/LangHack/frontend/src/components/VersionSelector.vue)

Created reusable version selector component:
- Displays tabs for each available version
- Highlights the currently selected version
- Emits `version-change` event when user clicks a tab
- Responsive design for mobile devices
- Beautiful gradient styling for active tab

Version labels:
- `original` → 原文
- `cet4` → CET-4
- `cet6` → CET-6
- `academic` → 学术版
- `chinese_parallel` → 中英对照

---

**[MODIFIED]** [LearningRecordsServiceImpl.java](file:///home/teruun/projects/LangHack/backend/src/main/java/com/english/service/impl/LearningRecordsServiceImpl.java)

Updated `getTextDetailWithAnnotation` method to include version data:
- Builds version map with all available versions
- Returns `versions` and `availableVersions` fields
- Used by student text learning pages

---

#### 2. Article Detail Page Updates

**[MODIFIED]** [SimpleTextView.vue](file:///home/teruun/projects/LangHack/frontend/src/views/user/SimpleTextView.vue)

Enhanced article detail page:
- Added `VersionSelector` component
- Added `versions` ref to store all available versions
- Added `selectedVersion` ref to track current version
- Updated `loadTextDetail()` to handle version data from API
- Added `handleVersionChange()` to switch between versions
- Version selector only shows when multiple versions are available

---

#### 3. Student Text Learning Page Updates

**[MODIFIED]** [InteractiveText.vue](file:///home/teruun/projects/LangHack/frontend/src/views/public/InteractiveText.vue)

Enhanced student text learning page:
- Added `VersionSelector` component
- Added version switching functionality
- Updated to use version data from student API
- Maintains all existing learning features (word clicking, progress tracking)

---

## How It Works

### Backend Flow

1. Client requests article detail: `GET /api/public/texts/{id}`
2. Controller retrieves `Text` entity from database
3. Controller builds `TextVersionsDTO` with all non-null versions
4. Response includes:
   ```json
   {
     "id": 1,
     "title": "Article Title",
     "versions": {
       "original": "Original content...",
       "cet4": "CET-4 version...",
       "cet6": "CET-6 version..."
     },
     "availableVersions": ["original", "cet4", "cet6"]
   }
   ```

### Frontend Flow

1. Article detail page loads and fetches article data
2. Stores all versions in `versions` ref
3. Displays original version by default
4. Shows version selector if multiple versions exist
5. When user clicks a version tab:
   - Updates `selectedVersion`
   - Replaces `textContent` with selected version
   - Re-processes content for clickable words

---

## Usage

### For Users

1. Navigate to any article detail page
2. If the article has multiple versions, a version selector will appear below the article title
3. Click on any version tab to switch between versions
4. The article content updates instantly
5. All features (word clicking, font size) work with all versions

### For Administrators

To add versions to an article:

1. Edit the article in the admin panel
2. Fill in the desired version fields:
   - CET-4 Content
   - CET-6 Content
   - Academic Content
   - Chinese-English Parallel Content
3. Save the article
4. Users will now see the version selector on that article

---

## Testing Recommendations

> [!IMPORTANT]
> The following manual testing should be performed:

1. **Version Switching**
   - Open an article with multiple versions
   - Click each version tab
   - Verify content changes correctly
   - Verify selected tab is highlighted

2. **Missing Versions**
   - Open an article with only one version
   - Verify version selector does not appear
   - Verify article displays correctly

3. **Responsive Design**
   - Test on mobile devices
   - Verify version tabs wrap properly
   - Verify tabs are easily clickable on touch devices

4. **Word Clicking Feature**
   - Switch between versions
   - Click on words in different versions
   - Verify word lookup works in all versions

---

## Database Migration

The migration will run automatically when the Spring Boot application starts (if using Flyway). To manually run the migration:

```sql
ALTER TABLE texts ADD COLUMN chinese_parallel_content TEXT COMMENT '中英对照版本内容';
```

---

## Summary

✅ **Completed Features:**
- Database schema updated with new column
- Backend API returns all available versions
- Frontend displays version selector
- Smooth version switching
- Responsive design
- Graceful handling of missing versions

🎨 **Design Highlights:**
- Beautiful gradient styling for active version tab
- Smooth hover effects
- Mobile-responsive layout
- Consistent with existing design system

📝 **Next Steps:**
- Test with real article data
- Consider adding version badges on article list pages
- Consider persisting user's preferred version in localStorage
