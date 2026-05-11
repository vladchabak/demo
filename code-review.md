# LocalPro Backend — Code Review
> Reviewed: 2026-05-11 | Reviewer: Claude Sonnet 4.6

---

## Project State

Java 21 · Spring Boot 3.3.6 · PostGIS · Firebase Auth/FCM · Cloudinary · Railway  
**Domains:** auth, user, listing (+ review + category), chat, booking, notification, common  
**Migrations:** V1 users/categories → V2 listings → V3 chat/reviews → V4 bookings → V6 verification → V7 spatial index + optimistic locking

The core architecture is sound. Virtual threads, incremental rating, atomic view count, optimistic locking, and the GiST index are all correctly implemented. The main remaining risks are in the schema, production logging, and a few concurrency edge cases.

---

## What's Well-Implemented

| Area | Detail |
|---|---|
| Virtual threads | `spring.threads.virtual.enabled: true`; all I/O-bound operations benefit automatically |
| `@EnableAsync` / `@EnableScheduling` / `@EnableCaching` | All present in `LocalProApplication` |
| Incremental rating | `ReviewService.create()` uses entity-stored `rating`+`reviewCount`; zero aggregate scans |
| Atomic view count | `@Modifying incrementViewCount()` — single `UPDATE ... SET view_count = view_count + 1`; no lost updates |
| Optimistic locking | `@Version` on `ServiceListing` + `User`; `OptimisticLockingFailureException` → 409 in handler |
| GiST spatial index | V7 migration; `ST_DWithin` on `geography` is index-backed |
| JOIN FETCH discipline | Every multi-step query (`findByIdWithDetails`, `findAllByUserIdWithDetails`, etc.) uses explicit `JOIN FETCH`; no `LazyInitializationException` risk |
| CORS | `allowedOriginPatterns` reads `ALLOWED_ORIGINS` env var; no hardcoded wildcard |
| Dev token guard | `app.dev-mode=true` flag; both `FirebaseTokenFilter` and `JwtHandshakeInterceptor` respect it |
| Input validation | `@Min(1) @Max(5) @NotNull` on `CreateReviewRequest.rating`; `@NotNull` on lat/lng; `@Future` on `CreateBookingRequest.scheduledAt` |
| Soft delete | `ListingStatus.DELETED`; data is recoverable |
| WebSocket two-step auth | `JwtHandshakeInterceptor` + `ChannelInterceptor`; principal resolved correctly per STOMP spec |
| `Message.flush()+refresh()` | Explicit `entityManager.flush()` + `refresh()` correctly reads DB-generated `created_at` back into the entity before returning the response |
| `open-in-view: false` | No Open Session In View anti-pattern; all lazy loading is explicit |
| MapStruct PATCH | `NullValuePropertyMappingStrategy.IGNORE` on User + Listing mappers |
| `@Builder.Default` | Enum defaults on entities prevent null-from-builder bugs |
| Test suite | 77 tests (35+ unit + integration) across all major domains; Testcontainers PostGIS 16-3.4 |

---

## Findings

---

### HIGH

---

#### 1. `TRACE` Hibernate parameter logging in production — PII leak

**Location:** `application-prod.yml` lines 11–12, `application.yml` lines 55–56

```yaml
# BOTH files contain:
org.hibernate.SQL: DEBUG
org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

`BasicBinder: TRACE` logs the value of every JDBC bind parameter. In production this means every user's email address, FCM token, message content, rating, and Firebase UID is written to logs in plain text on every query. For a chat + marketplace app this is a severe PII exposure.

**Fix — `application-prod.yml`:**
```yaml
logging:
  level:
    com.localpro: INFO
    org.hibernate.SQL: WARN
    org.hibernate.type.descriptor.sql.BasicBinder: WARN
    org.springframework.web: WARN
    org.springframework.security: WARN
