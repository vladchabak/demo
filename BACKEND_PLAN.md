# LocalPro Backend — Claude Code Action Plan

> **Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL 16 + PostGIS · Firebase Auth · Flyway · Testcontainers
> **Repo:** `localPro-backend`
> Work through phases in order. Each phase ends with passing tests before moving on.

---

## Phase 0 — Project Scaffold

### Prompt 1 — Spring Boot project structure
```
Create a Spring Boot 3.3 project called "localpro-backend" with Java 21.

Package root: com.localpro

Modules (sub-packages):
- auth       (Firebase JWT filter, user registration)
- user       (user profiles, roles)
- listing    (service listings CRUD)
- geo        (PostGIS nearby search)
- chat       (WebSocket + REST history)
- media      (Cloudinary upload)
- notification (FCM push via Firebase Admin)
- payment    (stub only for MVP)

Include these dependencies in pom.xml:
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-security
- spring-boot-starter-websocket
- spring-boot-starter-validation
- spring-boot-starter-cache
- postgresql driver
- hibernate-spatial (for PostGIS JTS types)
- flyway-core
- firebase-admin 9.x
- cloudinary-http45
- springdoc-openapi-starter-webmvc-ui
- lombok
- mapstruct
- testcontainers (postgresql + junit5)
- spring-boot-starter-test

Create the base directory structure with empty placeholder classes in each package.
Add application.yml with placeholder values for: DB_URL, DB_USER, DB_PASS, FIREBASE_PROJECT_ID, CLOUDINARY_URL.
```

### Prompt 2 — Docker Compose for local development
```
Create a docker-compose.yml for local development of the LocalPro backend.

Services:
1. postgres:
   - image: postgis/postgis:16-3.4
   - port: 5432
   - database: localpro_dev
   - user: localpro / password: localpro
   - volume: postgres_data
   - healthcheck

2. redis (optional, for future session caching):
   - image: redis:7-alpine
   - port: 6379

Also create a .env.example file with all required environment variables.
```

### Prompt 3 — GitHub Actions CI
```
Create a GitHub Actions workflow file .github/workflows/ci.yml for the LocalPro Spring Boot project.

On push and pull_request to main:
1. Start PostgreSQL + PostGIS service container (postgis/postgis:16-3.4)
2. Set up Java 21
3. Run: mvn verify (includes unit + integration tests with Testcontainers)
4. Upload test reports as artifacts

Use environment variables for DB connection matching application-test.yml.
```

---

## Phase 1 — Auth + Users

### Prompt 4 — Flyway migration V1: core tables
```
Create a Flyway migration V1__init_schema.sql for PostgreSQL 16 + PostGIS.

Tables to create:

users:
  id UUID PRIMARY KEY DEFAULT gen_random_uuid()
  firebase_uid VARCHAR(128) UNIQUE NOT NULL
  email VARCHAR(255) UNIQUE NOT NULL
  name VARCHAR(255) NOT NULL
  avatar_url TEXT
  role VARCHAR(20) NOT NULL DEFAULT 'CLIENT'  -- CLIENT | PROVIDER | BOTH
  bio TEXT
  phone VARCHAR(30)
  rating NUMERIC(3,2) DEFAULT 0
  review_count INT DEFAULT 0
  is_active BOOLEAN DEFAULT true
  created_at TIMESTAMPTZ DEFAULT now()
  updated_at TIMESTAMPTZ DEFAULT now()

categories:
  id UUID PRIMARY KEY DEFAULT gen_random_uuid()
  name VARCHAR(100) NOT NULL
  icon VARCHAR(50)
  parent_id UUID REFERENCES categories(id)
  sort_order INT DEFAULT 0

service_listings:
  id UUID PRIMARY KEY DEFAULT gen_random_uuid()
  provider_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
  category_id UUID NOT NULL REFERENCES categories(id)
  title VARCHAR(255) NOT NULL
  description TEXT
  price NUMERIC(10,2)
  price_type VARCHAR(20) DEFAULT 'FROM'  -- FIXED | HOURLY | FROM
  location GEOGRAPHY(POINT, 4326)
  address TEXT
  city VARCHAR(100)
  status VARCHAR(20) DEFAULT 'ACTIVE'  -- ACTIVE | PAUSED | DELETED
  view_count INT DEFAULT 0
  created_at TIMESTAMPTZ DEFAULT now()
  updated_at TIMESTAMPTZ DEFAULT now()

service_photos:
  id UUID PRIMARY KEY DEFAULT gen_random_uuid()
  listing_id UUID NOT NULL REFERENCES service_listings(id) ON DELETE CASCADE
  url TEXT NOT NULL
  sort_order INT DEFAULT 0

Include: GiST index on service_listings.location, index on service_listings.provider_id,
index on service_listings.category_id, updated_at trigger function for both users and service_listings.
```

