-- ============================================================
-- V3: Create conversations and conversation_participants tables
-- ============================================================

CREATE TABLE conversations (
    id              UUID            PRIMARY KEY,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ     NULL,
    ended_reason    VARCHAR(30)     NULL,

    CONSTRAINT chk_conversations_status    CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT chk_conversations_end_reason CHECK (
        ended_reason IS NULL OR
        ended_reason IN ('NEXT', 'DISCONNECT', 'LEAVE', 'ADMIN_ACTION')
    )
);

CREATE TABLE conversation_participants (
    conversation_id UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    joined_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    left_at         TIMESTAMPTZ     NULL,

    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_cp_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_user         FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE
);

CREATE INDEX idx_conversations_status      ON conversations (status);
CREATE INDEX idx_cp_user_id                ON conversation_participants (user_id);
CREATE INDEX idx_cp_conversation_user      ON conversation_participants (conversation_id, user_id);
