# LocalPro — Claude Code Master Guide

> This file is the starting point for every Claude Code session.
> Reference it at the start of each session: "Read CLAUDE.md first, then..."

---

## Project Overview

**LocalPro** is a mobile marketplace for local services (Airbnb-style).
Providers list services visible on a map. Clients search by location, category, and radius, then chat with providers.

Two repos:
- `localPro-backend` — Java 21 + Spring Boot 3.3
- `localPro-mobile` — Flutter 3 + Dart

---

## How to Work with Claude Code Effectively

### The golden rule
Always give Claude Code **context + task**, not just the task.

Bad prompt:
```
Create a user service
```

Good prompt:
```
In the LocalPro Spring Boot project (com.localpro), create UserService in the user package.
It should use UserRepository (already exists, extends JpaRepository<User, UUID>).
Methods needed: findOrCreate(FirebaseToken), updateProfile(UUID, UpdateProfileRequest), getById(UUID).
User entity has fields: id, firebaseUid, email, name, role (enum: CLIENT/PROVIDER/BOTH), rating.
Follow the pattern used in existing ListingService.
```

### Session startup prompt
Use this at the start of every Claude Code session:

```
I'm building LocalPro — a mobile service marketplace (Airbnb-style).
Backend: Java 21 + Spring Boot 3.3, package com.localpro.
Frontend: Flutter 3 + Riverpod 2.

Current state: [describe what's done]
Today's task: [describe what to build]

Rules:
- Use Lombok @Data @Builder on all entities
- Use MapStruct for DTO mapping
- Write Testcontainers integration tests for all controllers
- Follow RESTful naming conventions
- All IDs are UUID
- Use TIMESTAMPTZ for all timestamps
```

---

## Phase Checklist

### Backend phases
- [ ] Phase 0 — Project scaffold + Docker Compose + CI
- [ ] Phase 1 — Auth filter + User CRUD (with tests)
- [ ] Phase 2 — Service Listings CRUD (with tests)
- [ ] Phase 3 — PostGIS geo search (with tests)
- [ ] Phase 4 — Media upload (Cloudinary)
- [ ] Phase 5 — WebSocket chat + FCM notifications
- [ ] Phase 6 — Reviews + Payment stub
- [ ] Phase 7 — Docker + Railway deploy config

### Frontend phases (start after Backend Phase 1 is deployed)
- [ ] Phase 0 — Flutter scaffold + GoRouter + Dio client
- [ ] Phase 1 — Firebase Auth screens
- [ ] Phase 2 — Map with markers + nearby search
- [ ] Phase 3 — ServiceCard + Listing detail
- [ ] Phase 4 — Chat (WebSocket + FCM)
- [ ] Phase 5 — Provider dashboard + CreateListing
- [ ] Phase 6 — Profile + polish + app icon

---

## Recommended Workflow Per Feature

```
1. Write Flyway migration (if new DB table needed)
2. Prompt Claude Code → entity + repository
3. Prompt Claude Code → service + tests
4. Prompt Claude Code → controller + integration test
5. Run tests locally: mvn verify
6. Commit: git commit -m "feat: add [feature]"
7. CI runs on GitHub → green = deploy
```

For Flutter:
```
1. Prompt Claude Code → Repository + API interface (Retrofit)
2. Prompt Claude Code → Riverpod providers
3. Prompt Claude Code → Screen + Widgets
4. Run on emulator: flutter run
5. Run tests: flutter test
6. Commit
```

---

## API Contract (Backend → Frontend)

Base URL (dev): `http://localhost:8080`
Base URL (prod): `https://localpro-api.railway.app`

All responses follow:
```json
{
  "data": { ... },
  "page": { "number": 0, "size": 20, "totalElements": 150, "totalPages": 8 }
}
```

All error responses:
```json
{
  "code": "LISTING_NOT_FOUND",
  "message": "Service listing not found",
  "fieldErrors": [],
  "timestamp": "2025-05-03T10:00:00Z"
}
```

Auth header: `Authorization: Bearer <firebase_id_token>`

---

## Environment Variables Reference

