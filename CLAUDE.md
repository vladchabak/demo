# LocalPro — Claude Code Master Guide

## Project Overview

**LocalPro** — mobile marketplace for local services (Airbnb-style). Providers list services on a map; clients search by location/category/radius, then chat.

- `localPro-backend` — Java 21 + Spring Boot 3.3.6, package `com.localpro`
- `localPro-mobile` — Flutter 3 + Riverpod 2

**Environment:** JDK 21 LTS · Spring Boot 3.3.6 · Lombok 1.18.40 (pinned in pom.xml)

---

## Session Startup Prompt

```
I'm building LocalPro (mobile service marketplace).
Backend: Java 21 + Spring Boot 3.3, package com.localpro.
Frontend: Flutter 3 + Riverpod 2.
Current state: [what's done]
Today's task: [what to build]
Rules: Lombok @Data @Builder · MapStruct DTOs · Testcontainers integration tests · RESTful naming · UUID IDs · TIMESTAMPTZ timestamps
```

---

## Phase Checklist

**Backend**
- [ ] Phase 0 — Scaffold + Docker Compose + CI
- [ ] Phase 1 — Auth filter + User CRUD
- [ ] Phase 2 — Service Listings CRUD
- [ ] Phase 3 — PostGIS geo search
- [ ] Phase 4 — Media upload (Cloudinary)
- [ ] Phase 5 — WebSocket chat + FCM
- [ ] Phase 6 — Reviews + Payment stub
- [ ] Phase 7 — Docker + Railway deploy

**Frontend** (start after Backend Phase 1 deployed)
- [ ] Phase 0 — Flutter scaffold + GoRouter + Dio
- [ ] Phase 1 — Firebase Auth screens
- [ ] Phase 2 — Map + nearby search
- [ ] Phase 3 — ServiceCard + Listing detail
- [ ] Phase 4 — Chat (WebSocket + FCM)
- [ ] Phase 5 — Provider dashboard + CreateListing
- [ ] Phase 6 — Profile + polish + app icon

---

## Workflow

**Backend:** Flyway migration → entity/repo → service/tests → controller/integration test → `mvn verify` → commit

**Flutter:** Repository + API → Riverpod providers → Screen/Widgets → `flutter run` → `flutter test` → commit

**Git convention:** `feat:` `fix:` `test:` `chore:` `docs:`

---

## API Contract

Base URL: `http://localhost:8080` (dev) · `https://localpro-api.railway.app` (prod)  
Auth: `Authorization: Bearer <firebase_id_token>`

```json
// Success
{ "data": { ... }, "page": { "number": 0, "size": 20, "totalElements": 150, "totalPages": 8 } }

// Error
{ "code": "LISTING_NOT_FOUND", "message": "...", "fieldErrors": [], "timestamp": "..." }
```

---

## Environment Variables

```
# Backend (.env)
DB_URL=jdbc:postgresql://localhost:5432/localpro_dev
DB_USER=localpro  DB_PASS=localpro
GOOGLE_APPLICATION_CREDENTIALS=./firebase-service-account.json
CLOUDINARY_URL=cloudinary://API_KEY:API_SECRET@CLOUD_NAME
SERVER_PORT=8080  ALLOWED_ORIGINS=http://localhost:3000

# Frontend (dart-define)
API_BASE_URL=http://10.0.2.2:8080   # Android emulator
API_BASE_URL=http://localhost:8080   # iOS simulator
```

---

## Testing Strategy

- **Backend unit:** service layer with mocked repos
- **Backend integration:** Testcontainers (real PostgreSQL + PostGIS) for all controllers
- **Flutter widget:** ServiceCard, ChatBubble, FilterBar
- **Flutter integration:** auth flow, map screen (mocked API)

---

## Coding Patterns

### Entity / Lombok
- `@Builder.Default` on any field with an initializer (enum defaults, `BigDecimal.ZERO`, etc.) — without it Lombok builder ignores initializers → null → NOT NULL violation.
- `insertable = false, updatable = false` on `createdAt`/`updatedAt` — DB trigger owns these.
- `@ToString(exclude = "photos")` + `@EqualsAndHashCode(exclude = "photos")` on `@OneToMany` side (and mirror on `@ManyToOne` side) — prevents circular `toString` → StackOverflow.

### MapStruct
- Update mappers always use `NullValuePropertyMappingStrategy.IGNORE` — PATCH semantics, omitted fields don't wipe DB values.

### Spring Security
- `FilterRegistrationBean.setEnabled(false)` for every `OncePerRequestFilter` also in the Security chain — prevents double-execution.
- Coarse `SecurityConfig` (`permitAll` on GET patterns) + fine-grained `@CurrentUser` resolver checks for auth enforcement. Don't rely solely on SecurityConfig for method-level auth.

### Hibernate / JPA
- Lazy associations accessed in controller (after transaction closes) → `LazyInitializationException`. Fix: use `JOIN FETCH` in a custom repository query so associations are loaded before the session closes. Do NOT rely on `@Transactional` alone when the mapper runs outside the service method.
- Haversine formula for distance — do NOT add GeoTools (100 MB+, not needed for MVP).
- Category tree: `findAll()` + build tree in Java (first-level cache prevents N+1; safe for < 1000 rows).
- `@ManyToOne` fields require loading the entity via repo, never set from raw UUID:  
  `chat.setListing(listingRepository.findById(id).orElseThrow())`

### WebSocket / STOMP
- Two-step Principal resolution (both required together):
  1. `JwtHandshakeInterceptor` (HTTP handshake) — extracts userId → WebSocket session attributes
  2. `ChannelInterceptor` on CONNECT — reads userId → wraps in `StompPrincipal` → sets on `MessageHeaders`
- Keep `getOrCreateChat()` (returns entity) and `buildSummary()` (returns DTO) separate — reusable from both WebSocket and REST.

### Exception Handling
- `IllegalArgumentException` → 400 in `GlobalExceptionHandler` for all business rule violations (duplicate review, self-review, etc.).

---

## Deployment Checklist

**Railway (first deploy)**
- [ ] All env vars set in Railway dashboard
- [ ] FIREBASE_CONFIG set as env var (JSON contents, not file path)
- [ ] ALLOWED_ORIGINS includes prod origin
- [ ] Flyway migrations pass against prod DB

**App Store**
- [ ] `API_BASE_URL` → prod Railway URL
- [ ] Prod `google-services.json` / `GoogleService-Info.plist`
- [ ] App icon + splash screen configured
- [ ] Privacy policy URL live
- [ ] `flutter build appbundle --release` passes

---

## Services

| Service | Purpose |
|---|---|
| Firebase | Auth (Google + Email) + FCM — download service account JSON |
| Supabase | PostgreSQL + PostGIS (`CREATE EXTENSION postgis;`) |
| Cloudinary | Media upload |
| Railway | Hosting — connect GitHub repo, add PostgreSQL plugin |