```

---

#### 2. Duplicate GiST index on `service_listings.location`

**Location:** `V2__listings_schema.sql` line 16, `V7__add_spatial_index.sql` line 2

V2 creates:
```sql
CREATE INDEX idx_listings_location ON service_listings USING GIST (location);
```
V7 creates:
```sql
CREATE INDEX IF NOT EXISTS idx_service_listings_location ON service_listings USING GIST(location);
```

Two GiST indexes on the same column. PostgreSQL's query planner uses only one; both must be maintained on every INSERT and UPDATE. Every new listing write pays the cost twice for no benefit.

**Fix — add to V7 or a new migration:**
```sql
DROP INDEX IF EXISTS idx_listings_location;
-- idx_service_listings_location already covers everything
```

---

#### 3. `scheduled_at TIMESTAMP` — timezone data loss in bookings

**Location:** `V4__bookings_schema.sql` line 6, `V6__listing_verification.sql` line 3

```sql
scheduled_at TIMESTAMP NOT NULL   -- no timezone
verified_at  TIMESTAMP            -- no timezone
```

Every other timestamp column in the schema uses `TIMESTAMPTZ`. For a marketplace where providers and clients may be in different timezones, `TIMESTAMP` stores local time with no TZ info. The stored value is ambiguous — does `2026-05-15 14:00` mean UTC, the server's timezone, or the user's timezone? This will produce incorrect booking times for users across timezone boundaries.

**Fix — migration to add:**
```sql
ALTER TABLE bookings   ALTER COLUMN scheduled_at TYPE TIMESTAMPTZ;
ALTER TABLE service_listings ALTER COLUMN verified_at  TYPE TIMESTAMPTZ;
```

Update `Booking.scheduledAt` and `ServiceListing.verifiedAt` from `LocalDateTime` to `Instant` (or `OffsetDateTime`) on the Java side.

---

#### 4. UNIQUE constraint on `chats` doesn't prevent duplicate null-listing chats

**Location:** `V3__chat_and_reviews.sql` line 11

```sql
UNIQUE(client_id, provider_id, listing_id)
```

In standard PostgreSQL, `NULL != NULL` for UNIQUE constraint evaluation. Two rows with `(client_1, provider_1, NULL)` are **not** considered duplicates — both are admitted. So when `StartChatRequest.listingId` is null (chat without a linked listing), the UNIQUE constraint provides no protection and concurrent calls to `ChatService.getOrCreateChat()` with the same client/provider can create duplicate chat records.

**Fix (PostgreSQL 15+):**
```sql
ALTER TABLE chats DROP CONSTRAINT chats_client_id_provider_id_listing_id_key;
ALTER TABLE chats ADD CONSTRAINT chats_unique_participants
    UNIQUE NULLS NOT DISTINCT (client_id, provider_id, listing_id);
```

**Fix (PostgreSQL < 15) — partial index:**
```sql
CREATE UNIQUE INDEX chats_no_listing_unique
    ON chats (client_id, provider_id)
    WHERE listing_id IS NULL;
```

---

#### 5. Offline users never receive push notifications for new messages

**Location:** `ChatService.java` lines 136–144 — `notificationService` is not injected

`ChatService.sendMessage()` broadcasts via WebSocket only:
```java
messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages", response);
```

`NotificationService.sendMessageNotification()` exists and is `@Async`-ready, but `ChatService` has no reference to it. If the recipient is offline or not connected via WebSocket, the in-memory broker drops the message silently. The message is persisted to DB and visible on reconnect, but no FCM push is sent.

**Fix:**
```java
// ChatService — inject NotificationService
@Autowired(required = false)
private NotificationService notificationService;

// In sendMessage(), after messagingTemplate.convertAndSendToUser(...)
User recipient = userRepository.findById(recipientId).orElse(null);
if (notificationService != null && recipient != null
        && recipient.getFcmToken() != null && !recipient.getFcmToken().isBlank()) {
    User senderUser = loadUser(senderId);
    notificationService.sendMessageNotification(
            recipient.getFcmToken(), chatId, senderUser.getName());
}
```

---

### MEDIUM

---

#### 6. `findOrCreateByFirebaseToken` + `getOrCreateChat` — race condition on startup / concurrent requests

**Location:** `UserService.java:25`, `ChatService.java:45`

Both use the check-then-act pattern without any lock:

```java
// UserService
return userRepository.findByFirebaseUid(uid).orElseGet(() -> {
    return userRepository.save(User.builder()...build());  // <-- two threads can both reach here
});

// ChatService
chatRepository.findByClientIdAndProviderIdAndListingId(...)
    .orElseGet(() -> {
        return chatRepository.save(Chat.builder()...build());  // <-- same race
    });
