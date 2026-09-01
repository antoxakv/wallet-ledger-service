DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'wallet-ledger-service') THEN
        CREATE ROLE "wallet-ledger-service" WITH LOGIN PASSWORD 'wallet-ledger-service';
    END IF;
END
$$;

ALTER DATABASE "wallet-ledger-service" SET timezone TO 'UTC';
ALTER ROLE "wallet-ledger-service" SET timezone TO 'UTC';

CREATE SCHEMA IF NOT EXISTS "wallet-ledger-service";
ALTER SCHEMA "wallet-ledger-service" OWNER TO "wallet-ledger-service";

CREATE TYPE operation_type AS ENUM ('debit', 'credit');
CREATE TYPE operation_status AS ENUM ('success', 'failed');
CREATE TYPE wallet_updated_outbox_status AS ENUM ('pending', 'processing', 'published', 'failed', 'exhausted');

CREATE TABLE IF NOT EXISTS wallet (
    id uuid PRIMARY KEY,
    balance numeric(38, 0) NOT NULL,
    CONSTRAINT wallet_balance_not_negative_check CHECK (balance >= 0),
    updated timestamp with time zone NOT NULL DEFAULT now()
);

COMMENT ON TABLE wallet IS 'Current wallet balance state.';
COMMENT ON COLUMN wallet.id IS 'Wallet identifier.';
COMMENT ON COLUMN wallet.balance IS 'Current wallet balance in minor units. Must not be negative.';
COMMENT ON COLUMN wallet.updated IS 'Timestamp when the wallet balance was last updated.';

CREATE TABLE IF NOT EXISTS wallet_ledger (
    transaction_id uuid PRIMARY KEY,
    wallet_id uuid NOT NULL,
    type_operation operation_type NOT NULL,
    amount numeric(38, 0) NOT NULL,
    status operation_status NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    balance_after numeric(38, 0) NOT NULL,
    CONSTRAINT wallet_ledger_wallet_id_fk FOREIGN KEY (wallet_id) REFERENCES wallet (id),
    CONSTRAINT wallet_ledger_amount_positive_check CHECK (amount > 0),
    CONSTRAINT wallet_ledger_balance_after_not_negative_check CHECK (balance_after >= 0)
);

COMMENT ON TABLE wallet_ledger IS 'Immutable append-only wallet transaction log.';
COMMENT ON COLUMN wallet_ledger.transaction_id IS 'Unique transaction identifier used for idempotency.';
COMMENT ON COLUMN wallet_ledger.wallet_id IS 'Wallet affected by the transaction.';
COMMENT ON COLUMN wallet_ledger.type_operation IS 'Wallet operation type: debit or credit.';
COMMENT ON COLUMN wallet_ledger.amount IS 'Positive operation amount in minor units.';
COMMENT ON COLUMN wallet_ledger.status IS 'Operation result status.';
COMMENT ON COLUMN wallet_ledger.created_at IS 'Timestamp when the ledger entry was created.';
COMMENT ON COLUMN wallet_ledger.balance_after IS 'Wallet balance after applying the operation in minor units.';

CREATE INDEX IF NOT EXISTS wallet_updated_idx ON wallet (updated);

CREATE TABLE IF NOT EXISTS wallet_updated_outbox (
    transaction_id uuid PRIMARY KEY,
    wallet_id uuid NOT NULL,
    type_operation operation_type NOT NULL,
    amount numeric(38, 0) NOT NULL,
    balance_after numeric(38, 0) NOT NULL,
    event_created_at timestamp with time zone NOT NULL,
    status wallet_updated_outbox_status NOT NULL DEFAULT 'pending',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone NOT NULL DEFAULT now(),
    locked_until timestamp with time zone,
    last_error text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    published_at timestamp with time zone,
    CONSTRAINT wallet_updated_outbox_transaction_id_fk FOREIGN KEY (transaction_id) REFERENCES wallet_ledger (transaction_id),
    CONSTRAINT wallet_updated_outbox_wallet_id_fk FOREIGN KEY (wallet_id) REFERENCES wallet (id),
    CONSTRAINT wallet_updated_outbox_attempts_not_negative_check CHECK (attempts >= 0),
    CONSTRAINT wallet_updated_outbox_amount_positive_check CHECK (amount > 0),
    CONSTRAINT wallet_updated_outbox_balance_after_not_negative_check CHECK (balance_after >= 0)
);

COMMENT ON TABLE wallet_updated_outbox IS 'Outbox for reliable WalletUpdatedEvent publication.';
COMMENT ON COLUMN wallet_updated_outbox.transaction_id IS 'Transaction identifier and outbox event identifier.';
COMMENT ON COLUMN wallet_updated_outbox.wallet_id IS 'Wallet affected by the published event.';
COMMENT ON COLUMN wallet_updated_outbox.type_operation IS 'Wallet operation type copied from the ledger entry.';
COMMENT ON COLUMN wallet_updated_outbox.amount IS 'Positive operation amount in minor units copied from the ledger entry.';
COMMENT ON COLUMN wallet_updated_outbox.balance_after IS 'Wallet balance after the operation in minor units.';
COMMENT ON COLUMN wallet_updated_outbox.event_created_at IS 'Business event creation timestamp copied from the ledger entry.';
COMMENT ON COLUMN wallet_updated_outbox.status IS 'Outbox publication status.';
COMMENT ON COLUMN wallet_updated_outbox.attempts IS 'Number of broker publication attempts.';
COMMENT ON COLUMN wallet_updated_outbox.next_attempt_at IS 'Earliest timestamp when the publisher may retry the event.';
COMMENT ON COLUMN wallet_updated_outbox.locked_until IS 'Timestamp until which a publisher instance owns this event.';
COMMENT ON COLUMN wallet_updated_outbox.last_error IS 'Last broker publication error message.';
COMMENT ON COLUMN wallet_updated_outbox.created_at IS 'Timestamp when the outbox row was created.';
COMMENT ON COLUMN wallet_updated_outbox.published_at IS 'Timestamp when the event was successfully published.';

CREATE INDEX IF NOT EXISTS wallet_updated_outbox_pending_idx
    ON wallet_updated_outbox (status, next_attempt_at, created_at);

GRANT USAGE ON SCHEMA "wallet-ledger-service" TO "wallet-ledger-service";
GRANT ALL PRIVILEGES ON TABLE wallet TO "wallet-ledger-service";
GRANT SELECT, INSERT ON TABLE wallet_ledger TO "wallet-ledger-service";
GRANT SELECT, INSERT, UPDATE ON TABLE wallet_updated_outbox TO "wallet-ledger-service";
