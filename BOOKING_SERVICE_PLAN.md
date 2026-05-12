# Booking Service — Design & Security Plan

## 1. Current State & Gaps

| Area | Problem |
|---|---|
| Double-tap | No idempotency guard — same request submitted twice creates two bookings and charges twice |
| Duplicate check | No check for same `(customer, listing, scheduledAt)` with active status |
| Provider conflict | Provider can be double-booked across different listings at the same time slot |
| Payment order | `processPayment()` fires **before** `bookingRepository.save()` — a DB failure leaves money taken but no booking |
| Response shape | Flat record exposes raw IDs; UI needs derived flags (`canCancel`, `canConfirm`) and nested objects |
| Missing endpoints | No `GET /api/bookings/{id}` and no `PUT /{id}/complete` |
| Cancellation | No `cancellation_reason` field stored |
| Notifications | Provider receives no push notification when a booking is created |
| Pagination | `GET /api/bookings/my` returns all rows unbounded |

---

## 2. Booking Status Machine

```
                  ┌──────────┐
      create ───► │  PENDING │
                  └────┬─────┘
          provider confirms │         customer or provider cancels
                  ┌─────▼──────┐ ◄────────────────────────────────┐
                  │ CONFIRMED  │                                    │
                  └─────┬──────┘                                   │
     scheduledAt passes │         customer cancels (≥2h before)    │
                  ┌─────▼──────┐ ◄────────────────────────────────┘
                  │ COMPLETED  │   provider marks done
                  └────────────┘
```

**Rules:**
- Only the **provider** can move `PENDING → CONFIRMED`.
- Customer can cancel `PENDING` at any time; can cancel `CONFIRMED` only if `scheduledAt` is > 2 hours away.
- Provider can cancel `PENDING` or `CONFIRMED` at any time (rare, but must exist).
- `COMPLETED` and `CANCELLED` are terminal — no further transitions.
- `COMPLETED` should eventually be set automatically (scheduler job, out of scope here).

---

## 3. Double-Tap & Idempotency

### Layer 1 — Flutter (client-side)
- Disable the "Book" button immediately on first tap; re-enable only if the request returns a non-2xx response.
- Store the last `Idempotency-Key` in local state; do not generate a new key on retry.

### Layer 2 — HTTP Idempotency Key (backend)
Client sends `Idempotency-Key: <uuid-v4>` header with every `POST /api/bookings`.

Backend stores key → serialised `BookingResponse` in Caffeine for **24 h**. If the key is seen again, return the cached response immediately (no DB hit, no charge).

```
POST /api/bookings
Idempotency-Key: f47ac10b-58cc-4372-a567-0e02b2c3d479
```

Implementation sketch:
```java
// BookingIdempotencyFilter (OncePerRequestFilter)
String key = request.getHeader("Idempotency-Key");
if (key != null) {
    BookingResponse cached = idempotencyCache.getIfPresent(key);
    if (cached != null) { return cached with 200; }
}
// ... proceed, then cache result under key
```

### Layer 3 — Database unique constraint
Prevent duplicates at the storage level regardless of application logic:

```sql
-- V11__booking_constraints.sql
CREATE UNIQUE INDEX idx_bookings_no_duplicate_active
    ON bookings (customer_id, listing_id, scheduled_at)
    WHERE status IN ('PENDING', 'CONFIRMED');
```

This is the last line of defence — even a bug in the service layer cannot create two active bookings for the same slot.

### Layer 4 — Service-layer guard (fast fail, good error message)
```java
// Before processPayment()
boolean alreadyBooked = bookingRepository.existsActiveBooking(
        customer.getId(), req.listingId(), req.scheduledAt());
if (alreadyBooked) {
    throw new IllegalArgumentException(
        "You already have an active booking for this listing at this time");
}
```

Repository query:
```java
@Query("""
    SELECT COUNT(b) > 0 FROM Booking b
    WHERE b.customer.id = :customerId
      AND b.listing.id  = :listingId
      AND b.scheduledAt = :scheduledAt
      AND b.status IN (com.localpro.booking.BookingStatus.PENDING,
                       com.localpro.booking.BookingStatus.CONFIRMED)
    """)
boolean existsActiveBooking(UUID customerId, UUID listingId, Instant scheduledAt);
```

---

## 4. Provider Slot Conflict Check

After the duplicate check, also verify the provider is free:

```java
boolean providerBusy = bookingRepository.existsProviderConflict(
        provider.getId(), req.scheduledAt());
if (providerBusy) {
    throw new IllegalArgumentException(
        "This provider is already booked at the requested time");
}
```

```java
@Query("""
    SELECT COUNT(b) > 0 FROM Booking b
    WHERE b.provider.id = :providerId
      AND b.scheduledAt = :scheduledAt
      AND b.status = com.localpro.booking.BookingStatus.CONFIRMED
    """)
boolean existsProviderConflict(UUID providerId, Instant scheduledAt);
```