```

Under concurrent traffic (e.g., first-login burst, double-tap on "Start Chat"), both threads pass the initial `find`, both enter `orElseGet`, and the second `save` throws `DataIntegrityViolationException` (UNIQUE on `firebase_uid` / chat). The global handler returns 409, which the client sees as an error on a valid operation.

**Fix — catch and retry in `UserService`:**
```java
public User findOrCreateByFirebaseToken(String uid, String email, String name) {
    return userRepository.findByFirebaseUid(uid).orElseGet(() -> {
        try {
            return userRepository.save(User.builder()
                    .firebaseUid(uid).email(email).name(name).build());
        } catch (DataIntegrityViolationException e) {
            // Another thread created it between our find and save — just return it
            return userRepository.findByFirebaseUid(uid)
                    .orElseThrow(() -> new IllegalStateException("User vanished after race", e));
        }
    });
}
```

Apply the same catch-and-retry pattern in `ChatService.getOrCreateChat()`.

---

#### 7. `findNearby` fires two independent PostGIS scans per page

**Location:** `ServiceListingRepository.java:39` + `ServiceListingRepository.java:46`, `ListingService.java:153–154`

```java
List<ServiceListing> listings = listingRepository.findNearby(lat, lng, radiusMeters, categoryId, size, offset);
long total   = listingRepository.countNearby(lat, lng, radiusMeters, categoryId);
```

Both query the same `ST_DWithin` predicate. Each hits the GiST index independently — double the planning and I/O cost per request.

**Fix — merge into one query using a window function:**
```sql
SELECT sl.*, COUNT(*) OVER () AS total_count,
       ST_Distance(sl.location, ST_MakePoint(:lng, :lat)::geography) AS distance_meters
FROM service_listings sl
WHERE ST_DWithin(sl.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
  AND (:categoryId IS NULL OR sl.category_id = CAST(:categoryId AS uuid))
  AND sl.status = 'ACTIVE'
  AND sl.is_visible_on_map = true
ORDER BY distance_meters
LIMIT :limit OFFSET :offset
```

This also eliminates finding #8 (haversine redundancy) — `distance_meters` comes from the DB.

---

#### 8. Haversine distance recalculated in Java — redundant, diverges from DB sort

**Location:** `ListingService.java:165–180` — `toNearbyResponse()` calls `haversineMeters()`

`findNearby` sorts by `ST_Distance(geography)` in the DB. That result is discarded; `toNearbyResponse()` re-computes the same distance in Java:

```java
double dist = listing.getLocation() != null
    ? haversineMeters(listing.getLocation().getY(), listing.getLocation().getX(), queryLat, queryLng)
    : 0.0;
```

Haversine (spherical earth) vs PostGIS `ST_Distance` on `geography` (geodesic) produce slightly different values. The sort order in the response doesn't match the displayed distance label for edge cases near the radius boundary.

**Fix:** Capture `distance_meters` from the native query SELECT (see #7) and pass it through to `toNearbyResponse()`. Remove `haversineMeters()`.

---

#### 9. Full-table scan on `search` — LIKE with leading wildcard

**Location:** `ServiceListingRepository.java:68–69`

```sql
LOWER(sl.title) LIKE LOWER(CONCAT('%', :query, '%'))
OR LOWER(sl.description) LIKE LOWER(CONCAT('%', :query, '%'))
```

A leading-wildcard `LIKE '%text%'` cannot use a B-tree index. Every search requires a full sequential scan of all active, visible listings filtered row-by-row. At small scale this is acceptable; at thousands of listings it becomes the bottleneck.

**Fix — PostgreSQL full-text search:**
```sql
-- Migration: add tsvector column + GIN index
ALTER TABLE service_listings ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''))
    ) STORED;
CREATE INDEX idx_listings_search ON service_listings USING GIN(search_vector);

-- Query:
AND (:query IS NULL OR sl.search_vector @@ plainto_tsquery('english', :query))
```

---

#### 10. `HikariCP maxPoolSize=3` in production is too small

**Location:** `application-prod.yml` line 8

```yaml
hikari:
  maximum-pool-size: 3
