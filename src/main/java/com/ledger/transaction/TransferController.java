package com.ledger.transaction;

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
@RequestMapping("/api")
public class TransferController {

    private final TransferService transferService;
    private final ReversalService reversalService;

    public TransferController(TransferService transferService, ReversalService reversalService) {
        this.transferService = transferService;
        this.reversalService = reversalService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return IdempotentResponse.of(transferService.transfer(idempotencyKey, request), HttpStatus.CREATED);
    }

    @PostMapping("/transactions/{id}/reverse")
    public ResponseEntity<TransferResponse> reverse(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID id) {
        return IdempotentResponse.of(reversalService.reverse(idempotencyKey, id), HttpStatus.CREATED);
    }

    @GetMapping("/transactions/{id}")
    public TransferResponse getTransaction(@PathVariable UUID id) {
        return transferService.getTransaction(id);
    }
}
