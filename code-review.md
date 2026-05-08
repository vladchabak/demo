# LocalPro Backend — Code Review
> Reviewed: 2026-05-08 | Reviewer: Claude Sonnet 4.6

---

## Project Overview

LocalPro backend is a **Java 21 + Spring Boot 3.3.6** REST + WebSocket API for a mobile services marketplace.  
Architecture: Firebase Auth → Spring Security filter chain → Service layer → JPA/PostGIS.

Seven domains: **auth, user, listing, chat, review, common, (util — planned)**

---

## Findings by Severity

### CRITICAL

_None found._

---

### HIGH

#### 1. No integration tests despite TestContainers being in pom.xml
- **Location:** `src/test/java/.../LocalProApplicationTests.java` — only `contextLoads()` exists
- **Risk:** No confidence that controllers, DB queries, or migrations work together
- **Fix:** Add `@SpringBootTest` + Testcontainers tests for every controller (per CLAUDE.md plan)

#### 2. `User.rating` and `User.reviewCount` are never updated
- **Location:** `User.java` fields, `ReviewService.java`
- **Risk:** Fields exist in the DB schema and entity but nothing writes to them; they will always be `null`/`0`
- **Fix:** Either aggregate from reviews (like listing does) or remove from the entity until implemented

#### 3. CORS set to `["*"]` with `allowCredentials(true)`
- **Location:** `SecurityConfig.java` — `allowedOriginPatterns("*")`
- **Risk:** Any origin can make credentialed requests; browsers will reject responses because wildcard + credentials is invalid per spec, causing hard-to-debug client errors
- **Fix:** Set explicit origins in `application.yml` (e.g., `ALLOWED_ORIGINS: https://yourapp.com`)

---

### MEDIUM

#### 4. Dev-token hardcoded in multiple places
- **Location:** `FirebaseTokenFilter.java`, `JwtHandshakeInterceptor.java`, `OpenApiConfig.java`
- **Risk:** If Firebase is misconfigured in prod, any request with `Bearer dev-token` gets authenticated as the first DB user
- **Fix:** Guard behind an explicit `app.dev-mode=true` flag in `application.yml`; dev profile only

#### 5. Listing rating/reviewCount missing from `ListingResponse` DTO
- **Location:** `ListingResponse.java` — no `rating` or `reviewCount` fields
- **Risk:** Clients cannot display ratings on listing cards despite columns existing in DB
- **Fix:** Add `BigDecimal rating` and `int reviewCount` to the record

#### 6. `CreateListingRequest` does not validate `lat`/`lng` as required
- **Location:** `CreateListingRequest.java` — lat/lng are `@Nullable` / unannotated
- **Risk:** Listings created without coordinates produce a `null` Geography column; `findNearby` silently excludes them
- **Fix:** Add `@NotNull` if location is always required, or document that location-less listings are intentional

#### 7. Review rating validated only at DB level, not Java level
- **Location:** `CreateReviewRequest.java` — no `@Min(1) @Max(5)` on `rating`
- **Risk:** Invalid ratings reach the service before the DB constraint fires, leaking a 500 instead of a 400
- **Fix:** Add `@Min(1) @Max(5) @NotNull` to `CreateReviewRequest.rating`

#### 8. No rate limiting on message/chat/listing creation
- **Risk:** Any authenticated user can flood the system
- **Fix:** Add `spring-boot-starter-actuator` + Bucket4j or Resilience4j rate limiter on write endpoints

#### 9. Swagger UI publicly accessible in prod
- **Location:** `SecurityConfig.java` — `permitAll` on `/swagger-ui/**` and `/v3/api-docs/**`
- **Risk:** Exposes full API surface to unauthenticated attackers; aids reconnaissance
- **Fix:** Disable in prod profile: `springdoc.swagger-ui.enabled: false` in `application-prod.yml`

---

### LOW

#### 10. Review aggregation is O(N) per review submission
- **Location:** `ReviewService.java` — `findAverageRatingByListingId` called on every create
- **Risk:** Acceptable at MVP scale; degrades on popular listings
- **Fix (future):** Replace with incremental `(oldAvg * oldCount + newRating) / newCount` calculation

#### 11. `JwtHandshakeInterceptor.beforeHandshake` always returns `true`
- **Location:** `JwtHandshakeInterceptor.java` line ~50
- **Note:** Intentional per CLAUDE.md (auth rejected at CONNECT command level); acceptable but should be documented with a comment explaining why

#### 12. Firebase error message leaked in 401 response
- **Location:** `FirebaseTokenFilter.java` — catches `FirebaseAuthException` and passes `e.getMessage()` to response
- **Risk:** Minor info leak; Firebase error strings can contain internal token details
- **Fix:** Respond with a generic "Invalid or expired token" message; log the real error server-side