```

With virtual threads, thousands of requests can run concurrently. All of them share 3 DB connections. Under modest traffic every request that touches the DB queues behind 2 others. The `connection-timeout` of 30 s means a slow connection takes 30 s to fail, cascading to HTTP timeouts.

Supabase free tier allows up to 20 connections. Set:
```yaml
hikari:
  maximum-pool-size: 10
  minimum-idle: 2
  connection-timeout: 20000
  keepalive-time: 30000       # prevent Railway/PgBouncer idle disconnect
  leak-detection-threshold: 60000
```

---

#### 11. `@Transactional` on `MessageRepository.markAllAsRead` — wrong layer

**Location:** `MessageRepository.java:19`

```java
@Transactional        // <-- on the repository interface
@Modifying
@Query("UPDATE Message m SET m.isRead = true WHERE ...")
void markAllAsRead(...);
```

Spring Data recommends `@Transactional` on service methods, not repository interfaces. `@Modifying` without `clearAutomatically = true` leaves first-level cache stale after the bulk UPDATE — any `Message` entity already loaded in the same session will still have `isRead = false` in memory even after the UPDATE. If those stale entities are later saved, they would write `isRead = false` back to the DB.

In `ChatService.getMessages()` the mark-read fires before the page fetch, so in practice the stale cache contains no `Message` objects yet — the bug doesn't trigger here. But the pattern is fragile.

**Fix:**
```java
// MessageRepository — remove @Transactional, add clearAutomatically
@Modifying(clearAutomatically = true)
@Query("UPDATE Message m SET m.isRead = true WHERE m.chat.id = :chatId AND m.sender.id != :userId")
void markAllAsRead(@Param("chatId") UUID chatId, @Param("userId") UUID userId);
```

Move `@Transactional` to `ChatService.markRead()` (it's already on `ChatService` at class level, so this is already correct — just remove the repository annotation).

---

#### 12. `BookingService.create()` calls external services inside a transaction

**Location:** `BookingService.java:52–59`

```java
@Transactional
public BookingResponse create(User customer, CreateBookingRequest req) {
    // ...
    paymentService.processPayment(req.paymentType(), amount);  // external call inside TX
    CalendarLinks links = calendarService.generateLinks(...);   // external call inside TX
    Booking booking = Booking.builder()...build();
    bookingRepository.save(booking);
}
```

Both calls are mocks today and are fast. When real implementations replace them (payment gateway, Calendly API), any network timeout or error will roll back the entire transaction — including the `Booking` that was already saved. Worse: if a real payment succeeds but the DB save fails, the charge occurs with no booking record.

**Fix — process payment before the transaction, or use a saga/outbox pattern:**
```java
// Option A: move payment outside transaction
public BookingResponse create(User customer, CreateBookingRequest req) {
    // 1. Validate (read-only, no transaction needed)
    ServiceListing listing = listingRepository.findByIdWithDetails(req.listingId())
        .orElseThrow(...);
    // 2. Call external service outside TX scope
    paymentService.processPayment(req.paymentType(), amount);
    // 3. Persist booking in its own transaction
    return saveBooking(customer, listing, req, paymentStatus, links);
}