### Backend (.env)
```
# Database (local dev)
DB_URL=jdbc:postgresql://localhost:5432/localpro_dev
DB_USER=localpro
DB_PASS=localpro

# Firebase (download from Firebase Console → Project Settings → Service Accounts)
GOOGLE_APPLICATION_CREDENTIALS=./firebase-service-account.json

# Cloudinary (from cloudinary.com dashboard)
CLOUDINARY_URL=cloudinary://API_KEY:API_SECRET@CLOUD_NAME

# Server
SERVER_PORT=8080
ALLOWED_ORIGINS=http://localhost:3000
```

### Frontend (dart-define)
```
API_BASE_URL=http://10.0.2.2:8080   # Android emulator
API_BASE_URL=http://localhost:8080   # iOS simulator
API_BASE_URL=https://localpro-api.railway.app  # Production
```

---

## Free Services Setup Links

| Service | URL | What to do |
|---|---|---|
| Firebase | console.firebase.google.com | Create project → Enable Auth (Google + Email) → Enable FCM → Download service account JSON |
| Supabase | supabase.com | New project → SQL editor → `CREATE EXTENSION postgis;` |
| Cloudinary | cloudinary.com | Sign up → Dashboard → Copy CLOUDINARY_URL |
| Railway | railway.app | Connect GitHub repo → Add PostgreSQL plugin → Set env vars |
| GitHub | github.com | Create two repos: localPro-backend, localPro-mobile |

---

## Common Claude Code Prompts (Quick Reference)

### Fix a bug
```
In LocalPro backend, the /api/listings/nearby endpoint returns 500 when categoryId is null.
The error is: [paste error].
The relevant code is in GeoService.findNearby() and the @Query in ServiceListingRepository.
Fix the null handling.
```

### Add a field to existing entity
```
In LocalPro backend, add field `city VARCHAR(100)` to service_listings table.
1. Create Flyway migration V4__add_city_to_listings.sql
2. Add city field to ServiceListing entity
3. Add city to CreateListingRequest and UpdateListingRequest DTOs
4. Add city as optional filter to GET /api/listings/nearby
```

### Create a new screen (Flutter)
```
In LocalPro Flutter app, create SearchScreen at route /catalog/search.
It should:
- Have a search text field that calls GET /api/listings/nearby with a text search param
- Show results as a scrollable list of ServiceCard widgets
- Show search history (stored in SharedPreferences, last 5 searches)
- Use NearbyListingsProvider for state
Follow the pattern of CatalogScreen.
```

---

## Git Commit Convention

```
feat: add PostGIS nearby search endpoint
fix: handle null categoryId in geo search
test: add integration tests for ChatController
chore: update dependencies
docs: update API endpoints in CLAUDE.md
```

---

## Testing Strategy

### Backend
- Unit tests: service layer logic (mock repositories)
- Integration tests: controllers with Testcontainers (real PostgreSQL + PostGIS)
- Goal: all endpoints have at least one happy-path integration test

### Flutter
- Widget tests: ServiceCard, ChatBubble, FilterBar
- Integration tests: auth flow, map screen (with mocked API)
- Goal: critical user flows covered

---

## Deployment Checklist

### Before first Railway deploy
- [ ] All environment variables set in Railway dashboard
- [ ] Supabase DB URL added as DATABASE_URL in Railway
- [ ] Firebase service account JSON contents set as FIREBASE_CONFIG env var (not file path)
- [ ] ALLOWED_ORIGINS includes production Flutter app origin
- [ ] Flyway migrations run successfully against production DB

### Before App Store submission
- [ ] API_BASE_URL points to production Railway URL
- [ ] Firebase google-services.json (Android) and GoogleService-Info.plist (iOS) are production versions
- [ ] App icon and splash screen are set (flutter_launcher_icons + flutter_native_splash)
- [ ] Privacy policy URL is real and accessible
- [ ] flutter build appbundle --release passes without errors

Add the following section to CLAUDE.md under Environment Notes:

## Entity Patterns (learned from User entity)

1. @Builder.Default on fields with initializers — always add when using @Builder + default values.
   Without it Lombok builder ignores initializers → null → violates NOT NULL constraints.
   Affects: enums with defaults, BigDecimal.ZERO, Integer defaults, Boolean defaults.

2. DB-owned timestamp columns — use insertable = false, updatable = false on createdAt / updatedAt.
   The DB trigger and DEFAULT now() own these. Hibernate must not write them.
   Apply this pattern to ALL entities that have created_at / updated_at columns.

3. MapStruct updateEntity — always use NullValuePropertyMappingStrategy.IGNORE on update mappers.
   Ensures PATCH semantics: omitted fields in request do not wipe existing DB values.