---

## 5. Payment Safety — Charge After Save

Current order (unsafe):
```
processPayment() ──► save() ──► [DB throws] ──► money gone, no booking
```

Safe order:
```
save(status=PENDING, paymentStatus=PENDING)
  ──► processPayment()
        ──► on success: booking.paymentStatus = PAID
        ──► on failure: booking.status = CANCELLED, throw 502
  ──► save(updated statuses)
```

Because the entire `create()` method runs inside `@Transactional`, a payment failure that throws an exception will roll back the initial save — no orphan booking, no charge. When real payment integration arrives, the pattern is: save first → charge → update.

---

## 6. Enriched Booking Response

Replace the flat `BookingResponse` record with a structured shape:

```json
{
  "id": "837af6aa-...",
  "status": "PENDING",
  "scheduledAt": "2026-05-13T08:15:00Z",
  "createdAt": "2026-05-12T16:08:07Z",
  "updatedAt": "2026-05-12T16:08:07Z",

  "listing": {
    "id": "35cf63b5-...",
    "title": "Home IT Support – Nicosia",
    "address": "Prodromou St, Nicosia",
    "city": "Nicosia",
    "price": 30.00,
    "priceType": "PER_HOUR",
    "photoUrl": "https://res.cloudinary.com/..."
  },

  "provider": {
    "id": "...",
    "name": "Antonis Stavrou",
    "avatarUrl": "https://...",
    "phone": "+357 99 111 002"
  },

  "customer": {
    "id": "...",
    "name": "Vlad Chabak"
  },

  "payment": {
    "type": "CREDIT_CARD",
    "status": "PAID",
    "totalPrice": 30.00
  },

  "calendar": {
    "type": "IN_APP",
    "calendlyLink": null,
    "googleCalendarLink": null
  },

  "notes": null,
  "cancellationReason": null,

  "actions": {
    "canCancel": true,
    "canConfirm": false
  }
}
```

`canCancel` and `canConfirm` are computed server-side based on the caller's role and the booking status — the UI uses them directly without embedding business logic in Flutter.

---

## 7. Schema Changes (V11)

```sql
-- V11__booking_improvements.sql

-- 1. Unique partial index (double-tap guard at DB level)
CREATE UNIQUE INDEX idx_bookings_no_duplicate_active
    ON bookings (customer_id, listing_id, scheduled_at)
    WHERE status IN ('PENDING', 'CONFIRMED');

-- 2. Cancellation reason
ALTER TABLE bookings ADD COLUMN cancellation_reason TEXT;

-- 3. Index for provider conflict check
CREATE INDEX idx_bookings_provider_scheduled
    ON bookings (provider_id, scheduled_at)
    WHERE status = 'CONFIRMED';
```

---

## 8. Missing Endpoints

| Method | Path | Who | Purpose |
|---|---|---|---|
| `GET` | `/api/bookings/{id}` | customer or provider | Fetch single booking by ID |
| `PUT` | `/api/bookings/{id}/complete` | provider only | Mark service as delivered |
| `PUT` | `/api/bookings/{id}/cancel` | customer or provider | Already exists; add `reason` body field |

Request body for cancel:
```json
{ "reason": "Customer requested reschedule" }
```

Pagination for `GET /api/bookings/my`:
```
GET /api/bookings/my?page=0&size=20&status=CONFIRMED
```

---

## 9. FCM Notification on Booking Created

After a booking is saved, send a push notification to the provider:

```java
// in BookingService.create(), after bookingRepository.save()
fcmService.send(
    provider.getFcmToken(),
    "New booking request",
    customer.getName() + " booked "" + listing.getTitle() + """,
    Map.of("bookingId", saved.getId().toString(), "type", "NEW_BOOKING")
);
```

Provider taps the notification → deep-link opens the booking detail screen.

---

## 10. Implementation Order

| Step | What | Files touched |
|---|---|---|
| 1 | `V11__booking_improvements.sql` | new migration |
| 2 | Add `cancellationReason` to `Booking` entity | `Booking.java` |
| 3 | Add `existsActiveBooking` + `existsProviderConflict` queries | `BookingRepository.java` |
| 4 | Reorder payment (save → charge → update) + add guards | `BookingService.java` |
| 5 | `BookingIdempotencyFilter` + Caffeine cache bean | new filter + `JacksonConfig.java` or separate config |
| 6 | Replace `BookingResponse` flat record with nested DTOs | `dto/` package |
| 7 | Add `canCancel`/`canConfirm` logic to `toResponse()` | `BookingService.java` |
| 8 | Add `GET /{id}` and `PUT /{id}/complete` | `BookingController.java` + `BookingService.java` |
| 9 | Paginate `GET /my` | `BookingRepository.java` + `BookingController.java` |
| 10 | FCM on booking created | `BookingService.java` |
