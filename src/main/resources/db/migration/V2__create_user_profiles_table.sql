-- ============================================================
-- V2: Create user_profiles table
-- ============================================================
CREATE TABLE user_profiles (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL,
    username        VARCHAR(30)     NOT NULL,
    display_name    VARCHAR(50)     NULL,
    bio             TEXT            NULL,
    avatar_url      TEXT            NULL,
    interests       JSONB           NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_profiles_user_id UNIQUE (user_id),
    CONSTRAINT uq_profiles_username UNIQUE (username),
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_profiles_user_id ON user_profiles (user_id);
CREATE INDEX idx_profiles_username ON user_profiles (username);