### Prompt 5 — Flyway migration V2: chat + reviews
```
Create Flyway migration V2__chat_and_reviews.sql.

Tables:

chats:
  id UUID PRIMARY KEY DEFAULT gen_random_uuid()
  client_id UUID NOT NULL REFERENCES users(id)
  provider_id UUID NOT NULL REFERENCES users(id)
  listing_id UUID REFERENCES service_listings(id)
  last_message TEXT
  last_message_at TIMESTAMPTZ
  created_at TIMESTAMPTZ DEFAULT now()
  UNIQUE(client_id, provider_id, listing_id)

messages:
  id UUID PRIMARY KEY DEFAULT gen_random_uuid()
  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE
  sender_id UUID NOT NULL REFERENCES users(id)
  content TEXT NOT NULL
  is_read BOOLEAN DEFAULT false
  created_at TIMESTAMPTZ DEFAULT now()

reviews:
  id UUID PRIMARY KEY DEFAULT gen_random_uuid()
  listing_id UUID NOT NULL REFERENCES service_listings(id)
  client_id UUID NOT NULL REFERENCES users(id)
  rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5)
  comment TEXT
  created_at TIMESTAMPTZ DEFAULT now()
  UNIQUE(listing_id, client_id)

Add indexes on: messages(chat_id, created_at DESC), messages(sender_id),
chats(client_id), chats(provider_id).

Also add a trigger: after INSERT on reviews, update service_listings.rating
and review_count by computing AVG(rating) and COUNT(*) from reviews for that listing.
```

### Prompt 6 — Firebase Auth filter + SecurityConfig
```
Create Spring Security configuration for the LocalPro project.

Requirements:
- FirebaseTokenFilter: OncePerRequestFilter that reads the Bearer token from Authorization header,
  verifies it with Firebase Admin SDK (FirebaseAuth.getInstance().verifyIdToken()),
  extracts uid + email + name, sets UsernamePasswordAuthenticationToken in SecurityContext.
- FirebaseConfig: @Configuration that initializes FirebaseApp from GOOGLE_APPLICATION_CREDENTIALS
  env variable (path to service account JSON file).
- SecurityConfig: @Configuration with SecurityFilterChain.
  Public endpoints: GET /api/listings/**, GET /api/categories, GET /api/listings/nearby, /swagger-ui/**, /v3/api-docs/**
  All others: authenticated.
- CurrentUser: custom @interface annotation + CurrentUserArgumentResolver to inject authenticated
  User entity into controller methods.
- UserDetailsServiceImpl: loads User entity from DB by firebase_uid, creates it on first login.

Use Spring Security 6 lambda DSL (no deprecated WebSecurityConfigurerAdapter).
```

### Prompt 7 — User entity + AuthController
```
Create the following for the LocalPro auth/user module:

1. User entity (JPA) mapping the users table. Use Lombok @Data, @Builder.
   role field as enum UserRole { CLIENT, PROVIDER, BOTH }.

2. UserRepository extends JpaRepository<User, UUID> with:
   - findByFirebaseUid(String uid): Optional<User>
   - findByEmail(String email): Optional<User>

3. UserService with methods:
   - findOrCreateByFirebaseToken(FirebaseToken token): User
     (creates user on first login with data from Firebase token)
   - updateProfile(UUID userId, UpdateProfileRequest req): User
   - getById(UUID id): User

4. AuthController POST /api/auth/register:
   - Receives Firebase JWT (via filter, user already in SecurityContext)
   - Calls findOrCreateByFirebaseToken
   - Returns UserResponse DTO

5. UserController:
   - GET /api/users/me → returns current user profile
   - PUT /api/users/me → updates profile (name, bio, phone, avatarUrl, role)
   - GET /api/users/{id} → public profile

Include DTOs: UserResponse, UpdateProfileRequest (with Jakarta validation).
Include MapStruct mapper: UserMapper.
Include integration test with @SpringBootTest + Testcontainers for AuthController.
```

---

## Phase 2 — Service Listings

