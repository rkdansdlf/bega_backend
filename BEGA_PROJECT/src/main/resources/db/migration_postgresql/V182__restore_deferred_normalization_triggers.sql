-- Restore normalization triggers skipped by V36 because their tables did not yet exist.
-- This migration is forward-only: it validates the complete contract before any DDL.

DO $migration$
DECLARE
    function_contract record;
    trigger_contract record;
    actual record;
    relation_oid oid;
    function_oid oid;
    matching_trigger_count integer;
    missing_columns text[];
    missing_triggers text[] := ARRAY[]::text[];
BEGIN
    -- Phase A: validate the complete function, relation, column, and trigger contract.
    FOR function_contract IN
        SELECT *
        FROM (VALUES
            (
                'public.normalize_team_code(text)',
                'text',
                'plpgsql',
                'v',
                false,
                false,
                'cbb828a3508258cfff5b8b3f686c0790d610b522d4af8d32f79a38225e75b4e1',
                $body$BEGIN IF input_code IS NULL THEN RETURN NULL; END IF; CASE UPPER(BTRIM(input_code)) WHEN 'HT' THEN RETURN 'KIA'; WHEN 'DO' THEN RETURN 'DB'; WHEN 'OB' THEN RETURN 'DB'; WHEN 'KI' THEN RETURN 'KH'; WHEN 'NX' THEN RETURN 'KH'; WHEN 'WO' THEN RETURN 'KH'; WHEN 'KW' THEN RETURN 'KH'; WHEN 'BE' THEN RETURN 'HH'; WHEN 'SK' THEN RETURN 'SSG'; WHEN 'SL' THEN RETURN 'SSG'; WHEN 'MBC' THEN RETURN 'LG'; WHEN 'LOT' THEN RETURN 'LT'; ELSE RETURN UPPER(BTRIM(input_code)); END CASE; END;$body$
            ),
            (
                'public.normalize_game_team_codes()',
                'trigger',
                'plpgsql',
                'v',
                false,
                false,
                'e16b35c5e1ee05aa276390d71e611096c6622bb7a9e739474903b68311a4354a',
                $body$BEGIN NEW.home_team = normalize_team_code(NEW.home_team); NEW.away_team = normalize_team_code(NEW.away_team); NEW.winning_team = normalize_team_code(NEW.winning_team); RETURN NEW; END;$body$
            ),
            (
                'public.normalize_team_code_column()',
                'trigger',
                'plpgsql',
                'v',
                false,
                false,
                '7df6deb4b2f29e14425554c75f89c58e3b34f6f30c89d2d4833497b06350a768',
                $body$BEGIN NEW.team_code = normalize_team_code(NEW.team_code); RETURN NEW; END;$body$
            ),
            (
                'public.normalize_team_id_column()',
                'trigger',
                'plpgsql',
                'v',
                false,
                false,
                'df8940d5a9968c2c504557ee12ad93dedb1e87979f8b98ba9f45d273936a42a2',
                $body$BEGIN NEW.team_id = normalize_team_code(NEW.team_id); RETURN NEW; END;$body$
            ),
            (
                'public.normalize_favorite_team_column()',
                'trigger',
                'plpgsql',
                'v',
                false,
                false,
                'b8c81112205cc5f8dcacbc44df2876fa0ea8b1e2e46ff6330a8f20bc7fc8b96d',
                $body$BEGIN NEW.favorite_team = normalize_team_code(NEW.favorite_team); RETURN NEW; END;$body$
            )
        ) AS expected(
            signature,
            return_type,
            language_name,
            volatility,
            security_definer,
            is_strict,
            semantic_sha256,
            normalized_prosrc
        )
    LOOP
        function_oid := to_regprocedure(function_contract.signature);
        IF function_oid IS NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = 'P0001',
                MESSAGE = format(
                    'NORMALIZATION_FUNCTION_CONTRACT_MISMATCH: missing %s',
                    function_contract.signature
                );
        END IF;

        SELECT
            p.prorettype::regtype::text AS return_type,
            language.lanname AS language_name,
            p.provolatile::text AS volatility,
            p.prosecdef AS security_definer,
            p.proisstrict AS is_strict,
            btrim(regexp_replace(p.prosrc, '[[:space:]]+', ' ', 'g')) AS normalized_prosrc
        INTO actual
        FROM pg_proc AS p
        JOIN pg_language AS language ON language.oid = p.prolang
        WHERE p.oid = function_oid
          AND p.prokind = 'f';

        IF NOT FOUND
           OR actual.return_type IS DISTINCT FROM function_contract.return_type
           OR actual.language_name IS DISTINCT FROM function_contract.language_name
           OR actual.volatility IS DISTINCT FROM function_contract.volatility
           OR actual.security_definer IS DISTINCT FROM function_contract.security_definer
           OR actual.is_strict IS DISTINCT FROM function_contract.is_strict
           OR actual.normalized_prosrc IS DISTINCT FROM function_contract.normalized_prosrc
        THEN
            RAISE EXCEPTION USING
                ERRCODE = 'P0001',
                MESSAGE = format(
                    'NORMALIZATION_FUNCTION_CONTRACT_MISMATCH: %s semantic_sha256=%s',
                    function_contract.signature,
                    function_contract.semantic_sha256
                );
        END IF;
    END LOOP;

    FOR trigger_contract IN
        SELECT *
        FROM (VALUES
            ('trg_normalize_game_team_codes', 'game', 'public.normalize_game_team_codes()', ARRAY['home_team', 'away_team', 'winning_team']::text[]),
            ('trg_normalize_game_lineups_team_code', 'game_lineups', 'public.normalize_team_code_column()', ARRAY['team_code']::text[]),
            ('trg_normalize_game_inning_scores_team_code', 'game_inning_scores', 'public.normalize_team_code_column()', ARRAY['team_code']::text[]),
            ('trg_normalize_player_movements_team_code', 'player_movements', 'public.normalize_team_code_column()', ARRAY['team_code']::text[]),
            ('trg_normalize_cheer_post_team_id', 'cheer_post', 'public.normalize_team_id_column()', ARRAY['team_id']::text[]),
            ('trg_normalize_cheer_battle_votes_team_id', 'cheer_battle_votes', 'public.normalize_team_id_column()', ARRAY['team_id']::text[]),
            ('trg_normalize_team_profiles_team_id', 'team_profiles', 'public.normalize_team_id_column()', ARRAY['team_id']::text[]),
            ('trg_normalize_users_favorite_team', 'users', 'public.normalize_favorite_team_column()', ARRAY['favorite_team']::text[])
        ) AS expected(trigger_name, table_name, function_signature, required_columns)
    LOOP
        SELECT relation.oid
        INTO relation_oid
        FROM pg_class AS relation
        JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = 'public'
          AND relation.relname = trigger_contract.table_name
          AND relation.relkind IN ('r', 'p');

        IF relation_oid IS NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = 'P0001',
                MESSAGE = format(
                    'NORMALIZATION_TRIGGER_PREREQUISITE_MISSING: public.%s',
                    trigger_contract.table_name
                );
        END IF;

        SELECT array_agg(required.column_name ORDER BY required.column_name)
        INTO missing_columns
        FROM unnest(trigger_contract.required_columns) AS required(column_name)
        WHERE NOT EXISTS (
            SELECT 1
            FROM pg_attribute AS attribute
            WHERE attribute.attrelid = relation_oid
              AND attribute.attname = required.column_name
              AND attribute.attnum > 0
              AND NOT attribute.attisdropped
              AND attribute.atttypid IN (
                  'text'::regtype,
                  'character varying'::regtype,
                  'character'::regtype
              )
        );

        IF missing_columns IS NOT NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = 'P0001',
                MESSAGE = format(
                    'NORMALIZATION_TRIGGER_PREREQUISITE_MISSING: public.%s missing_or_incompatible_columns=%s',
                    trigger_contract.table_name,
                    missing_columns
                );
        END IF;

        function_oid := to_regprocedure(trigger_contract.function_signature);
        IF function_oid IS NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = 'P0001',
                MESSAGE = format(
                    'NORMALIZATION_FUNCTION_CONTRACT_MISMATCH: missing %s',
                    trigger_contract.function_signature
                );
        END IF;

        SELECT count(*)
        INTO matching_trigger_count
        FROM pg_trigger AS trigger
        JOIN pg_class AS relation ON relation.oid = trigger.tgrelid
        JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = 'public'
          AND trigger.tgname = trigger_contract.trigger_name;

        IF matching_trigger_count = 0 THEN
            missing_triggers := array_append(
                missing_triggers,
                trigger_contract.trigger_name
            );
        ELSIF matching_trigger_count = 1 THEN
            SELECT
                trigger.tgrelid,
                trigger.tgfoid,
                trigger.tgtype,
                trigger.tgenabled::text AS tgenabled,
                trigger.tgqual,
                trigger.tgisinternal,
                trigger.tgnargs,
                trigger.tgconstraint,
                trigger.tgdeferrable,
                trigger.tginitdeferred
            INTO actual
            FROM pg_trigger AS trigger
            JOIN pg_class AS relation ON relation.oid = trigger.tgrelid
            JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
            WHERE namespace.nspname = 'public'
              AND trigger.tgname = trigger_contract.trigger_name;

            IF actual.tgrelid IS DISTINCT FROM relation_oid
               OR actual.tgfoid IS DISTINCT FROM function_oid
               OR actual.tgtype IS DISTINCT FROM 23::smallint
               OR actual.tgenabled IS DISTINCT FROM 'O'
               OR actual.tgqual IS NOT NULL
               OR actual.tgisinternal IS DISTINCT FROM false
               OR actual.tgnargs IS DISTINCT FROM 0::smallint
               OR actual.tgconstraint IS DISTINCT FROM 0::oid
               OR actual.tgdeferrable IS DISTINCT FROM false
               OR actual.tginitdeferred IS DISTINCT FROM false
            THEN
                RAISE EXCEPTION USING
                    ERRCODE = 'P0001',
                    MESSAGE = format(
                        'NORMALIZATION_TRIGGER_CONTRACT_MISMATCH: %s',
                        trigger_contract.trigger_name
                    );
            END IF;
        ELSE
            RAISE EXCEPTION USING
                ERRCODE = 'P0001',
                MESSAGE = format(
                    'NORMALIZATION_TRIGGER_CONTRACT_MISMATCH: %s count=%s',
                    trigger_contract.trigger_name,
                    matching_trigger_count
                );
        END IF;
    END LOOP;

    -- Phase B: create only absent triggers after all Phase A checks have passed.
    IF 'trg_normalize_game_team_codes' = ANY(missing_triggers) THEN
        EXECUTE $ddl$CREATE TRIGGER trg_normalize_game_team_codes BEFORE INSERT OR UPDATE ON public.game FOR EACH ROW EXECUTE FUNCTION public.normalize_game_team_codes()$ddl$;
    END IF;
    IF 'trg_normalize_game_lineups_team_code' = ANY(missing_triggers) THEN
        EXECUTE $ddl$CREATE TRIGGER trg_normalize_game_lineups_team_code BEFORE INSERT OR UPDATE ON public.game_lineups FOR EACH ROW EXECUTE FUNCTION public.normalize_team_code_column()$ddl$;
    END IF;
    IF 'trg_normalize_game_inning_scores_team_code' = ANY(missing_triggers) THEN
        EXECUTE $ddl$CREATE TRIGGER trg_normalize_game_inning_scores_team_code BEFORE INSERT OR UPDATE ON public.game_inning_scores FOR EACH ROW EXECUTE FUNCTION public.normalize_team_code_column()$ddl$;
    END IF;
    IF 'trg_normalize_player_movements_team_code' = ANY(missing_triggers) THEN
        EXECUTE $ddl$CREATE TRIGGER trg_normalize_player_movements_team_code BEFORE INSERT OR UPDATE ON public.player_movements FOR EACH ROW EXECUTE FUNCTION public.normalize_team_code_column()$ddl$;
    END IF;
    IF 'trg_normalize_cheer_post_team_id' = ANY(missing_triggers) THEN
        EXECUTE $ddl$CREATE TRIGGER trg_normalize_cheer_post_team_id BEFORE INSERT OR UPDATE ON public.cheer_post FOR EACH ROW EXECUTE FUNCTION public.normalize_team_id_column()$ddl$;
    END IF;
    IF 'trg_normalize_cheer_battle_votes_team_id' = ANY(missing_triggers) THEN
        EXECUTE $ddl$CREATE TRIGGER trg_normalize_cheer_battle_votes_team_id BEFORE INSERT OR UPDATE ON public.cheer_battle_votes FOR EACH ROW EXECUTE FUNCTION public.normalize_team_id_column()$ddl$;
    END IF;
    IF 'trg_normalize_team_profiles_team_id' = ANY(missing_triggers) THEN
        EXECUTE $ddl$CREATE TRIGGER trg_normalize_team_profiles_team_id BEFORE INSERT OR UPDATE ON public.team_profiles FOR EACH ROW EXECUTE FUNCTION public.normalize_team_id_column()$ddl$;
    END IF;
    IF 'trg_normalize_users_favorite_team' = ANY(missing_triggers) THEN
        EXECUTE $ddl$CREATE TRIGGER trg_normalize_users_favorite_team BEFORE INSERT OR UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.normalize_favorite_team_column()$ddl$;
    END IF;
END;
$migration$;
