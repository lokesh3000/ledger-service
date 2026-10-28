package com.ledger.hold;

import com.ledger.account.Account;
import com.ledger.account.AccountLocker;
import com.ledger.common.BusinessRuleException;
import com.ledger.common.NotFoundException;
import com.ledger.idempotency.IdempotencyExecutor;
import com.ledger.transaction.LedgerPoster;
import com.ledger.transaction.LedgerTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Authorize-then-capture, like a card payment.
 *   place   -> reserves money (account.held goes up, available goes down, NO ledger entries yet)
 *   capture -> turns the reservation into a real transfer (ledger entries are written)
 *   release -> drops the reservation
 *
 * Lock order is always: hold row first, then accounts (sorted). That keeps it deadlock-free.
 */
@Service
public class HoldService {

    private final IdempotencyExecutor idempotency;
    private final AccountLocker locker;
    private final LedgerPoster poster;
    private final HoldRepository holds;

    public HoldService(IdempotencyExecutor idempotency, AccountLocker locker,
                       LedgerPoster poster, HoldRepository holds) {
        this.idempotency = idempotency;
        this.locker = locker;
        this.poster = poster;
        this.holds = holds;
    }

    public IdempotencyExecutor.Outcome<HoldResponse> place(String key, PlaceHoldRequest request) {
        return idempotency.execute(key, "HOLD_PLACE", request.canonical(), 201,
                HoldResponse.class, () -> doPlace(request));
    }

    public IdempotencyExecutor.Outcome<HoldResponse> capture(String key, UUID holdId, CaptureHoldRequest request) {
        return idempotency.execute(key, "HOLD_CAPTURE", request.canonical(holdId), 200,
                HoldResponse.class, () -> doCapture(holdId, request));
    }

    public IdempotencyExecutor.Outcome<HoldResponse> release(String key, UUID holdId) {
        return idempotency.execute(key, "HOLD_RELEASE", holdId.toString(), 200,
                HoldResponse.class, () -> doRelease(holdId));
    }

    @Transactional(readOnly = true)
    public HoldResponse get(UUID id) {
        return holds.findById(id)
                .map(HoldResponse::from)
                .orElseThrow(() -> new NotFoundException("Hold not found: " + id));
    }

    // ---------------------------------------------------------------- internals

    private HoldResponse doPlace(PlaceHoldRequest request) {
        Account account = locker.lockInOrder(request.accountId()).get(request.accountId());
        if (!account.getCurrency().equals(request.currency())) {
            throw new BusinessRuleException("Currency mismatch: account uses " + account.getCurrency());
        }
        account.placeHold(request.amount());   // throws if available funds are too low
        Hold hold = holds.save(new Hold(account.getId(), request.amount(), request.currency(), request.description()));
        return HoldResponse.from(hold);
    }

    private HoldResponse doCapture(UUID holdId, CaptureHoldRequest request) {
        Hold hold = findForUpdate(holdId);
        hold.requireActive();
        if (hold.getAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("Cannot capture a hold into its own account");
        }

        Map<UUID, Account> locked = locker.lockInOrder(hold.getAccountId(), request.toAccountId());
        Account from = locked.get(hold.getAccountId());
        Account to = locked.get(request.toAccountId());

        String description = (request.description() == null || request.description().isBlank())
                ? "Capture of hold " + holdId
                : request.description();

        from.releaseHold(hold.getAmount());   // the reservation becomes spendable again...
        LedgerTransaction tx = poster.post(from, to, hold.getAmount(), description, null);   // ...and is spent at once
        hold.markCaptured(tx.getId());
        return HoldResponse.from(hold);
    }

    private HoldResponse doRelease(UUID holdId) {
        Hold hold = findForUpdate(holdId);
        hold.requireActive();

        Account account = locker.lockInOrder(hold.getAccountId()).get(hold.getAccountId());
        account.releaseHold(hold.getAmount());
        hold.markReleased();
        return HoldResponse.from(hold);
    }

    private Hold findForUpdate(UUID holdId) {
        return holds.findByIdForUpdate(holdId)
                .orElseThrow(() -> new NotFoundException("Hold not found: " + holdId));
    }
}
