package com.ledger.hold;

import com.ledger.idempotency.IdempotentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/holds")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @PostMapping
    public ResponseEntity<HoldResponse> place(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PlaceHoldRequest request) {
        return IdempotentResponse.of(holdService.place(key, request), HttpStatus.CREATED);
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<HoldResponse> capture(
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable UUID id,
            @Valid @RequestBody CaptureHoldRequest request) {
        return IdempotentResponse.of(holdService.capture(key, id, request), HttpStatus.OK);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<HoldResponse> release(
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable UUID id) {
        return IdempotentResponse.of(holdService.release(key, id), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public HoldResponse get(@PathVariable UUID id) {
        return holdService.get(id);
    }
}
