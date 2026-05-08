package com.localpro.chat;

import com.localpro.chat.dto.ChatSummaryResponse;
import com.localpro.chat.dto.MessageResponse;
import com.localpro.listing.ServiceListingRepository;
import com.localpro.user.User;
import com.localpro.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatRepository chatRepository;
    @Mock MessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock ServiceListingRepository listingRepository;
    @Mock MessageMapper messageMapper;
    @Mock SimpMessageSendingOperations messagingTemplate;
    @Mock EntityManager entityManager;
    @InjectMocks ChatService chatService;

    UUID clientId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    UUID chatId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();

    User client;
    User provider;
    Chat chat;

    @BeforeEach
    void setUp() {
        client = User.builder().id(clientId).firebaseUid("c").email("c@test.com").name("Client").build();
        provider = User.builder().id(providerId).firebaseUid("p").email("p@test.com").name("Provider").build();
        chat = Chat.builder().id(chatId).client(client).provider(provider).build();
        // @PersistenceContext fields are not injected by @InjectMocks constructor injection
        ReflectionTestUtils.setField(chatService, "entityManager", entityManager);
    }

    @Test
    void getOrCreateChat_noExisting_createsNewChat() {
        when(chatRepository.findByClientIdAndProviderIdAndListingId(clientId, providerId, null))
                .thenReturn(Optional.empty());
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(userRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(chatRepository.save(any())).thenReturn(chat);
        when(chatRepository.findByIdWithDetails(chatId)).thenReturn(Optional.of(chat));
        when(messageRepository.countByChatIdAndIsReadFalseAndSenderIdNot(chatId, clientId)).thenReturn(0);

        ChatSummaryResponse result = chatService.getOrCreateChat(clientId, providerId, null);

        assertThat(result.id()).isEqualTo(chatId);
        verify(chatRepository).save(any());
    }

    @Test
    void getOrCreateChat_existingChat_returnsExistingWithoutCreating() {
        when(chatRepository.findByClientIdAndProviderIdAndListingId(clientId, providerId, null))
                .thenReturn(Optional.of(chat));
        when(chatRepository.findByIdWithDetails(chatId)).thenReturn(Optional.of(chat));
        when(messageRepository.countByChatIdAndIsReadFalseAndSenderIdNot(chatId, clientId)).thenReturn(2);

        ChatSummaryResponse result = chatService.getOrCreateChat(clientId, providerId, null);

        assertThat(result.unreadCount()).isEqualTo(2);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void sendMessage_asClient_savesAndBroadcasts() {
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        Message saved = Message.builder().id(messageId).chat(chat).sender(client).content("Hello").build();
        when(messageRepository.save(any())).thenReturn(saved);
        when(chatRepository.save(any())).thenReturn(chat);
        MessageResponse dto = new MessageResponse(messageId, chatId, clientId, "Client", null, "Hello", false, null);
        when(messageMapper.toResponse(saved)).thenReturn(dto);

        MessageResponse result = chatService.sendMessage(chatId, clientId, "Hello");

        assertThat(result.content()).isEqualTo("Hello");
        verify(messagingTemplate).convertAndSendToUser(eq(providerId.toString()), any(), eq(dto));
        verify(chatRepository).save(any());
    }

    @Test
    void sendMessage_asProvider_broadcastsToClient() {
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(userRepository.findById(providerId)).thenReturn(Optional.of(provider));
        Message saved = Message.builder().id(messageId).chat(chat).sender(provider).content("Hi back").build();
        when(messageRepository.save(any())).thenReturn(saved);
        when(chatRepository.save(any())).thenReturn(chat);
        MessageResponse dto = new MessageResponse(messageId, chatId, providerId, "Provider", null, "Hi back", false, null);
        when(messageMapper.toResponse(saved)).thenReturn(dto);

        chatService.sendMessage(chatId, providerId, "Hi back");

        verify(messagingTemplate).convertAndSendToUser(eq(clientId.toString()), any(), eq(dto));
    }

    @Test
    void sendMessage_nonParticipant_throwsAccessDeniedException() {
        UUID outsiderId = UUID.randomUUID();
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        assertThatThrownBy(() -> chatService.sendMessage(chatId, outsiderId, "Hacked"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sendMessage_chatNotFound_throwsEntityNotFoundException() {
        when(chatRepository.findById(chatId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(chatId, clientId, "Hello"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getChats_returnsAllChatsForUser() {
        Chat otherChat = Chat.builder().id(UUID.randomUUID()).client(provider).provider(client).build();
        when(chatRepository.findAllByUserIdWithDetails(clientId)).thenReturn(List.of(chat, otherChat));
        when(messageRepository.countByChatIdAndIsReadFalseAndSenderIdNot(any(), eq(clientId))).thenReturn(0);

        List<ChatSummaryResponse> result = chatService.getChats(clientId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getMessages_authorizedUser_returnsPageAndMarksRead() {
        Message msg = Message.builder().id(messageId).chat(chat).sender(provider).content("Yo").build();
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(messageRepository.findByChatIdOrderByCreatedAtDesc(eq(chatId), any()))
                .thenReturn(new PageImpl<>(List.of(msg)));
        MessageResponse dto = new MessageResponse(messageId, chatId, providerId, "Provider", null, "Yo", false, null);
        when(messageMapper.toResponse(msg)).thenReturn(dto);

        Page<MessageResponse> result = chatService.getMessages(chatId, clientId, PageRequest.of(0, 30));

        assertThat(result.getContent()).hasSize(1);
        verify(messageRepository).markAllAsRead(chatId, clientId);
    }

    @Test
    void getMessages_unauthorized_throwsAccessDeniedException() {
        UUID outsiderId = UUID.randomUUID();
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        assertThatThrownBy(() -> chatService.getMessages(chatId, outsiderId, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void buildSummary_clientAskingAboutChat_returnsProviderAsOtherParty() {
        when(messageRepository.countByChatIdAndIsReadFalseAndSenderIdNot(chatId, clientId)).thenReturn(3);

        ChatSummaryResponse summary = chatService.buildSummary(chat, clientId);

        assertThat(summary.otherPartyId()).isEqualTo(providerId);
        assertThat(summary.otherPartyName()).isEqualTo("Provider");
        assertThat(summary.unreadCount()).isEqualTo(3);
    }
}