@Transactional
private BookingResponse saveBooking(...) { ... }
```

---

### LOW

---

#### 13. `Message.createdAt` flush+refresh — consider `@Generated`

**Location:** `ChatService.java:129–130`

```java
entityManager.flush();
entityManager.refresh(saved);
```

This is a valid workaround to read the DB-default `created_at` back after INSERT. The cleaner declarative approach is to annotate the field:

```java
// Message.java
@Column(name = "created_at", insertable = false, updatable = false)
@org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
private Instant createdAt;
```

With `@Generated`, Hibernate automatically re-selects after INSERT without requiring manual flush/refresh in the service. Eliminates two extra operations per message send.

---

#### 14. `BookingRepository` uses INNER `JOIN FETCH` — excludes orphaned bookings

**Location:** `BookingRepository.java:14–20`

```jpql
SELECT b FROM Booking b
JOIN FETCH b.listing   -- INNER, not LEFT
JOIN FETCH b.customer
JOIN FETCH b.provider
```

The schema has `ON DELETE CASCADE` on `listing_id`, so a deleted listing deletes its bookings — no orphan risk today. But `LEFT JOIN FETCH` would be more defensive and future-proof if cascade behavior ever changes.

---

#### 15. `ReviewRepository` aggregate methods are dead code paths

**Location:** `ReviewRepository.java:18–27`

```java
Optional<Double> findAverageRatingByListingId(UUID listingId);
long countByListingId(UUID listingId);
Optional<Double> findAverageRatingByProviderId(UUID providerId);
long countByProviderId(UUID providerId);
```

`ReviewService.create()` no longer calls these (incremental formula replaced them). They remain for a potential `RatingReconciliationJob`. Document this intent or remove them until the job is written.

---

#### 16. Optimistic lock conflict returns 409 with no retry hint

**Location:** `GlobalExceptionHandler.java:103–112`

When a concurrent review submission causes a version conflict on `ServiceListing` or `User`, the handler returns 409 with `"please retry"` but no `Retry-After` header. Mobile clients have no signal for how long to wait.

**Fix:** Add `Retry-After: 1` to the 409 response for `OptimisticLockingFailureException`.

---

## Query Analysis

| Repository | Method | Type | Notes |
|---|---|---|---|
| `ServiceListingRepository` | `findNearby` | Native SQL | Correct `ST_DWithin` on `geography`; GiST-indexed; but double-scan with `countNearby` |
| `ServiceListingRepository` | `countNearby` | Native SQL | Redundant — merge into `findNearby` with `COUNT(*) OVER()` |
| `ServiceListingRepository` | `search` | JPQL | LIKE with leading wildcard — full table scan; CASE WHEN sort is safe (parameterized) |
| `ServiceListingRepository` | `incrementViewCount` | JPQL `@Modifying` | Atomic; correct; no `@Transactional` needed (called from `@Transactional` service) |
| `ServiceListingRepository` | `findByIdWithDetails` | JPQL | `LEFT JOIN FETCH` photos, category, provider — single query, no N+1 |
| `ChatRepository` | `findAllByUserIdWithDetails` | JPQL | Three `LEFT JOIN FETCH` on `@ManyToOne` — safe, no Cartesian product |
| `ChatRepository` | `findByClientIdAndProviderIdAndListingId` | Derived | Null `listingId` generates `IS NULL` in Spring Data — correct, but UNIQUE constraint doesn't protect nulls (see #4) |
| `MessageRepository` | `markAllAsRead` | JPQL `@Modifying` | Stale cache risk — add `clearAutomatically = true` |
| `MessageRepository` | `findByChatIdOrderByCreatedAtDesc` | Derived | Correct pagination pattern |
| `ReviewRepository` | `findAverageRatingByProviderId` | JPQL | Traversal join `Review → listing → provider` — Hibernate generates implicit JOIN; unused by service |
| `BookingRepository` | `findAllByUserIdWithDetails` | JPQL | INNER `JOIN FETCH` — safe given CASCADE, but `LEFT JOIN FETCH` is more defensive |

---

## Multithreading & Concurrency

| Operation | Risk | Protection | Gap |
|---|---|---|---|
| `UserService.findOrCreateByFirebaseToken` | Race: two threads both miss find, both save | DB UNIQUE → 409 | First-login burst returns error; needs catch-and-retry |
| `ChatService.getOrCreateChat` | Race: same as above + null listing_id UNIQUE bypass | DB UNIQUE (only for non-null listing) | Null-listing chats can duplicate; needs catch-and-retry + schema fix |
| `ReviewService.create` — listing aggregate | Lost-update: concurrent reviews on same listing | `@Version` on `ServiceListing` → 409 | Correct; client must retry on 409 |
| `ReviewService.create` — provider aggregate | Lost-update: concurrent reviews update same User | `@Version` on `User` → 409 | Correct; but provider `@Version` increment also affects profile update concurrency |
| `ServiceListingRepository.incrementViewCount` | Concurrent increments | Atomic `UPDATE ... + 1` at DB level | None needed; already correct |
| `ChatService.sendMessage` | Concurrent sends to same chat | No lock; `lastMessage` overwrite is benign | `lastMessageAt` could be set to an older value if threads interleave — negligible |
| `NotificationService.sendMessageNotification` | Async fire-and-forget | `@Async` on virtual thread | No retry; no fallback; never called from `ChatService` (see #5) |
| `BookingService.create` | External service in TX | None | Payment/calendar outside transaction scope risk (see #12) |

**Thread model:** Virtual threads (`spring.threads.virtual.enabled: true`) are active. Tomcat and `@Async` both use virtual threads. No manual `Executors.newFixedThreadPool` instances. HikariCP connection acquisition will park (not block) virtual threads — correct behavior.

**`@Async` configuration:** `@EnableAsync` is present. Spring Boot 3.x with virtual threads uses a virtual-thread executor by default for `@Async`. No explicit executor configuration needed.

---

## Schema & Migration Analysis

| File | Finding | Severity |
|---|---|---|
| `V2__listings_schema.sql:16` | Creates `idx_listings_location` GiST on `location` | — |
| `V7__add_spatial_index.sql:2` | Creates second `idx_service_listings_location` GiST on same column | HIGH — drop one |
| `V4__bookings_schema.sql:6` | `scheduled_at TIMESTAMP` — no timezone | HIGH — change to `TIMESTAMPTZ` |
| `V6__listing_verification.sql:3` | `verified_at TIMESTAMP` — no timezone | HIGH — change to `TIMESTAMPTZ` |
| `V3__chat_and_reviews.sql:11` | `UNIQUE(client_id, provider_id, listing_id)` — NULLs not covered | HIGH — add partial index or `NULLS NOT DISTINCT` |
| `V1__init_schema.sql` | `firebase_uid UNIQUE NOT NULL` — UNIQUE protects `findOrCreate` race at DB level | Good |
| `V3__chat_and_reviews.sql` | `UNIQUE(listing_id, client_id)` on reviews — prevents duplicate reviews | Good |
| `V7__add_spatial_index.sql` | `@Version` columns added to both `users` and `service_listings` | Good |
| All migrations | Missing `rating_count` index on `reviews(listing_id)` already covered; `reviews(listing_id)` index present | Good |

---

## Security Summary

| Area | Status | Detail |
|---|---|---|
| Authentication | Good | Firebase JWT verified; stateless session |
| Authorization | Good | `@CurrentUser` + service-level ownership checks |
| SQL injection | Good | All native queries use `@Param`; search `sortBy` now validated via enum |
| CORS | Good | `ALLOWED_ORIGINS` env var; no wildcard |
| Dev-mode backdoor | Good | `app.dev-mode` flag; both filters guarded |
| Input validation | Good | Rating, lat/lng, scheduledAt (`@Future`), content size all validated |
| Booking status | Good | ACTIVE check prevents booking paused/deleted listings |
| Secrets management | Good | All credentials from env vars |
| WebSocket auth | Good | Two-step principal resolution |
| Swagger in prod | Good | Disabled in `application-prod.yml` |
| Firebase error leak | Good | Generic 401 returned; real error server-logged |
| PII in logs | **Bad** | `BasicBinder: TRACE` logs every bind parameter in production — emails, tokens, content |
| Mock payment in prod | Gap | `MockPaymentService` is `@Profile("!prod")`; prod needs real implementation |

---

## Test Coverage

| Domain | Unit | Integration | Gap |
|---|---|---|---|
| Auth | — | `AuthControllerTest` (2) | |
| User | `UserServiceTest` (8) | `UserControllerTest` (7) | |
| Listing | `ListingServiceTest` (9) | `ListingControllerTest` (11) | |
| Category | `CategoryServiceTest` (4) | via `ListingControllerTest` | |
| Review | `ReviewServiceTest` (6) | `ReviewControllerTest` (7) | |
| Chat | `ChatServiceTest` (10) | `ChatControllerTest` (8) | |
| Exception | `GlobalExceptionHandlerTest` (4) | — | |
| Booking | — | — | **No tests at all** |
| Notification | — | — | No tests; never called |

Booking has 4 endpoints, payment/calendar logic, and lifecycle state machines — the highest-risk untested domain.

---

## Recommendations (Priority Order)

### Fixed in this session

1. ~~**Fix production SQL/PII logging**~~ — `BasicBinder: WARN` in `application-prod.yml` ✓
2. ~~**Drop duplicate GiST index**~~ — V8 migration drops `idx_listings_location` ✓
3. ~~**Fix `TIMESTAMP` → `TIMESTAMPTZ`**~~ — V8 migration + `Booking.scheduledAt`, `ServiceListing.verifiedAt` changed to `Instant` ✓
4. ~~**Fix null chat UNIQUE constraint**~~ — V8 migration: two partial indexes replace the broken UNIQUE ✓
5. ~~**Wire `NotificationService` into `ChatService.sendMessage`**~~ — push sent for offline recipients ✓
6. ~~**Raise `maxPoolSize` to 10 in prod**~~ — also added `keepalive-time` and `leak-detection-threshold` ✓
7. ~~**Catch-and-retry on `DataIntegrityViolationException`**~~ — `UserService.findOrCreateByFirebaseToken` and `ChatService.getOrCreateChat` ✓
8. ~~**Add `clearAutomatically = true`** to `MessageRepository.markAllAsRead`; remove `@Transactional` from repo interface~~ ✓
9. ~~**Replace `flush()+refresh()`**~~ — `@Generated(EventType.INSERT)` on `Message.createdAt`; removed `EntityManager` from `ChatService` ✓
10. ~~**Add `Retry-After: 1`**~~ — header on 409 optimistic lock responses ✓
11. ~~**Document** unused `ReviewRepository` aggregate methods~~ — comment added ✓

### Still open

12. **Merge double PostGIS scan** — `COUNT(*) OVER()` + `ST_Distance` in `findNearby` native query; remove `countNearby` and `haversineMeters()` (#7, #8)
13. **Remove leading-wildcard LIKE** — add `tsvector`/GIN column + migration for full-text search (#9)
14. **Move payment/calendar calls outside transaction** in `BookingService.create` — deferred until real payment gateway replaces mocks (#12)
15. **Write booking tests** — `BookingServiceTest` (unit) + `BookingControllerTest` (integration)
16. **Implement `RatingReconciliationJob`** — safety net for rating drift after data migrations

---

## File-by-File Summary

| File | Status | Finding |
|---|---|---|
| `application-prod.yml` | ⚠ HIGH | `BasicBinder: TRACE` — PII in logs; `maxPoolSize: 3` — too small |
| `V2__listings_schema.sql` | ⚠ HIGH | GiST index duplicated by V7 |
| `V4__bookings_schema.sql` | ⚠ HIGH | `scheduled_at TIMESTAMP` — no timezone |
| `V6__listing_verification.sql` | ⚠ HIGH | `verified_at TIMESTAMP` — no timezone |
| `V3__chat_and_reviews.sql` | ⚠ HIGH | UNIQUE constraint permits duplicate null-listing chats |
| `ChatService.java` | ⚠ HIGH | No push notification on `sendMessage`; `NotificationService` not wired |
| `UserService.java` | ⚠ MED | `findOrCreate` race → 409 on concurrent first-login |
| `ChatService.java` | ⚠ MED | `getOrCreateChat` race → 409 (non-null listing); silent dup (null listing) |
| `ServiceListingRepository.java` | ⚠ MED | Double PostGIS scan; leading-wildcard LIKE |
| `ListingService.java` | ⚠ MED | Haversine recalculates distance PostGIS already computed |
| `BookingService.java` | ⚠ MED | External service calls inside `@Transactional` |
| `MessageRepository.java` | ⚠ MED | `@Transactional` on interface; no `clearAutomatically` |
| `ReviewService.java` | ✓ | Incremental formula; `@Version` protected; no aggregate queries |
| `ServiceListingRepository.java` | ✓ | `incrementViewCount` atomic; native PostGIS correct |
| `LocalProApplication.java` | ✓ | `@EnableAsync` + `@EnableScheduling` + `@EnableCaching` |
| `SecurityConfig.java` | ✓ | CORS from env var; Swagger guarded |
| `FirebaseTokenFilter.java` | ✓ | Dev token behind profile; generic 401 |
| `NotificationService.java` | ✓ | `@Async`; null guard for missing Firebase |
| `GlobalExceptionHandler.java` | ✓ | All exceptions mapped; `log.error(..., ex)` on 500s |
| `User.java` / `ServiceListing.java` | ✓ | `@Version`; `@Builder.Default` on enums; correct `@ToString` excludes |
| `BookingService.java` | ✓ | Listing ACTIVE check; `@Future` validated; interface injection |
| `MockPaymentService.java` | ✓ | `@Profile("!prod")` |
| `MockCalendarService.java` | ✓ | `@Profile("!prod")` |
| `CategoryService.java` | ✓ | `@Cacheable`; lazy proxy ID access is safe (Hibernate resolves FK without proxy init) |
