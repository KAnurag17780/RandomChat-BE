-- ============================================================
-- V5: Create moderation tables (user_blocks, user_reports)
-- ============================================================

CREATE TABLE user_blocks (
    id              UUID            PRIMARY KEY,
    blocker_id      UUID            NOT NULL,
    blocked_user_id UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_blocks          UNIQUE (blocker_id, blocked_user_id),
    CONSTRAINT chk_user_blocks_self    CHECK (blocker_id <> blocked_user_id),
    CONSTRAINT fk_blocks_blocker       FOREIGN KEY (blocker_id)      REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_blocks_blocked_user  FOREIGN KEY (blocked_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_blocks_blocker   ON user_blocks (blocker_id);
CREATE INDEX idx_blocks_blocked   ON user_blocks (blocked_user_id);

CREATE TABLE user_reports (
    id                  UUID            PRIMARY KEY,
    reporter_id         UUID            NOT NULL,
    reported_user_id    UUID            NOT NULL,
    conversation_id     UUID            NULL,
    reason              VARCHAR(50)     NOT NULL,
    description         TEXT            NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_reports_self     CHECK (reporter_id <> reported_user_id),
    CONSTRAINT chk_reports_status   CHECK (status IN ('PENDING', 'REVIEWED', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT fk_reports_reporter  FOREIGN KEY (reporter_id)      REFERENCES users(id)          ON DELETE SET NULL,
    CONSTRAINT fk_reports_reported  FOREIGN KEY (reported_user_id) REFERENCES users(id)          ON DELETE CASCADE,
    CONSTRAINT fk_reports_convo     FOREIGN KEY (conversation_id)  REFERENCES conversations(id)  ON DELETE SET NULL
);

CREATE INDEX idx_reports_reporter        ON user_reports (reporter_id);
CREATE INDEX idx_reports_reported_user   ON user_reports (reported_user_id);
CREATE INDEX idx_reports_status          ON user_reports (status);
