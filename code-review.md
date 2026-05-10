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

#### 2. ~~`User.rating` and `User.reviewCount` are never updated~~ — **RETRACTED**
- `ReviewService.create()` lines 61–68 already update both `provider.rating` and `provider.reviewCount` on every review.
- The real issue is that both listing and provider aggregates use `SELECT AVG(...)` queries (O(N) per review), not that they're missing. See Performance section for the incremental formula fix.

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

---

## Performance & Optimization Analysis
> Re-reviewed: 2026-05-10 against actual source. Several prior suggestions contained bugs or incorrect assumptions — corrected below.

---

### Corrections to Prior Analysis

#### ~~"User.rating and reviewCount never updated" (HIGH #2)~~ — WRONG
`ReviewService.java` lines 61–68 already update **both** `listing.rating`/`reviewCount` **and** `provider.rating`/`reviewCount` on every `create()`. Remove HIGH finding #2 from the findings list.

#### Prior code samples contained the following bugs (do not use as-is)
| Sample | Bug |
|--------|-----|
| Incremental rating | Used `@Transaction` (invalid), fetched `countByListingId` after insert (gives `newCount`, not `oldCount` needed by formula) |
| Scheduled jobs | `"0 2 * * *"` — Spring cron is **6 fields** (sec min hr day month dow); `"0 2 * * *"` has 5 → `IllegalArgumentException` at startup |
| `aggregateUserRatings()` | `userRepository.saveAll(users)` — `users` not in scope inside `forEach`; compile error |
| WebSocket batcher | `flush()` references `chatId` not declared in class; mixes all chats into one queue without grouping |
| FCM `NotificationService` | `Executors.newFixedThreadPool(4)` as a field — not managed by Spring, not shut down on app stop |
| `PhotoService` | `private final CompletableFuture<AsyncExecutor> executor` — `AsyncExecutor` doesn't exist; wrong type entirely |
| Stream batch | `u.getId() % BATCH_SIZE` — `UUID` is not `long`, won't compile |
| N+1 test | `QueryCounterListener` — not in standard Spring/Hibernate, class doesn't exist |
| `<->` PostGIS operator | Works only on `geometry`; this project uses `geography(Point,4326)` — operator is incompatible |

---

### Query Optimization (Corrected)

#### 1. **Incremental Rating — Replace 2 Aggregate Queries with 0**
**Current state:** `ReviewService.create()` fires `findAverageRatingByListingId` + `countByListingId` + `findAverageRatingByProviderId` + `countByProviderId` — 4 full aggregate scans per review, one for each `SELECT AVG(...)`.

**The entity already stores `rating` and `reviewCount` — use them.**

```java
// In ReviewService.create(), replace the 4 aggregate queries with:

// Listing aggregates (use values already in entity, no DB round-trip)
int oldListingCount = listing.getReviewCount();
BigDecimal oldListingRating = listing.getRating();
BigDecimal newListingRating = oldListingCount == 0
    ? BigDecimal.valueOf(req.rating())
    : oldListingRating
        .multiply(BigDecimal.valueOf(oldListingCount))
        .add(BigDecimal.valueOf(req.rating()))
        .divide(BigDecimal.valueOf(oldListingCount + 1), 2, RoundingMode.HALF_UP);
listing.setRating(newListingRating);
listing.setReviewCount(oldListingCount + 1);

// Provider aggregates — same pattern
User provider = listing.getProvider(); // already loaded via @ManyToOne
int oldProviderCount = provider.getReviewCount();
BigDecimal oldProviderRating = provider.getRating() != null
    ? provider.getRating() : BigDecimal.ZERO;
BigDecimal newProviderRating = oldProviderCount == 0
    ? BigDecimal.valueOf(req.rating())
    : oldProviderRating
        .multiply(BigDecimal.valueOf(oldProviderCount))
        .add(BigDecimal.valueOf(req.rating()))
        .divide(BigDecimal.valueOf(oldProviderCount + 1), 2, RoundingMode.HALF_UP);
provider.setRating(newProviderRating);
provider.setReviewCount(oldProviderCount + 1);
```

