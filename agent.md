# Agent Context & Rules

## Project Overview
**English Learning Platform** (LangHack/learn-english)
A full-stack web application for English learning, featuring multi-role support (Admin, Teacher, Student, User), course management, interactive text learning, and vocabulary tracking.

## Tech Stack
### Backend
- **Language**: Java 17 (Recommended), compatible with 11+.
- **Framework**: Spring Boot 2.7.14.
- **ORM**: MyBatis Plus.
- **Database**: MySQL 8.0.
- **Security**: Spring Security + JWT.
- **Tools**: Maven, Flyway (for DB migrations).
- **Key Libraries**: WordNet (JWI), ECDICT.

### Frontend
- **Framework**: Vue 3 (Composition API).
- **Build Tool**: Vite.
- **UI Component Library**: Element Plus.
- **State Management**: Pinia (or Vuex, check usage).
- **Router**: Vue Router.
- **HTTP Client**: Axios.
- **Styling**: SCSS.
- **Mobile**: Capacitor (Android/iOS).

## Architecture
- **Monorepo-like Structure**:
  - `/backend`: Spring Boot application.
  - `/frontend`: Vue 3 application.
  - `/database`: Raw SQL scripts and seeds.
  - `/ECDICT-master`: Dictionary data.
- **Database Migrations**:
  - Uses Flyway.
  - Migration scripts location: `backend/src/main/resources/db/migration/`.
  - Format: `V{version}__{description}.sql`.
  - **IMPORTANT**: Always use Flyway for schema changes. Do not manually execute SQL unless debugging.
- **Media Storage**:
  - Images and videos are stored as **BLOBs** in the database (`media` table).
  - Accessed via `/api/media/{id}/download`.

## Coding Conventions
### Backend
- **Layered Architecture**: `Controller` -> `Service` -> `Mapper` -> `Database`.
- **Entities**: Located in `com.english.entity`. Use Lombok for getters/setters (if present, check `pom.xml`).
- **API Response**: All APIs should return a unified response structure (e.g., `Result<T>` or similar wrapper).
- **Security**:
  - Admin endpoints: `/api/admin/**`
  - Teacher endpoints: `/api/teacher/**`
  - Student endpoints: `/api/student/**`
  - Public endpoints: `/api/public/**`

### Frontend
- **Components**: Use `<script setup>` syntax.
- **API Calls**: Encapsulate in `src/api/*.js` modules.
- **Views**: Organized by role (`views/admin`, `views/student`, etc.).
- **Styling**: Scoped SCSS preferred.

## Development Workflow (Spec Driven Development)
1.  **Read `docs/spec.md`**: Understand the current task, requirements, and user story.
2.  **Update `docs/spec.md`**: Mark checklist items as in-progress.
3.  **Implement**: Write code conforming to the patterns above.
4.  **Verify**:
    - Backend: `mvn test` (if tests exist), check logs.
    - Frontend: `npm run dev`, check browser console.
    - Manual: Verify against the `Verification Plan` in `spec.md`.
5.  **Finalize**: Update `docs/spec.md` to mark task as completed.

## Critical Rules
- **Database**: Never change the DB schema without a corresponding Flyway migration script.
- **Markdown**: The system supports Markdown content in `texts` table. Ensure any new content handling supports this.
- **Encoding**: Ensure `UTF-8` is used everywhere, especially for Chinese/English dictionary data.
- **Ports**:
  - Backend: 8080
  - Frontend: 3000 (or 8888 as seen in PM2 config)
