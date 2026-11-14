package ru.sfustudio.dobryshkaf.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sfustudio.dobryshkaf.domain.Chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    Optional<Chat> findByItemIdAndRequesterId(UUID itemId, UUID requesterId);

    /** Блокировка строки чата выдаёт сообщениям номера по порядку и делает повтор отправки безопасным. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Chat c where c.id = :id")
    Optional<Chat> findForUpdateById(@Param("id") UUID id);

    @Query("""
            select c from Chat c join fetch c.item i join fetch c.requester
            where c.requester.id = :userId or i.owner.id = :userId
            order by c.updatedAt desc""")
    List<Chat> findAllForParticipant(@Param("userId") UUID userId);

    @Query("""
            select case when count(c) > 0 then true else false end from Chat c
            where c.id = :chatId and (c.requester.id = :userId or c.item.owner.id = :userId)""")
    boolean isParticipant(@Param("chatId") UUID chatId, @Param("userId") UUID userId);
}
