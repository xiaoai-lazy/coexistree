# Migration V10: Add Conversation Metadata

## Overview
This migration adds three new columns to the `conversations` table to support the session management enhancement feature.

## Changes

### New Columns
1. **version** (BIGINT, DEFAULT 0)
   - Purpose: Optimistic locking for concurrent update control
   - Used by JPA's `@Version` annotation to prevent race conditions

2. **message_count** (INT, DEFAULT 0)
   - Purpose: Track the number of messages in each conversation
   - Automatically updated when messages are added/removed
   - Used to enforce the 200-message limit per conversation

3. **total_tokens** (BIGINT, DEFAULT 0)
   - Purpose: Track total token usage for each conversation
   - Accumulates input and output tokens across all messages
   - Used for cost monitoring and analytics

### Data Initialization
The migration includes logic to initialize `message_count` for existing conversations:
```sql
UPDATE conversations c
SET message_count = (
    SELECT COUNT(*) 
    FROM messages m 
    WHERE m.conversation_id = c.conversation_id
);
```

This ensures that existing conversations have accurate message counts after the migration.

## Requirements Addressed
- **Requirement 10.1**: Conversation metadata tracking (message count)
- **Requirement 10.2**: Token usage tracking for cost monitoring

## Design Reference
See `.kiro/specs/session-management-enhancement/design.md`, Section "数据库迁移"

## Testing
The migration has been validated:
1. ✅ Entity definition matches migration schema
2. ✅ Integration tests pass with new columns
3. ✅ SQL syntax is compatible with H2 database

## Rollback
If rollback is needed, execute:
```sql
ALTER TABLE conversations DROP COLUMN version;
ALTER TABLE conversations DROP COLUMN message_count;
ALTER TABLE conversations DROP COLUMN total_tokens;
```

## Notes
- The migration is idempotent-safe (uses DEFAULT values)
- Existing conversations will have `version=0`, `message_count=<actual count>`, `total_tokens=0`
- The migration runs automatically when the application starts (Flyway enabled in dev/prod profiles)
