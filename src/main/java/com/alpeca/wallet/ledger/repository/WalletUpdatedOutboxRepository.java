package com.alpeca.wallet.ledger.repository;

import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for wallet updated outbox events.
 */
public interface WalletUpdatedOutboxRepository extends JpaRepository<WalletUpdatedOutboxEvent, UUID> {

	/**
	 * Atomically claims ready outbox events for broker publication.
	 *
	 * @param limit maximum number of events to claim.
	 * @param maxAttempts maximum allowed publication attempts.
	 * @param now current timestamp used for retry and lock checks.
	 * @param lockedUntil timestamp until which claimed events are owned by this publisher.
	 * @return claimed events marked as processing.
	 */
	@Transactional
	@Query(
			value = """
					update wallet_updated_outbox
					set status = 'processing',
						attempts = attempts + 1,
						locked_until = :lockedUntil,
						last_error = null
					where transaction_id in (
						select transaction_id
						from wallet_updated_outbox
						where status in ('pending', 'failed', 'processing')
							and next_attempt_at <= :now
							and (locked_until is null or locked_until <= :now)
							and attempts < :maxAttempts
						order by created_at
						for update skip locked
						limit :limit
					)
					returning *
					""",
			nativeQuery = true
	)
	List<WalletUpdatedOutboxEvent> claimReadyEvents(
			@Param("limit") int limit,
			@Param("maxAttempts") int maxAttempts,
			@Param("now") OffsetDateTime now,
			@Param("lockedUntil") OffsetDateTime lockedUntil
	);
}
