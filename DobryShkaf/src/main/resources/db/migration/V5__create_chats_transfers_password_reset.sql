CREATE TABLE chats (
    id           UUID        PRIMARY KEY,
    item_id      UUID        NOT NULL REFERENCES items (id),
    requester_id UUID        NOT NULL REFERENCES users (id),
    last_seq     BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    -- Один диалог на пару «объявление и заинтересованный пользователь»
    CONSTRAINT uq_chats_item_requester UNIQUE (item_id, requester_id)
);

CREATE INDEX idx_chats_requester_id ON chats (requester_id);

CREATE TABLE messages (
    id         UUID          PRIMARY KEY,
    chat_id    UUID          NOT NULL REFERENCES chats (id),
    sender_id  UUID          NOT NULL REFERENCES users (id),
    seq        BIGINT        NOT NULL,
    text       VARCHAR(2000) NOT NULL,
    request_id UUID          NOT NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_messages_chat_seq UNIQUE (chat_id, seq),
    -- Повтор отправки с тем же идентификатором запроса не создаёт второе сообщение
    CONSTRAINT uq_messages_request UNIQUE (chat_id, sender_id, request_id)
);

CREATE TABLE transfers (
    id                        UUID        PRIMARY KEY,
    item_id                   UUID        NOT NULL REFERENCES items (id),
    giver_id                  UUID        NOT NULL REFERENCES users (id),
    receiver_id               UUID        NOT NULL REFERENCES users (id),
    chat_id                   UUID        NOT NULL REFERENCES chats (id),
    status                    VARCHAR(32) NOT NULL,
    giver_confirmed_at        TIMESTAMPTZ,
    receiver_confirmed_at     TIMESTAMPTZ,
    cancellation_requested_by VARCHAR(16),
    completed_at              TIMESTAMPTZ,
    version                   BIGINT      NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_transfers_status CHECK (status IN (
        'REQUESTED', 'RESERVED', 'AWAITING_CONFIRMATION', 'CANCELLATION_REQUESTED',
        'COMPLETED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_transfers_cancellation_role CHECK (cancellation_requested_by IN ('GIVER', 'RECEIVER')),
    CONSTRAINT chk_transfers_participants CHECK (giver_id <> receiver_id),
    CONSTRAINT chk_transfers_completed_at CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);

CREATE INDEX idx_transfers_item_id ON transfers (item_id);
CREATE INDEX idx_transfers_giver_id ON transfers (giver_id);
CREATE INDEX idx_transfers_receiver_id ON transfers (receiver_id);

-- Один резерв на вещь: даже при гонке двух выборов база не даст второй
CREATE UNIQUE INDEX uq_transfers_item_reservation ON transfers (item_id)
    WHERE status IN ('RESERVED', 'AWAITING_CONFIRMATION', 'CANCELLATION_REQUESTED', 'COMPLETED');

-- Один активный запрос от конкретного получателя
CREATE UNIQUE INDEX uq_transfers_active_request ON transfers (item_id, receiver_id)
    WHERE status IN ('REQUESTED', 'RESERVED', 'AWAITING_CONFIRMATION', 'CANCELLATION_REQUESTED');

CREATE TABLE transfer_events (
    id          UUID        PRIMARY KEY,
    transfer_id UUID        NOT NULL REFERENCES transfers (id),
    actor_id    UUID        NOT NULL REFERENCES users (id),
    command     VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status   VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_transfer_events_transfer_id ON transfer_events (transfer_id);

CREATE TABLE password_reset_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