### Prompt 8 — Listings CRUD
```
Create the full service listings module for LocalPro.

1. ServiceListing entity (JPA) mapping service_listings table.
   Use org.locationtech.jts.geom.Point for the location field.
   Include relationship to User (provider) and Category.

2. ServicePhoto entity mapping service_photos.

3. Category entity with self-referential parent/children.

4. ServiceListingRepository with:
   - findByProviderIdAndStatusNot(UUID providerId, ListingStatus status, Pageable p)
   - A @Query using ST_DWithin and ST_Distance for nearby search (see Phase 3)

5. ServiceListingService:
   - create(UUID providerId, CreateListingRequest req): ServiceListing
   - update(UUID providerId, UUID listingId, UpdateListingRequest req): ServiceListing
   - delete(UUID providerId, UUID listingId): void  (soft delete: set status=DELETED)
   - getById(UUID id): ServiceListing
   - getByProvider(UUID providerId, Pageable p): Page<ServiceListing>

6. ListingController:
   - POST /api/listings               → create (provider only)
   - PUT  /api/listings/{id}          → update (owner only)
   - DELETE /api/listings/{id}        → soft delete (owner only)
   - GET /api/listings/{id}           → public detail
   - GET /api/listings/my             → my listings (authenticated)

7. DTOs: CreateListingRequest, UpdateListingRequest, ListingResponse, ListingDetailResponse.
   ListingDetailResponse includes provider info (name, avatar, rating, reviewCount).

8. Integration tests for all endpoints.
```

---

## Phase 3 — Geo Search

### Prompt 9 — PostGIS nearby search
```
Add geo search to the LocalPro listings module.

1. Add a native @Query to ServiceListingRepository:

  @Query(value = """
    SELECT sl.* FROM service_listings sl
    WHERE ST_DWithin(sl.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
    AND (:categoryId IS NULL OR sl.category_id = :categoryId::uuid)
    AND sl.status = 'ACTIVE'
    ORDER BY ST_Distance(sl.location, ST_MakePoint(:lng, :lat)::geography)
    LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
  List<ServiceListing> findNearby(
    @Param("lat") double lat, @Param("lng") double lng,
    @Param("radiusMeters") double radiusMeters,
    @Param("categoryId") String categoryId,
    @Param("limit") int limit, @Param("offset") int offset);

2. GeoService wrapping the query with business logic:
   - Input: NearbySearchRequest { lat, lng, radiusKm (0.5–50), categoryId (nullable), page, size }
   - Output: Page<ListingResponse> with distanceMeters field added to each item

3. GeoController GET /api/listings/nearby:
   - @RequestParam: lat, lng, radiusKm (default 5), categoryId (optional), page (default 0), size (default 20)
   - Returns Page<NearbyListingResponse> (ListingResponse + distanceMeters + bearing)

4. NearbyListingResponse DTO includes all ListingResponse fields plus:
   - distanceMeters: double
   - distanceLabel: String ("350 m" or "1.2 km")

5. Integration test: insert 3 listings at known coordinates, search with radius,
   assert correct listings returned in distance order.
```

---

## Phase 4 — Media Upload

### Prompt 10 — Cloudinary media upload
```
Create the media upload module for LocalPro.

1. CloudinaryConfig: reads CLOUDINARY_URL from env, creates Cloudinary bean.

2. MediaService:
   - uploadListingPhoto(MultipartFile file, UUID listingId): String (returns Cloudinary URL)
   - Validates: max 5MB, only image/jpeg + image/png
   - Uploads to Cloudinary folder "localpro/listings/{listingId}"
   - Saves ServicePhoto record to DB (url, listingId, sort_order = current count + 1)
   - deletePhoto(UUID photoId, UUID requestingUserId): void

3. MediaController:
   - POST /api/listings/{listingId}/photos → upload photo (owner only), returns PhotoResponse
   - DELETE /api/listings/{listingId}/photos/{photoId} → delete (owner only)
   - PUT /api/listings/{listingId}/photos/order → reorder (body: list of photoId in new order)

4. Limit: max 8 photos per listing.
5. Unit tests with mocked Cloudinary SDK.
```

---

## Phase 5 — Chat

### Prompt 11 — WebSocket chat
```
Create the real-time chat module for LocalPro.

1. WebSocketConfig:
   - @EnableWebSocketMessageBroker
   - STOMP endpoint: /ws (with SockJS fallback)
   - Application destination prefix: /app
   - User destination prefix: /user
   - In-memory SimpleBroker on /queue and /topic
   - Add JwtHandshakeInterceptor to authenticate the WebSocket connection

2. ChatService:
   - getOrCreateChat(UUID clientId, UUID providerId, UUID listingId): Chat
   - sendMessage(UUID chatId, UUID senderId, String content): Message
     → saves to DB
     → sends to recipient via SimpMessagingTemplate convertAndSendToUser("/queue/messages")
     → sends FCM push notification if recipient has fcmToken
   - getChats(UUID userId): List<ChatSummaryResponse>
   - getMessages(UUID chatId, UUID userId, Pageable p): Page<MessageResponse>
     → marks messages as read for this user

3. ChatController (@MessageMapping):
   - /app/chat.send → receives SendMessageRequest { chatId, content }
   - Validates sender is participant in the chat

4. ChatRestController:
   - POST /api/chats → start chat { providerId, listingId } (client initiates)
   - GET  /api/chats → list my chats
   - GET  /api/chats/{id}/messages?page=0 → paginated history (cursor by created_at)
   - POST /api/chats/{id}/read → mark messages as read

5. DTOs: ChatSummaryResponse (lastMessage, lastMessageAt, unreadCount, otherParty info),
   MessageResponse, SendMessageRequest.

6. FCM token: add fcm_token VARCHAR column to users table (Flyway V3__fcm_token.sql).
   PUT /api/users/me/fcm-token endpoint to update it from Flutter.
```