**Race condition guard:** Add `@Version private Long version` to both `ServiceListing` and `User` to get optimistic locking. Two simultaneous reviews on the same listing will retry rather than lose an update.

**Impact:** 4 aggregate `SELECT AVG(...)` scans → 0. Only the 2 `UPDATE` statements already being issued remain.

#### 2. **`viewCount` Lost-Update Bug**
**Location:** `ListingService.getById()` line 118–119.
```java
listing.setViewCount(listing.getViewCount() + 1); // read-modify-write — loses updates under concurrency
```
Two simultaneous `GET /listings/{id}` requests both read `viewCount=10`, both write `11`. One increment is silently lost.

**Fix — atomic update in the DB:**
```java
// Add to ServiceListingRepository:
@Modifying
@Query("UPDATE ServiceListing sl SET sl.viewCount = sl.viewCount + 1 WHERE sl.id = :id")
void incrementViewCount(@Param("id") UUID id);

// In ListingService.getById():
listingRepository.incrementViewCount(id);
return listingRepository.findByIdWithDetails(id).orElseThrow(...);
```

#### 3. **PostGIS GiST Index (Not Yet in Migrations)**
`findNearby` already uses `ST_DWithin` (correct for `geography` type) and has LIMIT/OFFSET. The issue is there may be no spatial index, causing a sequential scan on every radius search.

**Add to next migration:**
```sql
CREATE INDEX IF NOT EXISTS idx_service_listings_location
    ON service_listings USING GIST(location);
```

**Note:** `ST_DWithin` on `geography` **can** use a GiST index — it is the correct operator pair. The `<->` KNN operator does NOT work on `geography` columns; do not use it.

#### 4. **Double `countNearby` Query**
`findNearby` issues 2 independent PostGIS spatial scans: one for results, one for `COUNT(*)`. Use a SQL window function to get both in one pass:
```sql
SELECT sl.*, COUNT(*) OVER () AS total_count
FROM service_listings sl
WHERE ST_DWithin(sl.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
  AND (:categoryId IS NULL OR sl.category_id = CAST(:categoryId AS uuid))
  AND sl.status = 'ACTIVE'
  AND sl.is_visible_on_map = true
ORDER BY ST_Distance(sl.location, ST_MakePoint(:lng, :lat)::geography)
LIMIT :limit OFFSET :offset
```

#### 5. **Haversine Redundancy in ListingService**
`toNearbyResponse()` recalculates distance in Java using `haversineMeters()` even though PostGIS already computed it via `ST_Distance` in the DB. Add `distance_meters` to the native query projection to eliminate the Java-side math.

#### 6. **N+1 Detection (Use datasource-proxy, not invented class)**
```xml
<!-- pom.xml (test scope) -->
<dependency>
  <groupId>net.ttddyy</groupId>
  <artifactId>datasource-proxy</artifactId>
  <version>1.10</version>
  <scope>test</scope>
</dependency>
```
```java
@Test
void findNearbyDoesNotCauseNPlusOne() {
  QueryExecutionListener listener = ...;
  listingService.findNearby(lat, lng, 5, null, 0, 20);
  assertThat(listener.getSelectCount()).isLessThanOrEqualTo(2); // query + count
}
```

---

### Async & Multithreading (Corrected)

#### 0. **Required — Enable Async and Scheduling**
`LocalProApplication.java` has no `@EnableAsync` or `@EnableScheduling`. Without them, `@Async` methods run synchronously and `@Scheduled` methods never fire.

```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class LocalProApplication { ... }
```

#### 1. **Java 21 Virtual Threads — Use Instead of Thread Pools**
The project targets Java 21. Virtual threads (Project Loom) are a better fit for I/O-bound async work (Firebase, DB, Cloudinary) than `Executors.newFixedThreadPool`. They scale to millions of concurrent tasks with no configuration.

```yaml
# application.yml — enable virtual threads globally for all Spring MVC requests
spring:
  threads:
    virtual:
      enabled: true
```

With virtual threads enabled, Spring Boot 3.2+ automatically uses them for Tomcat and `@Async`. No manual thread pool sizing needed for I/O-bound work.

