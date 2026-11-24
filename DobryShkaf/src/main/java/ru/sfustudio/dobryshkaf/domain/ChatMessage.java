package ru.sfustudio.dobryshkaf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    public static final int MAX_TEXT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false, updatable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @Column(nullable = false, updatable = false)
    private long seq;

    @Column(nullable = false, length = MAX_TEXT_LENGTH, updatable = false)
    private String text;

    @Embedded
    private ChatAttachment attachment;

    /** Идентификатор запроса от клиента: по нему повтор отправки возвращает это же сообщение. */
    @Column(nullable = false, updatable = false)
    private UUID requestId;

    private Instant readAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /** Номер сообщения выдаёт чат, поэтому создавать его можно только через {@link #send}. */
    private ChatMessage(Chat chat, User sender, long seq, String text, UUID requestId, ChatAttachment attachment) {
        this.chat = chat;
        this.sender = sender;
        this.seq = seq;
        this.text = text;
        this.requestId = requestId;
        this.attachment = attachment;
    }

    public static ChatMessage send(Chat chat, User sender, String text, UUID requestId) {
        return send(chat, sender, text, requestId, null);
    }

    public static ChatMessage send(Chat chat, User sender, String text, UUID requestId, ChatAttachment attachment) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        String content = text == null || text.isBlank() ? "" : text;
        if ((content.isEmpty() && attachment == null) || (text != null && text.length() > MAX_TEXT_LENGTH)) {
            throw new IllegalArgumentException("text must be non-blank and at most " + MAX_TEXT_LENGTH + " characters");
        }
        if (!chat.isParticipant(sender.getId())) {
            throw new IllegalArgumentException("sender must be a chat participant");
        }
        return new ChatMessage(chat, sender, chat.nextSeq(), content, requestId, attachment);
    }
}