---

## Phase 6 — Reviews + Payments Stub

### Prompt 12 — Reviews
```
Create the reviews module for LocalPro.

1. Review entity mapping the reviews table.

2. ReviewService:
   - create(UUID clientId, UUID listingId, CreateReviewRequest req): Review
     → validates: user cannot review own listing, cannot review twice
     → after save: recalculates listing rating and review_count
   - getByListing(UUID listingId, Pageable p): Page<ReviewResponse>

3. ReviewController:
   - POST /api/listings/{listingId}/reviews → create (authenticated client)
   - GET  /api/listings/{listingId}/reviews → public, paginated

4. ReviewResponse DTO: rating, comment, createdAt, author (name + avatarUrl).
```

### Prompt 13 — Payments stub
```
Create a payment stub module for LocalPro MVP.

PaymentController:
  POST /api/payments/intent
  Request body: { listingId: UUID, amount: BigDecimal, currency: String }
  Response: { clientSecret: "stub_secret_mvp", status: "STUB", message: "Real payments coming soon" }
  Returns HTTP 200. Log the request.

This is a placeholder. Real Stripe/YooKassa integration will replace this post-MVP.
Include a TODO comment at the top of the file explaining the future integration plan.
```

---

## Phase 7 — Production Readiness

### Prompt 14 — Exception handling + logging
```
Add global exception handling and structured logging to LocalPro backend.

1. GlobalExceptionHandler (@RestControllerAdvice):
   - EntityNotFoundException → 404 with { code, message, timestamp }
   - AccessDeniedException → 403
   - MethodArgumentNotValidException → 400 with field errors list
   - FirebaseAuthException → 401
   - Generic Exception → 500 (log full stack, return safe message)

2. ErrorResponse DTO: { code: String, message: String, fieldErrors: List, timestamp: Instant }

3. Structured logging with Logback:
   - Console: human-readable in dev (application-dev.yml)
   - Production: JSON format (application-prod.yml) for log aggregation

4. Request logging filter: logs method, path, status, duration in ms for every request.
```

### Prompt 15 — Dockerfile + Railway config
```
Create production deployment configuration for LocalPro Spring Boot backend.

1. Dockerfile (multi-stage):
   Stage 1 (builder): maven:3.9-eclipse-temurin-21, copy pom.xml + src, run mvn package -DskipTests
   Stage 2 (runtime): eclipse-temurin:21-jre-alpine, copy JAR from builder, EXPOSE 8080,
   ENTRYPOINT with JVM flags: -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0

2. .dockerignore: exclude target/, .git/, .idea/, *.md

3. railway.json:
   {
     "build": { "builder": "DOCKERFILE" },
     "deploy": { "startCommand": "java -jar app.jar", "healthcheckPath": "/actuator/health" }
   }

4. Add spring-boot-starter-actuator dependency, configure in application.yml:
   expose only /actuator/health and /actuator/info endpoints.

5. application-prod.yml with production settings:
   - ddl-auto: validate (never create/update in prod)
   - connection pool: HikariCP max-pool-size 10
   - logging: WARN level except com.localpro: INFO
```

---

## Key Architecture Decisions

| Decision | Choice | Reason |
|---|---|---|
| Auth strategy | Firebase JWT verified server-side | No custom auth code, scales for free |
| Geo type | PostGIS GEOGRAPHY(POINT) | Accurate distance in meters, indexed |
| Chat broker | In-memory SimpleBroker | Sufficient for MVP, Redis Pub/Sub later |
| File storage | Cloudinary | Free tier 25GB, no S3 setup needed |
| Migrations | Flyway | Version-controlled schema, safe rollout |
| Tests | Testcontainers | Real PostgreSQL + PostGIS in tests |

---

## Running Locally

```bash
# Start DB
docker-compose up -d

# Set env vars (copy from .env.example)
export GOOGLE_APPLICATION_CREDENTIALS=./firebase-service-account.json
export CLOUDINARY_URL=cloudinary://key:secret@cloud

# Run
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Swagger UI
open http://localhost:8080/swagger-ui.html
```
