-- V188: Persist the party-expiry/provider-dispatch linearization point
ALTER TABLE payout_transactions
    ADD COLUMN IF NOT EXISTS provider_dispatch_intent_at TIMESTAMP(6) WITH TIME ZONE;
