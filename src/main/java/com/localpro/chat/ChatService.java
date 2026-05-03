package com.localpro.chat;

import com.localpro.chat.dto.ChatSummaryResponse;
import com.localpro.chat.dto.MessageResponse;
import com.localpro.listing.ServiceListing;
import com.localpro.listing.ServiceListingRepository;
import com.localpro.user.User;
import com.localpro.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ServiceListingRepository listingRepository;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public Chat getOrCreateChat(UUID clientId, UUID providerId, UUID listingId) {
        return chatRepository
                .findByClientIdAndProviderIdAndListingId(clientId, providerId, listingId)
                .orElseGet(() -> {
                    User client = loadUser(clientId);
                    User provider = loadUser(providerId);

                    Chat.ChatBuilder builder = Chat.builder()
                            .client(client)
                            .provider(provider);

                    if (listingId != null) {
                        ServiceListing listing = listingRepository.findById(listingId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                        "Listing not found: " + listingId));
                        builder.listing(listing);
                    }

                    return chatRepository.save(builder.build());
                });
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
        return chatRepository
                .findByClientIdOrProviderIdOrderByLastMessageAtDesc(userId, userId)
                .stream()
                .map(chat -> buildSummary(chat, userId))
                .toList();
    }

    public Message sendMessage(UUID chatId, UUID senderId, String content) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found: " + chatId));

        if (!chat.getClient().getId().equals(senderId)
                && !chat.getProvider().getId().equals(senderId)) {
            throw new AccessDeniedException("User is not a participant in this chat");
        }

        Message message = Message.builder()
                .chat(chat)
                .sender(loadUser(senderId))
                .content(content)
                .build();
        Message saved = messageRepository.save(message);

        chat.setLastMessage(content);
        chat.setLastMessageAt(Instant.now());
        chatRepository.save(chat);

        UUID recipientId = chat.getClient().getId().equals(senderId)
                ? chat.getProvider().getId()
                : chat.getClient().getId();

        messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                "/queue/messages",
                messageMapper.toResponse(saved));

        return saved;
    }

    @Transactional
    public Page<MessageResponse> getMessages(UUID chatId, UUID userId, Pageable pageable) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found: " + chatId));

        if (!chat.getClient().getId().equals(userId)
                && !chat.getProvider().getId().equals(userId)) {
            throw new AccessDeniedException("User is not a participant in this chat");
        }

        messageRepository.markAllAsRead(chatId, userId);

        return messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageable)
                .map(messageMapper::toResponse);
    }

    public void markRead(UUID chatId, UUID userId) {
        messageRepository.markAllAsRead(chatId, userId);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }
}
