package com.localpro.booking;

import com.localpro.auth.CurrentUser;
import com.localpro.booking.dto.BookingResponse;
import com.localpro.booking.dto.CreateBookingRequest;
import com.localpro.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(@CurrentUser User user,
                                                  @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.create(user, request);
        return ResponseEntity
                .created(URI.create("/api/bookings/" + response.id()))
                .body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponse>> getMyBookings(@CurrentUser User user) {
        return ResponseEntity.ok(bookingService.getMyBookings(user));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@CurrentUser User user, @PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.cancel(id, user));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<BookingResponse> confirm(@CurrentUser User user, @PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.confirm(id, user));
    }
}