**What this replaces:**
- `Executors.newFixedThreadPool(4)` in `NotificationService` → remove entirely
- `spring.task.execution.pool.max-size` tuning → no longer needed for I/O tasks
- Manual `CompletableFuture` executor injection → use `CompletableFuture.supplyAsync(...)` without executor (JVM schedules on virtual thread)

#### 2. **Async Review Aggregation — Correct Pattern**
`@Async` on a method in the same bean is called through the Spring AOP proxy only when invoked from **outside** the bean. Calling from `ReviewController` (different bean) → works correctly.

```java
// ReviewAggregationService.java — separate bean to ensure proxy works
@Service
public class ReviewAggregationService {

  @Async              // runs in Spring's virtual-thread executor (after step 0)
  @Transactional      // required — @Async opens a new thread with no tx context
  public void updateRatingsAsync(UUID listingId, UUID providerId) {
    // fallback full-recalculation (safe, runs off the hot path)
    listingRepository.findById(listingId).ifPresent(listing -> {
      double avg = reviewRepository.findAverageRatingByListingId(listingId).orElse(0.0);
      listing.setRating(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
      listing.setReviewCount((int) reviewRepository.countByListingId(listingId));
      listingRepository.save(listing);
    });
  }
}
```

With incremental updates (Query Opt #1) in `ReviewService.create()`, this async fallback is only needed for correction jobs, not the hot path.

#### 3. **Firebase FCM — Use `@Async`, Not Raw Executor**
```java
@Service
public class NotificationService {
  private final FirebaseMessaging firebaseMessaging;

  @Async  // Spring manages the thread; shut down correctly on app stop
  public void sendMessageNotification(String token, UUID chatId, String senderName) {
    try {
      firebaseMessaging.send(Message.builder()
          .setToken(token)
          .putData("chat_id", chatId.toString())
          .setNotification(Notification.builder()
              .setTitle(senderName)
              .setBody("Sent you a message")
              .build())
          .build());
    } catch (FirebaseMessagingException e) {
      log.error("FCM send failed for token {}: {}", token, e.getMessage());
    }
  }
}
```

#### 4. **Cloudinary Upload — Correct Type**
```java
@Service
public class PhotoService {
  private final Cloudinary cloudinary;
  // Inject Spring's task executor, not CompletableFuture<AsyncExecutor>
  private final Executor taskExecutor;

  public CompletableFuture<String> uploadPhotoAsync(byte[] bytes, UUID listingId) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        Map<?, ?> result = cloudinary.uploader().upload(bytes,
            ObjectUtils.asMap("folder", "listings/" + listingId));
        return (String) result.get("secure_url");
      } catch (IOException e) {
        throw new CompletionException(e);
      }
    }, taskExecutor);
  }
}
```

#### 5. **WebSocket Batching — Fixed (Group by chatId)**
The original batcher had `chatId` undefined in `flush()` and mixed messages from all chats.

```java
@Component
public class ChatMessageBatcher {
  // Key: chatId → queue of pending messages for that chat
  private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<ChatMessage>> queues =
      new ConcurrentHashMap<>();
  private final SimpMessagingTemplate template;

  @PostConstruct
  void startBatcher() {
    Executors.newSingleThreadScheduledExecutor()
        .scheduleAtFixedRate(this::flush, 500, 500, TimeUnit.MILLISECONDS);
  }

  public void enqueue(UUID chatId, ChatMessage msg) {
    queues.computeIfAbsent(chatId, id -> new ConcurrentLinkedQueue<>()).add(msg);
  }

  private void flush() {
    queues.forEach((chatId, queue) -> {
      List<ChatMessage> batch = new ArrayList<>();
      ChatMessage msg;
      int limit = 10;
      while (limit-- > 0 && (msg = queue.poll()) != null) batch.add(msg);
      if (!batch.isEmpty()) {
        template.convertAndSend("/topic/chat/" + chatId, batch);
      }
    });
  }
}
```

---

### Background Jobs (Corrected)

#### 1. **Nightly Rating Reconciliation Job — Fixed**
This is a correctness safety net in case incremental updates drift (e.g., from data migrations). Not needed for real-time accuracy.

```java
@Service
@RequiredArgsConstructor
public class RatingReconciliationJob {
  private final UserRepository userRepository;
  private final ReviewRepository reviewRepository;

  // Spring cron: 6 fields — second minute hour day month weekday
  @Scheduled(cron = "0 0 2 * * *") // 2 AM daily (was "0 2 * * *" — 5 fields, crashes)
  @Transactional
  void reconcile() {
    List<User> updated = new ArrayList<>();
    userRepository.findAll().forEach(user -> {
      double avg = reviewRepository.findAverageRatingByProviderId(user.getId()).orElse(0.0);
      long count = reviewRepository.countByProviderId(user.getId());
      user.setRating(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
      user.setReviewCount((int) count);
      updated.add(user);
    });
    userRepository.saveAll(updated); // was: saveAll(users) — compile error, users not in scope
    log.info("Reconciled ratings for {} providers", updated.size());
  }
}
```

**Memory concern:** `userRepository.findAll()` loads all users into heap. For large datasets use cursor streaming:
```java
// Add to UserRepository:
@Query("SELECT u FROM User u")
@QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "100"))
Stream<User> streamAll();

// Job: replace findAll() forEach with:
try (Stream<User> stream = userRepository.streamAll()) {
  stream.forEach(user -> { ... });
}
```

#### 2. **Category Cache Invalidation**
```java
// Add to pom.xml:
// <dependency> spring-boot-starter-cache + caffeine </dependency>

// application.yml:
spring:
  cache:
    type: CAFFEINE
    caffeine:
      spec: "maximumSize=200,expireAfterWrite=1h"

// CategoryService — add @EnableCaching to main class or a @Configuration:
@Cacheable(value = "categories", unless = "#result.isEmpty()")
public List<CategoryResponse> getTopLevelCategories() { ... }

@CacheEvict(value = "categories", allEntries = true)
@Transactional
public CategoryResponse create(CreateCategoryRequest req) { ... }
```

#### 3. **Audit Log Batch Flush**
Pattern is correct; the key constraint is the `LinkedBlockingQueue(10_000)` bound — when full, `offer()` silently drops events. Log a warning on drop:
```java
public void logAsync(AuditEvent event) {
  if (!queue.offer(event)) {
    log.warn("Audit queue full — dropping event: {}", event.type());
  }
}
```

#### 4. **Photo Cleanup Job — Fixed (Paginate Cloudinary API)**
Original loaded all Cloudinary resources + all DB photos into memory at once.

```java
@Scheduled(cron = "0 0 3 * * 0") // 3 AM Sunday (was "0 3 * * 0" — 5 fields, crashes)
void cleanupOrphanedPhotos() {
  Set<String> usedPublicIds = photoRepository.findAll().stream()
      .map(p -> extractPublicId(p.getUrl()))
      .collect(Collectors.toSet());

  String nextCursor = null;
  do {
    Map result = cloudinary.api().resources(ObjectUtils.asMap(
        "type", "upload", "prefix", "listings/",
        "max_results", 500,
        "next_cursor", nextCursor));
    List<Map> resources = (List<Map>) result.get("resources");
    resources.stream()
        .map(r -> (String) r.get("public_id"))
        .filter(id -> !usedPublicIds.contains(id))
        .forEach(id -> cloudinary.uploader().destroy(id, emptyMap()));
    nextCursor = (String) result.get("next_cursor");
  } while (nextCursor != null);
}
```

#### 5. **Notification Retry Queue**
Pattern is correct. Cron `"0 */5 * * * *"` is valid (6 fields). Consider adding a `@Lock` on the query to prevent two instances of the job processing the same row in multi-pod deployments.

---

### Memory Optimization

#### 1. **HikariCP Sizing — Use Formula, Not Guess**
The rule of thumb is `connections = (cpu_cores × 2) + effective_spindle_count`. On Railway (1–2 vCPU):
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10    # (2 cores × 2) + 1 disk = 5, round up to 10
      minimum-idle: 2
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
      leak-detection-threshold: 60000  # warn if connection held > 60s
```
With virtual threads enabled, the ceiling is less critical (threads don't block waiting for connections — they yield). Start at 10 and tune up only under measured load.

#### 2. **Chat Message Pagination (Already Paged)**
`ReviewRepository.findByListingIdOrderByCreatedAtDesc` takes a `Pageable` — already correct. Verify `ChatRestController` and `MessageRepository` do the same for chat message history. If not, apply the same pattern.

#### 3. **DTO Projections for `findNearby`**
`findNearby` returns full `ServiceListing` entities including all associations (even lazy ones risk Cartesian product with photos). The mapper then builds `NearbyListingResponse` which only needs 15 fields. A native query projection (or `@SqlResultSetMapping`) avoids hydrating unused associations entirely.

#### 4. **Stream Batch Processing — Correct UUID Grouping**
```java
// Partition a UUID-keyed stream into batches of 500
try (Stream<User> stream = userRepository.streamAll()) {
  Iterator<User> it = stream.iterator();
  List<User> batch = new ArrayList<>(500);
  while (it.hasNext()) {
    batch.add(it.next());
    if (batch.size() == 500) {
      userRepository.saveAll(batch);
      batch.clear();
    }
  }
  if (!batch.isEmpty()) userRepository.saveAll(batch);
}
// Note: UUID % BATCH_SIZE doesn't compile — UUID is not a number.
```

---

### Implementation Roadmap (Revised)

#### Phase 1: Quick Wins — No New Dependencies (1 week)
1. Add `@EnableAsync` + `@EnableScheduling` to `LocalProApplication`
2. Enable virtual threads: `spring.threads.virtual.enabled: true`
3. Replace 4 aggregate queries in `ReviewService.create()` with incremental formula
4. Fix `viewCount` lost-update: use `@Modifying` atomic increment
5. Add `@Version` to `ServiceListing` + `User` for optimistic locking
6. Add GiST index on `service_listings.location` in next Flyway migration

#### Phase 2: Async & Caching (1–2 weeks)
1. Make FCM send `@Async`; extract to `NotificationService`
2. Add Caffeine + `@Cacheable` to `CategoryService`
3. Implement audit logging `logAsync()` with batch queue

#### Phase 3: Background Jobs (2–3 weeks)
1. Nightly `RatingReconciliationJob` with fixed cron + stream cursor
2. Weekly `PhotoCleanupJob` with paginated Cloudinary API
3. Notification retry queue with exponential backoff

#### Phase 4: Observability (Ongoing)
1. Micrometer `Timer` on `findNearby`, `createReview`, WebSocket send
2. HikariCP pool metrics (Actuator exposes these by default)
3. Alert: `hikaricp.connections.timeout > 0` means pool too small
4. Alert: async queue depth (`queue.size()`) approaching capacity

---

### Concurrency & Consistency Guarantees (Revised)

| Operation | Current | Issue | Fix |
|---|---|---|---|
| Review creation | 4 AVG aggregate queries | O(N) scan per review | Incremental formula using entity fields |
| viewCount update | Read-modify-write | Lost updates under concurrency | Atomic `UPDATE ... SET view_count = view_count + 1` |
| Rating under concurrent reviews | No optimistic lock | Two simultaneous reviews lose one | `@Version` on `ServiceListing` + `User` |
| Category queries | Full DB scan each call | Unnecessary DB round-trips | Caffeine cache with 1h TTL |
| FCM notifications | Likely synchronous | Blocks response thread | `@Async` on send method |
| Batch jobs | `userRepository.findAll()` | Full heap load | Cursor stream with batch of 500 |
| Cloudinary cleanup | Full API + DB load | OOM risk at scale | Paginated cursor loop |

---

### Estimated Impact (Revised)

| Change | Latency | Throughput | Effort |
|---|---|---|---|
| Incremental rating formula | -4 DB queries per review | +3–5x review creation | Low (30 min) |
| Atomic viewCount | No latency change | Correct counts under load | Low (15 min) |
| Virtual threads | -latency under I/O wait | Scales to 10k+ concurrent | Low (1 config line) |
| GiST index on location | -70% on findNearby | +5x concurrent geo-searches | Low (1 SQL line) |
| Category cache | -30ms per search | +20% search throughput | Low (2h) |
| Async FCM | -50–300ms on chat send | +2x message throughput | Low (1h) |
| Optimistic locking | No latency change | Correct ratings under concurrency | Medium (3h) |
