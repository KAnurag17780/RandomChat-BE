-- ============================================================
-- V4: Create messages table
-- ============================================================

CREATE TABLE messages (
    id                  UUID            PRIMARY KEY,
    conversation_id     UUID            NOT NULL,
    sender_id           UUID            NOT NULL,
    client_message_id   UUID            NULL,
    content             TEXT            NOT NULL,
    message_type        VARCHAR(20)     NOT NULL DEFAULT 'TEXT',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender       FOREIGN KEY (sender_id)       REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT chk_messages_type        CHECK (message_type IN ('TEXT')),
    CONSTRAINT chk_messages_content_nonempty CHECK (char_length(content) > 0)
);

-- Idempotency: one unique client message per sender
CREATE UNIQUE INDEX uq_messages_client_id
    ON messages (sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

-- Cursor-based pagination index
CREATE INDEX idx_messages_conversation_time
    ON messages (conversation_id, created_at, id);
