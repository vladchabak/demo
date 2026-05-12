package com.localpro.booking;

import com.localpro.auth.CurrentUser;
import com.localpro.booking.dto.BookingResponse;
import com.localpro.booking.dto.CancelBookingRequest;
import com.localpro.booking.dto.CreateBookingRequest;
import com.localpro.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(@CurrentUser User user,
                                                  @Valid @RequestBody CreateBookingRequest request,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        BookingResponse response = bookingService.create(user, request, idempotencyKey);
        return ResponseEntity
                .created(URI.create("/api/bookings/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getById(@CurrentUser User user, @PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getById(id, user));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @CurrentUser User user,
            @RequestParam(required = false) BookingStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(bookingService.getMyBookings(user, status, pageable));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@CurrentUser User user, @PathVariable UUID id,
                                                  @RequestBody(required = false) CancelBookingRequest request) {
        CancelBookingRequest req = request != null ? request : new CancelBookingRequest(null);
        return ResponseEntity.ok(bookingService.cancel(id, user, req));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<BookingResponse> confirm(@CurrentUser User user, @PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.confirm(id, user));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<BookingResponse> complete(@CurrentUser User user, @PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.complete(id, user));
    }
}
