# Project Specification: Learning Enhancements

This document outlines the features implemented to improve the student learning experience, focusing on reading tracking, vocabulary mastery, and system reliability.

## 1. Manual Reading Task Completion
**Goal**: Replace the automatic 10-minute timer with manual completion to accurately reflect student effort.

### Features
- **Session Tracking**: Recordings start when the lesson page loads and end when the "Manual Complete" button is clicked.
- **Duration Logging**: Total duration (seconds) is calculated on the backend and stored in `text_learning_records`.
- **Database**: New table `text_learning_records` tracks `student_id`, `text_id`, `start_time`, `end_time`, and `is_completed`.

---

## 2. Vocabulary Mastery (SM-2 Algorithm)
**Goal**: Implement a scientifically backed spaced repetition algorithm for effective vocabulary memorization.

### Logic (SM-2)
- **Quality (Q)**: Students rate their recall from 0 (forgot) to 5 (perfect).
- **Interval (I)**: The gap between reviews increases exponentially for successful recalls.
- **Easiness Factor (EF)**: Adjusts based on recall quality; higher quality leads to faster interval growth.
- **Mastery State**: Tracks `repetition_count`, `easiness_factor`, and `next_review_date`.

### Implementation
- **Frontend**: Algorithm logic in `src/utils/sm2.js`.
- **Backend**: DB schema updated to store SM-2 parameters for each user-word pair.

---

## 3. Enhanced Word Inquiry & Persistence
**Goal**: Provide rich linguistic context and ensure every queried word is available for future study.

### Features
- **Multi-Source Lookup**: Integrates local Database, ECDICT, and WordNet.
- **Automatic Persistence**: Words found in ECDICT but missing from the local DB are automatically created and saved (via `wordNetService.getOrCreateWord`).
- **Rich Context**: Word popups now include:
    - Phonetic symbols
    - Audio pronunciation
    - Chinese definitions and English meanings
    - Synonyms and antonyms
    - "Add to Vocabulary" capability

---

## 4. Technical Infrastructure & Quality Assurance
**Goal**: Ensure system stability and developer productivity.

### Technical Stack
- **Runtime**: Java 17 (OpenJDK 17).
- **Build Tool**: Maven.
- **Database**: MySQL with Flyway migration support.

### Testing Framework
- **Unified Test Runner**: `scripts/run_all_tests.sh` script executes both:
    - **Backend (JUnit 5)**: Service layer mocks (LearningRecords, WordNet).
    - **Frontend (Node.js)**: Unit tests for SM-2 algorithm.
- **Continuous Logic Verification**: Standalone test scripts in `/src/utils/` for core business logic.

---

## 5. Interactive Reading & Social Features
**Goal**: Enhance the reading experience with instant actions and collaborative tools.

### Features
- **Selection Context Menu**: When text is selected during reading, a floating menu appears with the following options:
    - **Copy**: Copy the selected text to the clipboard.
    - **Underline (划线)**: Highlight the selected text with an underline.
    - **Share**: Create a shareable link or snippet (Note: Implementation planned for future).
    - **AI QA**: Redirects to a new page where the student can ask the AI questions about the selected context.
- **Shared Underlines**: Underlines created by any student are visible to all other students reading the same text, facilitating collaborative annotation and discovery of key content.
- **Contextual AI Interaction**: Clicking "AI QA" opens a dedicated interface where the AI is pre-loaded with the selected text, allowing for immediate analysis or explanation.

## Status
- [x] Reading Tracking Implementation
- [x] SM-2 Algorithm Integration
- [x] Enhanced Word Lookup
- [x] Unified Testing Framework
- [ ] Selection Context Menu (Copy, Underline, AI QA)
- [ ] Shared Underlines Infrastructure
- [ ] AI QA New Page Redirect