Update the Environment Notes section in CLAUDE.md to:

## Environment Notes

- JDK version: 21 LTS (manually set, matches Spring Boot 3.3.6 official support)
- Spring Boot 3.3.6
- Lombok version: 1.18.40 (overridden in pom.xml — keep as is, works fine with Java 21)

Add to CLAUDE.md under Entity Patterns, create new section:

## Spring Security Patterns

1. FilterRegistrationBean.setEnabled(false) — always add this for any OncePerRequestFilter
   that is also registered in Spring Security filter chain.
   Without it: Spring Boot auto-registers the filter as servlet filter AND Security registers it
   → filter executes twice per request.
   Pattern:
   @Bean
   public FilterRegistrationBean<FirebaseTokenFilter> firebaseFilterRegistration(FirebaseTokenFilter filter) {
   FilterRegistrationBean<FirebaseTokenFilter> reg = new FilterRegistrationBean<>(filter);
   reg.setEnabled(false);
   return reg;
   }

2. Public GET /api/users/* — when a GET endpoint is public but the same controller
   has authenticated endpoints (like /me), use permitAll() on the pattern
   and enforce auth via @CurrentUser resolver (throws 401 if no principal).
   Do not rely solely on SecurityConfig for method-level auth.

3. @Builder.Default handles role defaults — neve

Add to CLAUDE.md under Entity Patterns:

## Hibernate / JPA Patterns

1. @ToString/@EqualsAndHashCode on bidirectional entities — ALWAYS exclude the "many" side.
   Without this: circular toString → StackOverflowError.
   Without this: broken hashCode in HashSet/HashMap when entity is in a collection.
   Pattern for @OneToMany side:
   @ToString(exclude = "photos")
   @EqualsAndHashCode(exclude = "photos")
   Pattern for @ManyToOne side:
   @ToString(exclude = "listing")
   @EqualsAndHashCode(exclude = "listing")

2. Haversine instead of GeoTools — use Haversine formula for distance calculation.
   GeoTools is a heavy dependency (100MB+), not in pom, not needed for MVP.
   Haversine accuracy: <0.5% error vs geodesic — acceptable for "X km away" display.
   Keep this decision: do NOT add GeoTools dependency.

3. Category tree in-memory — load all categories with findAll() then build tree in Java.
   Hibernate resolves FK IDs from proxies without hitting DB (first-level cache).
   No N+1 queries. Safe pattern for small reference tables (<1000 rows).

4. permitAll on GET /api/listings/** + @CurrentUser on /my endpoint —
   SecurityConfig stays simple with broad permitAll on GET.
   Auth enforced at resolver level for endpoints that need it.
   This is the preferred pattern: coarse SecurityConfig + fine-grained resolver checks.

Add to CLAUDE.md under Spring Security Patterns, create new sections:

## WebSocket / STOMP Patterns

1. Two-step Principal resolution for @SendToUser:
   Step 1 — JwtHandshakeInterceptor (HTTP handshake):
   extracts userId from token → stores in WebSocket session attributes
   Step 2 — ChannelInterceptor on CONNECT (STOMP frame):
   reads userId from session attributes → wraps in StompPrincipal → sets on MessageHeaders
   Without Step 2: @SendToUser and convertAndSendToUser cannot resolve user destination.
   These two steps are ALWAYS needed together.

2. Keep getOrCreateChat and buildSummary separate concerns:
    - getOrCreateChat returns Chat entity
    - buildSummary(Chat, UUID userId) returns DTO
      REST controller calls both in sequence.
      Do not merge them — keeps service reusable from WebSocket and REST contexts.

3. @ManyToOne fields cannot be set from raw UUID —
   always load the entity via repository before setting a relationship.
   Wrong:  chat.setListing(listingId)
   Right:  chat.setListing(listingRepository.findById(listingId).orElseThrow())

## Exception Handling Patterns

1. IllegalArgumentException → 400 Bad Request in GlobalExceptionHandler.
   Business rule violations (duplicate review, self-review, invalid input combinations)
   always throw IllegalArgumentException → always map to 400, never 500.
   Pattern:
   @ExceptionHandler(IllegalArgumentException.class)
   public ResponseEntity<ErrorResponse> handleBusiness(IllegalArgumentException ex) {
   return ResponseEntity.status(400).body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
   }