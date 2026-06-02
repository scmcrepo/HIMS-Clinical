package com.hms.application.bulkupload;

import java.util.List;

/**
 * Summary of a bulk import operation.
 *
 * @param entityType   the type of entity that was imported
 * @param totalRows    total rows in the CSV (excluding header)
 * @param createdCount rows successfully created
 * @param skippedCount rows skipped (e.g. duplicate key)
 * @param errorCount   rows that failed with errors
 * @param errors       up to 50 row-level error messages (rowNumber: message)
 */
public record ImportResult(
    String entityType,
    int totalRows,
    int createdCount,
    int skippedCount,
    int errorCount,
    List<String> errors
) {}
