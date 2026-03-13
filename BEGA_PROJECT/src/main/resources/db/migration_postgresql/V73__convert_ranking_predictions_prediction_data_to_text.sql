-- V73: Convert ranking_predictions.prediction_data to text for cross-DB compatibility.
-- Existing json/jsonb values are preserved as JSON strings.

DO $$
BEGIN
    IF to_regclass('public.ranking_predictions') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'ranking_predictions'
           AND column_name = 'prediction_data'
           AND data_type IN ('json', 'jsonb')
    ) THEN
        EXECUTE '
            ALTER TABLE public.ranking_predictions
            ALTER COLUMN prediction_data TYPE text
            USING prediction_data::text
        ';
    END IF;
END $$;
