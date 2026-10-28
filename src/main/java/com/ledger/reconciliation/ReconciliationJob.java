package com.ledger.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the consistency check on a timer. It only reads, so running it on several instances is harmless. */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationService service;

    public ReconciliationJob(ReconciliationService service) {
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${ledger.reconciliation.initial-delay-ms:30000}",
            fixedDelayString = "${ledger.reconciliation.interval-ms:300000}")
    public void run() {
        try {
            ReconciliationReport report = service.run();
            if (report.consistent()) {
                log.info("Reconciliation OK: {} accounts, {} transactions, {} entries",
                        report.accountsChecked(), report.transactionsChecked(), report.entriesChecked());
            } else {
                log.error("RECONCILIATION FAILED: {} problem(s): {}", report.problems().size(), report.problems());
            }
        } catch (RuntimeException e) {
            log.error("Reconciliation could not run", e);
        }
    }
}
