-- V92: Normalize users.unique_id values to canonical UUID format.
-- Some legacy records store UUID without hyphens (32 hex chars), which fails UUID parsing.

UPDATE users
   SET unique_id =
       LOWER(
           REGEXP_REPLACE(
               unique_id,
               '^([0-9A-Fa-f]{8})([0-9A-Fa-f]{4})([0-9A-Fa-f]{4})([0-9A-Fa-f]{4})([0-9A-Fa-f]{12})$',
               '\1-\2-\3-\4-\5'
           )
       )
 WHERE unique_id IS NOT NULL
   AND REGEXP_LIKE(unique_id, '^[0-9A-Fa-f]{32}$');
