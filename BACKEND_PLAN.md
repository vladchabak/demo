# LocalPro Backend — Current State & Plan
> Last updated: May 12, 2026

## Stack
- Java 21, Spring Boot 3.3.6
- PostgreSQL 17.6 + PostGIS (Supabase)
- Firebase Admin SDK (Auth + FCM)
- Flyway migrations
- HikariCP, Hibernate Spatial
- Cloudinary (configured, not fully used)
- Deployed on Railway

## Live URLs
- API: https://demo-production-2680.up.railway.app
- Swagger: https://demo-production-2680.up.railway.app/swagger-ui.html

## Database Migrations Applied
- V1: init schema (users, categories, service_listings, service_photos)
- V2: chat + reviews (chats, messages, reviews)
- V3: FCM token column on users
- V4: listings enhancements (is_verified, is_visible_on_map, price_type, custom_questions)
- V5: bookings table
- V6: listing registration fields (priceType, customQuestions, verifiedAt)
- V7: spatial GiST index on service_listings.location
- V8: catalog search fields

## Implemented Features

### Auth ✅
- FirebaseTokenFilter — verifies JWT, sets SecurityContext
- FirebaseConfig — initializes FirebaseApp + exposes FirebaseMessaging bean
- Dev-mode bypass (Bearer dev-token) guarded by app.dev-mode flag
- UserService.findOrCreateByFirebaseToken()
- GET /api/users/me
- PUT /api/users/me
- GET /api/users/{id}
- PUT /api/users/me/fcm-token

### Listings ✅
- POST /api/listings — create listing
- PUT /api/listings/{id} — update listing
- DELETE /api/listings/{id} — soft delete
- GET /api/listings/{id} — public detail (increments viewCount atomically)
- GET /api/listings/my — provider's own listings
- GET /api/listings/nearby — PostGIS ST_DWithin search (only isVisibleOnMap=true)
- GET /api/listings/search — full text + filter + sort catalog search
- GET /api/listings/popular — top 10 by viewCount
- GET /api/listings/recent — latest 10 verified
- GET /api/listings/category/{categoryId}
- POST /api/listings/{id}/verify — mock verification → sets isVerified=true, isVisibleOnMap=true

### Categories ✅
- GET /api/categories — cached with Caffeine @Cacheable

### Chat ✅
- WebSocket STOMP endpoint /ws
- POST /api/chats — start chat
- GET /api/chats — list my chats
- GET /api/chats/{id}/messages — paginated history
- POST /api/chats/{id}/read — mark as read
- @MessageMapping /app/chat.send — real-time send

### Reviews ✅
- POST /api/listings/{id}/reviews
- GET /api/listings/{id}/reviews
- Incremental rating formula (no aggregate queries)
- Optimistic locking with @Version on ServiceListing + User

### Bookings ✅
- POST /api/bookings — create booking
- GET /api/bookings/my — user's bookings
- PUT /api/bookings/{id}/cancel
- PUT /api/bookings/{id}/confirm
- MockPaymentService (CREDIT_CARD, CASH, BONUSES always succeed)
- MockCalendarService (generates Calendly/Google Calendar/IN_APP links)
- CalendarType: IN_APP, CALENDLY, GOOGLE_CALENDAR
- PaymentType: CREDIT_CARD, CASH, BONUSES

### Media ✅
- POST /api/listings/{id}/photos
- DELETE /api/listings/{id}/photos/{photoId}
- PUT /api/listings/{id}/photos/order

### Payments Stub ✅
- POST /api/payments/intent → stub response

## Performance Optimizations Applied
- Virtual threads enabled (spring.threads.virtual.enabled=true)
- @EnableAsync + @EnableScheduling on main class
- Incremental rating formula (4 aggregate queries → 0)
- Atomic viewCount increment (@Modifying query)
- GiST spatial index on service_listings.location
- Caffeine cache on categories (1h TTL)
- FCM notifications @Async
- HikariCP: max-pool-size=10, leak-detection=60s

## Security
- CORS: explicit origins (localhost + vladchabak.github.io)
- Dev-token guarded by app.dev-mode=false in prod
- Swagger disabled in prod profile
- @Min @Max validation on review rating
- @NotNull on listing lat/lng

## Error Handling
- GlobalExceptionHandler (@RestControllerAdvice)
  - 400: MethodArgumentNotValidException, HttpMessageNotReadableException
  - 403: AccessDeniedException
  - 404: EntityNotFoundException
  - 409: ObjectOptimisticLockingFailureException
  - 500: all others
- RequestLoggingFilter — logs all requests with method, path, status, duration
- Detailed service-level logging with === markers

## Railway Environment Variables Required
PGHOST=aws-0-eu-west-1.pooler.supabase.com
PGPORT=5432
PGDATABASE=postgres
PGUSER=postgres.jwnndgnsslsdplbsqmns
PGPASSWORD=!Vladius080197
SPRING_PROFILES_ACTIVE=prod
JAVA_TOOL_OPTIONS=-Dspring.profiles.active=prod -Xmx256m -Xms128m
GOOGLE_APPLICATION_CREDENTIALS=
CLOUDINARY_URL=cloudinary://API_KEY:API_SECRET@CLOUD_NAME
FIREBASE_SERVICE_ACCOUNT_JSON={...full JSON one line...}

## Known Issues
- OOM if -Xmx256m removed from JAVA_TOOL_OPTIONS
- Railway occasionally loses env variables after redeploy
- Booking 400 error being debugged (CalendarType IN_APP fixed, PaymentService bean fixed)

## TODO / Next Steps

### High Priority
- [ ] Fix booking flow end-to-end (currently 400/500 errors)
- [ ] Cloudinary — real photo uploads
- [ ] Test WebSocket chat end-to-end

### Medium Priority
- [ ] Integration tests with Testcontainers
- [ ] RatingReconciliationJob (nightly cron)
- [ ] PhotoCleanupJob (weekly Cloudinary cleanup)
- [ ] Notification retry queue

### Future
- [ ] Stripe real payments
- [ ] Real Calendly/Google Calendar API integration
- [ ] Rate limiting (Bucket4j)
- [ ] Redis Pub/Sub for WebSocket (multi-pod)
- [ ] Audit logging

## Deploy Command
```bash
git add .
git commit -m "your message"
git push origin main:master
```
