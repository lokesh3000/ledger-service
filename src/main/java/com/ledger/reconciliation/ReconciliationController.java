package com.ledger.reconciliation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 3 will restrict /api/admin/** to the ADMIN role. */
@RestController
@RequestMapping("/api/admin")
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @GetMapping("/reconciliation")
    public ReconciliationReport run() {
        return service.run();
    }
}
