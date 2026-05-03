package com.localpro.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);

    int countByChatIdAndIsReadFalseAndSenderIdNot(UUID chatId, UUID userId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.chat.id = :chatId AND m.sender.id != :userId")
    void markAllAsRead(@Param("chatId") UUID chatId, @Param("userId") UUID userId);
}
