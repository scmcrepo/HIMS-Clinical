-- V048__remove_duplicate_sequences.sql
-- Removes duplicate sequence generators for the same document type, keeping only the most recently created or highest id one.

DELETE FROM sequence_generators
WHERE id NOT IN (
    SELECT DISTINCT ON (document_type) id
    FROM sequence_generators
    ORDER BY document_type, created_at DESC, id DESC
);
