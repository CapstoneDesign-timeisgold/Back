-- Read-only MariaDB audit. Select the intended database before executing.
-- All checks should report PASS. This file does not create or alter constraints.
SELECT DATABASE() AS checked_database, VERSION() AS database_version;

SELECT 'transactional_tables' AS check_name,
       IF(COUNT(*) = 3, 'PASS', 'FAIL') AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('promise', 'site_user', 'money_record') AND engine = 'InnoDB';

SELECT 'settlement_columns' AS check_name,
       IF(COUNT(*) = 5, 'PASS', 'FAIL') AS result
FROM information_schema.columns
WHERE table_schema = DATABASE() AND (
    (table_name = 'promise' AND column_name = 'settlement_result' AND data_type IN ('text', 'mediumtext', 'longtext'))
 OR (table_name = 'promise' AND column_name = 'is_settled' AND is_nullable = 'NO')
 OR (table_name = 'money_record' AND column_name IN ('promise_id', 'user_id') AND data_type = 'bigint')
 OR (table_name = 'money_record' AND column_name = 'is_penalty' AND is_nullable = 'NO')
);

-- Verify columns and uniqueness, rather than trusting only the index name.
SELECT 'unique_ledger_index' AS check_name,
       IF(COUNT(*) >= 1, 'PASS', 'FAIL') AS result
FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'money_record' AND non_unique = 0
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'promise_id,user_id,is_penalty'
       AND SUM(sub_part IS NOT NULL) = 0
) AS matching_indexes;

SELECT 'duplicate_ledger_groups' AS check_name,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS result, COUNT(*) AS duplicate_groups
FROM (
    SELECT promise_id, user_id, is_penalty
    FROM money_record WHERE promise_id IS NOT NULL
    GROUP BY promise_id, user_id, is_penalty HAVING COUNT(*) > 1
) AS duplicates;

-- A null promise_id is permitted for historical records. New settlement entries need a user.
SELECT 'identified_ledger_rows_have_user' AS check_name,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS result, COUNT(*) AS invalid_rows
FROM money_record WHERE promise_id IS NOT NULL AND user_id IS NULL;
