package com.ledger.history;

import com.ledger.transaction.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /** Newest first. The id tie-breaker keeps page boundaries stable when timestamps are equal. */
    @Query(value = "select new com.ledger.history.EntryHistoryItem("
            + "e.id, e.transaction.id, e.direction, e.amount, e.createdAt) "
            + "from LedgerEntry e where e.accountId = :accountId "
            + "order by e.createdAt desc, e.id desc",
            countQuery = "select count(e) from LedgerEntry e where e.accountId = :accountId")
    Page<EntryHistoryItem> findHistory(@Param("accountId") UUID accountId, Pageable pageable);
}
