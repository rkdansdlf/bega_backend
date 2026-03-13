-- V105: Relax legacy duplicate NOT NULL constraints on mixed legacy/snake_case schemas.
-- Context:
-- - party_applications/chat_messages currently contain both legacy columns
--   (e.g., applicantbadge, partyid) and snake_case columns
--   (e.g., applicant_badge, party_id).
-- - JPA writes snake_case for many fields, so legacy duplicate NOT NULL constraints
--   can break inserts when legacy columns are left null.

DO $$
BEGIN
    -- -------------------------------------------------------------------------
    -- party_applications: keep data in sync, then relax legacy duplicate columns
    -- -------------------------------------------------------------------------
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'applicantbadge'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'applicant_badge'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET applicant_badge = COALESCE(applicant_badge, applicantbadge),
                         applicantbadge = COALESCE(applicantbadge, applicant_badge)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'applicantname'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'applicant_name'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET applicant_name = COALESCE(applicant_name, applicantname),
                         applicantname = COALESCE(applicantname, applicant_name)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'applicantrating'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'applicant_rating'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET applicant_rating = COALESCE(applicant_rating, applicantrating),
                         applicantrating = COALESCE(applicantrating, applicant_rating)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'depositamount'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'deposit_amount'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET deposit_amount = COALESCE(deposit_amount, depositamount),
                         depositamount = COALESCE(depositamount, deposit_amount)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'ispaid'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'is_paid'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET is_paid = COALESCE(is_paid, ispaid),
                         ispaid = COALESCE(ispaid, is_paid)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'isapproved'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'is_approved'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET is_approved = COALESCE(is_approved, isapproved),
                         isapproved = COALESCE(isapproved, is_approved)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'isrejected'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'is_rejected'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET is_rejected = COALESCE(is_rejected, isrejected),
                         isrejected = COALESCE(isrejected, is_rejected)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'paymenttype'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'payment_type'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET payment_type = COALESCE(payment_type, paymenttype),
                         paymenttype = COALESCE(paymenttype, payment_type)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'createdat'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'party_applications' AND column_name = 'created_at'
        ) THEN
            EXECUTE 'UPDATE public.party_applications
                     SET created_at = COALESCE(created_at, createdat),
                         createdat = COALESCE(createdat, created_at, NOW())';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'applicantbadge'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN applicantbadge DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'applicantname'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN applicantname DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'applicantrating'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN applicantrating DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'depositamount'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN depositamount DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'ispaid'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN ispaid DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'isapproved'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN isapproved DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'isrejected'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN isrejected DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'paymenttype'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN paymenttype DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'party_applications'
              AND column_name = 'createdat'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.party_applications ALTER COLUMN createdat DROP NOT NULL';
        END IF;
    END IF;

    -- -------------------------------------------------------------------------
    -- chat_messages: keep duplicate columns synchronized and relax legacy strictness
    -- -------------------------------------------------------------------------
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'chat_messages' AND column_name = 'partyid'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'chat_messages' AND column_name = 'party_id'
        ) THEN
            EXECUTE 'UPDATE public.chat_messages
                     SET party_id = COALESCE(party_id, partyid),
                         partyid = COALESCE(partyid, party_id)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'chat_messages' AND column_name = 'senderid'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'chat_messages' AND column_name = 'sender_id'
        ) THEN
            EXECUTE 'UPDATE public.chat_messages
                     SET sender_id = COALESCE(sender_id, senderid),
                         senderid = COALESCE(senderid, sender_id)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'chat_messages' AND column_name = 'sendername'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'chat_messages' AND column_name = 'sender_name'
        ) THEN
            EXECUTE 'UPDATE public.chat_messages
                     SET sender_name = COALESCE(sender_name, sendername),
                         sendername = COALESCE(sendername, sender_name)';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'chat_messages' AND column_name = 'createdat'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'chat_messages' AND column_name = 'created_at'
        ) THEN
            EXECUTE 'UPDATE public.chat_messages
                     SET created_at = COALESCE(created_at, createdat),
                         createdat = COALESCE(createdat, created_at, NOW())';
            EXECUTE 'ALTER TABLE public.chat_messages ALTER COLUMN createdat SET DEFAULT NOW()';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'chat_messages'
              AND column_name = 'partyid'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.chat_messages ALTER COLUMN partyid DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'chat_messages'
              AND column_name = 'senderid'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.chat_messages ALTER COLUMN senderid DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'chat_messages'
              AND column_name = 'sendername'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.chat_messages ALTER COLUMN sendername DROP NOT NULL';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'chat_messages'
              AND column_name = 'createdat'
              AND is_nullable = 'NO'
        ) THEN
            EXECUTE 'ALTER TABLE public.chat_messages ALTER COLUMN createdat DROP NOT NULL';
        END IF;
    END IF;
END
$$;
