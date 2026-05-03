package com.localpro.chat;

import com.localpro.chat.dto.MessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    @Mapping(target = "chatId", source = "chat.id")
    @Mapping(target = "senderId", source = "sender.id")
    @Mapping(target = "senderName", source = "sender.name")
    @Mapping(target = "senderAvatarUrl", source = "sender.avatarUrl")
    MessageResponse toResponse(Message message);
}
