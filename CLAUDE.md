# LocalPro — Claude Code Master Guide

## Project

**LocalPro** — mobile marketplace for local services. Providers list services on a map; clients search by location/category/radius, then chat.

- Backend: Java 21 + Spring Boot 3.3.6, package `com.localpro` · Lombok 1.18.40 (pinned)
- Frontend: Flutter 3 + Riverpod 2
- Services: Firebase Auth/FCM · Supabase PostgreSQL + PostGIS · Cloudinary · Railway hosting

---

## Phase Checklist

**Backend:** Phase 0 Scaffold · 1 Auth+User · 2 Listings CRUD · 3 PostGIS geo · 4 Media · 5 Chat+FCM · 6 Reviews · 7 Deploy  
**Frontend** (after Backend Ph1 deployed): Phase 0 Scaffold · 1 Auth · 2 Map · 3 Listing detail · 4 Chat · 5 Provider dashboard · 6 Polish

---

## Workflow & Conventions

**Backend:** Flyway migration → entity/repo → service/tests → controller/integration test → `mvn verify` → commit  
**Flutter:** Repository+API → Riverpod providers → Screen/Widgets → `flutter run` → `flutter test` → commit  
**Git:** `feat:` `fix:` `test:` `chore:` `docs:`

---

## API Contract

Base URL: `http://localhost:8080` (dev) · `https://localpro-api.railway.app` (prod)  
Auth: `Authorization: Bearer <firebase_id_token>`

```json
{ "data": {}, "page": { "number": 0, "size": 20, "totalElements": 0, "totalPages": 0 } }
{ "code": "LISTING_NOT_FOUND", "message": "...", "fieldErrors": [], "timestamp": "..." }
```

---

## Environment Variables

```
FIREBASE_SERVICE_ACCOUNT_JSON=<json>   ALLOWED_ORIGINS=http://localhost:3000
SPRING_DATASOURCE_URL/USERNAME/PASSWORD   CLOUDINARY_URL=cloudinary://...
SERVER_PORT=8080
# app.dev-mode=true  → set in application-dev.yml and application-test.yml only
#                      enables "Bearer dev-token" auth when Firebase is not configured
```

Frontend dart-define: `API_BASE_URL=http://10.0.2.2:8080` (Android) / `http://localhost:8080` (iOS)

---

## Coding Patterns

### Entity / Lombok
- `@Builder.Default` on any initialized field (enum defaults, `BigDecimal.ZERO`) — Lombok builder ignores initializers without it → null → NOT NULL violation.
- `insertable=false, updatable=false` on `createdAt`/`updatedAt` — DB trigger owns them.
- `@ToString(exclude=...)` + `@EqualsAndHashCode(exclude=...)` on both sides of `@OneToMany`/`@ManyToOne` — prevents circular toString → StackOverflow.

### MapStruct
- Update mappers: always `NullValuePropertyMappingStrategy.IGNORE` — PATCH semantics.

### Spring Security
- `FilterRegistrationBean.setEnabled(false)` for every `OncePerRequestFilter` in the Security chain — prevents double-execution.
- Coarse `SecurityConfig` (`permitAll` on GET patterns) + fine-grained `@CurrentUser` checks. Don't rely solely on SecurityConfig.
- CORS: read from `ALLOWED_ORIGINS` env var — never use wildcard `*` with `allowCredentials(true)`.
- Dev-token backdoor: guarded by `app.dev-mode=true` AND `firebaseApp == null`. Never active in prod.

### Hibernate / JPA
- Lazy associations in controller → `LazyInitializationException`. Fix: `JOIN FETCH` in repo query; don't rely on `@Transactional` alone when mapper runs outside service.
- Haversine for distance — do NOT add GeoTools (100 MB+).
- Category tree: `findAll()` + build in Java (safe for < 1000 rows).
- `@ManyToOne` fields: load entity via repo, never set from raw UUID.

### WebSocket / STOMP
- Two-step Principal resolution (both required):
  1. `JwtHandshakeInterceptor` — HTTP handshake → userId in session attributes
  2. `ChannelInterceptor` on CONNECT — wraps userId in `StompPrincipal`
- `beforeHandshake` always returns `true` — auth rejected at CONNECT level (intentional).
- Keep `getOrCreateChat()` (entity) and `buildSummary()` (DTO) separate.

### Exception Handling
- `IllegalArgumentException` → 400 · `EntityNotFoundException` → 404 · `AccessDeniedException` → 403

---

## Testing

**Backend integration tests** use `@Testcontainers(disabledWithoutDocker = true)` + `@Container PostgreSQLContainer` + `@DynamicPropertySource`. Tests skip gracefully when Docker is not reachable (e.g., Windows without TCP socket); run fully in CI.

```java
// AbstractIntegrationTest base pattern
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
// → postgres container + @DynamicPropertySource wires datasource/flyway
// → app.dev-mode=true in application-test.yml → "Bearer dev-token" auth works
```

**Windows note:** Testcontainers needs Docker accessible from the Windows JVM (TCP on localhost:2375, or `~/.testcontainers.properties` with `docker.host=...`). Run `mvn test` from WSL2 if Docker is only available there.

**Flutter:** widget tests for ServiceCard, ChatBubble, FilterBar; integration tests for auth flow and map screen (mocked API).

---

## Deployment Checklist

**Railway:** `FIREBASE_SERVICE_ACCOUNT_JSON` · `ALLOWED_ORIGINS` with prod origin · all `PG*` vars · Flyway passes · Swagger disabled (`springdoc.swagger-ui.enabled: false` in prod profile)

**App Store:** `API_BASE_URL` → prod Railway URL · prod `google-services.json`/`GoogleService-Info.plist` · app icon + splash · privacy policy URL · `flutter build appbundle --release` passes
