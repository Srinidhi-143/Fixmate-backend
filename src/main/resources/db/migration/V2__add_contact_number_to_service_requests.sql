-- Older development runs used Hibernate ddl-auto=update and may already have
-- this column without a V2 history entry. Preserve that column and its data.
SET @contact_number_migration = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE service_requests ADD COLUMN contact_number VARCHAR(20) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'service_requests'
      AND column_name = 'contact_number'
);
PREPARE contact_number_statement FROM @contact_number_migration;
EXECUTE contact_number_statement;
DEALLOCATE PREPARE contact_number_statement;
