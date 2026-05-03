package com.localpro.user;

import com.localpro.user.dto.UpdateProfileRequest;
import com.localpro.user.dto.UserResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateProfileRequest request, @MappingTarget User user);
}