#### 13. `application-dev.yml` disables Flyway
- **Location:** `application-dev.yml` — `flyway.enabled: false`
- **Risk:** Dev schema drifts from prod migrations silently; migration bugs only surface on prod deploy
- **Fix:** Keep Flyway enabled everywhere; use `spring.flyway.clean-on-validation-error=true` in dev if needed

#### 14. No audit logging for sensitive operations
- **Risk:** No trail for chat creation, listing deletion, review submission
- **Fix:** Add `log.info("User {} sent message to chat {}", senderId, chatId)` at key business events

---

## Security Summary

| Area | Status | Detail |
|------|--------|--------|
| Authentication | Good | Firebase JWT verified; stateless |
| Authorization | Good | `@CurrentUser` + service-level ownership checks |
| SQL injection | Good | JPA parameterized queries; native queries use `@Param` |
| CORS | Bad | Wildcard + credentials — fix before prod |
| Dev-mode backdoor | Risky | Dev token not guarded by profile flag |
| Input validation | Partial | Missing on rating, lat/lng |
| Secrets management | Good | Credentials from env vars |
| WebSocket auth | Acceptable | Two-step principal resolution per spec |

---

## Code Quality Highlights

### What's done well
- **Lazy loading safety** — all multi-step queries use `JOIN FETCH` (`findByIdWithDetails`, `findAllByUserIdWithDetails`). No `LazyInitializationException` risk.
- **MapStruct + `NullValuePropertyMappingStrategy.IGNORE`** — PATCH semantics correctly implemented across User and Listing updates.
- **`@Builder.Default`** used on enum fields in entities — avoids null-from-builder bugs documented in CLAUDE.md.
- **`FilterRegistrationBean.setEnabled(false)`** — prevents double-execution of `FirebaseTokenFilter` (explicit CLAUDE.md pattern).
- **Soft delete** via `ListingStatus.DELETED` — no hard deletes, data recoverable.
- **WebSocket two-step principal** (`JwtHandshakeInterceptor` + `ChannelInterceptor`) — correctly implemented per CLAUDE.md spec.
- **`@ToString`/`@EqualsAndHashCode` exclude** `@OneToMany` and `@ManyToOne` sides — prevents circular `toString` → StackOverflow.

### What needs attention
- **Test coverage is the biggest gap.** TestContainers is already in `pom.xml`; writing controller integration tests is the single highest-leverage improvement.
- **DTO completeness** — `ListingResponse` missing rating fields that exist in DB.
- **Profile separation** — dev-only behaviors should be behind Spring profiles, not hardcoded strings.

---

## Recommendations (Priority Order)

1. **Write integration tests** — at minimum `ListingControllerTest`, `ChatControllerTest`, `ReviewControllerTest` using Testcontainers
2. **Fix CORS** — set `allowedOrigins` from env var, not wildcard
3. **Guard dev-token behind profile** — `@ConditionalOnProperty(name="app.dev-mode", havingValue="true")`
4. **Add missing DTO fields** — rating + reviewCount to `ListingResponse`
5. **Add input validation** — `@NotNull @Min @Max` on `CreateReviewRequest.rating`
6. **Implement User.rating aggregation** or remove the field
7. **Disable Swagger in prod** — `springdoc.swagger-ui.enabled: false` in prod profile
8. **Add category caching** — `@Cacheable("categories")` on `CategoryService.getTopLevelCategories`; invalidate on write
9. **Add rate limiting** — Bucket4j or Resilience4j on message + listing create endpoints
10. **Enable Flyway in dev profile** — prevent migration drift

---

## File-by-File Notes

| File | Issue | Severity |
|------|-------|----------|
| `SecurityConfig.java` | CORS wildcard + credentials | HIGH |
| `FirebaseTokenFilter.java` | Dev-token not profile-guarded; error message leaked | MEDIUM |
| `JwtHandshakeInterceptor.java` | Always returns true (undocumented); dev-token exposed | MEDIUM/LOW |
| `ListingResponse.java` | Missing `rating`, `reviewCount` fields | MEDIUM |
| `CreateListingRequest.java` | lat/lng not validated as required | MEDIUM |
| `CreateReviewRequest.java` | `rating` missing `@Min(1) @Max(5)` | MEDIUM |
| `ReviewService.java` | O(N) aggregation; User rating not updated | MEDIUM/HIGH |
| `application-dev.yml` | Flyway disabled → schema drift | LOW |
| `application-prod.yml` | Swagger not disabled | MEDIUM |
| `LocalProApplicationTests.java` | Only `contextLoads()` | HIGH |
| `ChatService.java` | `entityManager.flush()+refresh()` pattern is correct; keep | — |
| `ServiceListingRepository.java` | Native query with cast `::uuid` is correct PostGIS pattern | — |
| `CategoryService.java` | All-in-memory tree build; safe for MVP | LOW |
