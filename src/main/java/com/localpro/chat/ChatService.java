package com.localpro.chat;

import com.localpro.chat.dto.ChatSummaryResponse;
import com.localpro.chat.dto.MessageResponse;
import com.localpro.listing.ServiceListing;
import com.localpro.listing.ServiceListingRepository;
import com.localpro.notification.NotificationService;
import com.localpro.user.User;
import com.localpro.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ServiceListingRepository listingRepository;
    private final MessageMapper messageMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    @Autowired(required = false)
    private NotificationService notificationService;

    public ChatSummaryResponse getOrCreateChat(UUID clientId, UUID providerId, UUID listingId) {
        log.info("=== [ChatService.getOrCreateChat] called by client: {} with provider: {}, listing: {}",
                clientId, providerId, listingId);

        Chat chat = chatRepository
                .findByClientIdAndProviderIdAndListingId(clientId, providerId, listingId)
                .map(existing -> {
                    log.info("Found existing chat: {}", existing.getId());
                    return chatRepository.findByIdWithDetails(existing.getId()).orElse(existing);
                })
                .orElseGet(() -> {
                    User client = loadUser(clientId);
                    User provider = loadUser(providerId);

                    Chat.ChatBuilder builder = Chat.builder()
                            .client(client)
                            .provider(provider);

                    if (listingId != null) {
                        ServiceListing listing = listingRepository.findById(listingId)
                                .orElseThrow(() -> {
                                    log.warn("Listing not found for chat: {}", listingId);
                                    return new EntityNotFoundException("Listing not found: " + listingId);
                                });
                        builder.listing(listing);
                    }

                    Chat newChat;
                    try {
                        newChat = chatRepository.save(builder.build());
                        log.info("Created new chat {} between client {} and provider {}", newChat.getId(), clientId, providerId);
                    } catch (DataIntegrityViolationException e) {
                        // Another concurrent request created the same chat — find and return it
                        log.info("Concurrent chat creation detected for client {} provider {} — returning existing", clientId, providerId);
                        newChat = chatRepository
                                .findByClientIdAndProviderIdAndListingId(clientId, providerId, listingId)
                                .orElseThrow(() -> new IllegalStateException("Chat disappeared after race", e));
                    }
                    return chatRepository.findByIdWithDetails(newChat.getId()).orElse(newChat);
                });
        return buildSummary(chat, clientId);
    }

    public ChatSummaryResponse buildSummary(Chat chat, UUID userId) {
        User otherParty = chat.getClient().getId().equals(userId)
                ? chat.getProvider()
                : chat.getClient();
        int unread = messageRepository.countByChatIdAndIsReadFalseAndSenderIdNot(
                chat.getId(), userId);
        return new ChatSummaryResponse(
                chat.getId(),
                otherParty.getId(),
                otherParty.getName(),
                otherParty.getAvatarUrl(),
                chat.getListing() != null ? chat.getListing().getId() : null,
                chat.getListing() != null ? chat.getListing().getTitle() : null,
                chat.getLastMessage(),
                chat.getLastMessageAt(),
                unread
        );
    }

    @Transactional(readOnly = true)
    public List<ChatSummaryResponse> getChats(UUID userId) {
        log.info("=== [ChatService.getChats] called for user: {}", userId);
        List<ChatSummaryResponse> chats = chatRepository
                .findAllByUserIdWithDetails(userId)
                .stream()
                .map(chat -> buildSummary(chat, userId))
                .toList();
        log.info("Found {} chats for user {}", chats.size(), userId);
        return chats;
    }

    public MessageResponse sendMessage(UUID chatId, UUID senderId, String content) {
        log.info("=== [ChatService.sendMessage] called for chat: {} by sender: {}",
                chatId, senderId);

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> {
                    log.warn("Chat not found: {}", chatId);
                    return new EntityNotFoundException("Chat not found: " + chatId);
                });

        if (!chat.getClient().getId().equals(senderId)
                && !chat.getProvider().getId().equals(senderId)) {
            log.warn("User {} is not a participant in chat {}", senderId, chatId);
            throw new AccessDeniedException("User is not a participant in this chat");
        }

        Message message = Message.builder()
                .chat(chat)
                .sender(loadUser(senderId))
                .content(content)
                .build();
        // @Generated on createdAt causes Hibernate to re-select after INSERT automatically
        Message saved = messageRepository.save(message);
        log.info("User {} sent message to chat {}", senderId, chatId);

        chat.setLastMessage(content);
        chat.setLastMessageAt(Instant.now());
        chatRepository.save(chat);

        UUID recipientId = chat.getClient().getId().equals(senderId)
                ? chat.getProvider().getId()
                : chat.getClient().getId();

        MessageResponse response = messageMapper.toResponse(saved);
        messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages", response);

        // Push notification for offline recipients — only when Firebase is configured
        if (notificationService != null) {
            User recipient = loadUser(recipientId);
            if (recipient.getFcmToken() != null && !recipient.getFcmToken().isBlank()) {
                notificationService.sendMessageNotification(
                        recipient.getFcmToken(), chatId, saved.getSender().getName());
            }
        }

        return response;
    }

    @Transactional
    public Page<MessageResponse> getMessages(UUID chatId, UUID userId, Pageable pageable) {
        log.info("=== [ChatService.getMessages] called for chat: {} by user: {}, page: {}",
                chatId, userId, pageable.getPageNumber());

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> {
                    log.warn("Chat not found: {}", chatId);
                    return new EntityNotFoundException("Chat not found: " + chatId);
                });

        if (!chat.getClient().getId().equals(userId)
                && !chat.getProvider().getId().equals(userId)) {
            log.warn("User {} is not a participant in chat {}", userId, chatId);
            throw new AccessDeniedException("User is not a participant in this chat");
        }

        messageRepository.markAllAsRead(chatId, userId);
        Page<MessageResponse> messages = messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageable)
                .map(messageMapper::toResponse);
        log.info("Retrieved {} messages from chat {}", messages.getNumberOfElements(), chatId);
        return messages;
    }

    public void markRead(UUID chatId, UUID userId) {
        log.info("=== [ChatService.markRead] called for chat: {} by user: {}", chatId, userId);
        messageRepository.markAllAsRead(chatId, userId);
        log.info("Marked messages as read for user {} in chat {}", userId, chatId);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }
}
