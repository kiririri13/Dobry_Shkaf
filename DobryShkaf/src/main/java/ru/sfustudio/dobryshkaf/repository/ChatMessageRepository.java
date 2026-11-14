package ru.sfustudio.dobryshkaf.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sfustudio.dobryshkaf.domain.ChatMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Optional<ChatMessage> findByChatIdAndSenderIdAndRequestId(UUID chatId, UUID senderId, UUID requestId);

    List<ChatMessage> findByChatIdAndSeqLessThanOrderBySeqDesc(UUID chatId, long seq, Pageable pageable);

    List<ChatMessage> findByChatIdAndSeqGreaterThanOrderBySeqAsc(UUID chatId, long seq, Pageable pageable);

    Optional<ChatMessage> findFirstByChatIdOrderBySeqDesc(UUID chatId);

    long countByChatIdAndSenderIdNotAndReadAtIsNull(UUID chatId, UUID senderId);

    /** Отмечает прочитанными чужие сообщения до указанного номера включительно. */
    @Modifying
    @Query("""
            update ChatMessage m set m.readAt = :now, m.updatedAt = :now
            where m.chat.id = :chatId and m.sender.id <> :readerId and m.seq <= :upToSeq and m.readAt is null""")
    int markRead(@Param("chatId") UUID chatId, @Param("readerId") UUID readerId,
                 @Param("upToSeq") long upToSeq, @Param("now") Instant now);
}
