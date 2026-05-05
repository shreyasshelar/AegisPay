package com.aegispay.user.domain.mapper;

import com.aegispay.user.domain.dto.UserResponse;
import com.aegispay.user.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "email",  source = "email",  qualifiedByName = "maskEmail")
    @Mapping(target = "phone",  source = "phone",  qualifiedByName = "maskPhone")
    @Mapping(target = "name",   expression = "java(buildName(user))")
    UserResponse toResponse(User user);

    @Named("buildName")
    static String buildName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last  = user.getLastName()  != null ? user.getLastName().trim()  : "";
        String full  = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    @Named("maskEmail")
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String local = email.substring(0, email.indexOf('@'));
        String domain = email.substring(email.indexOf('@'));
        if (local.length() <= 1) return "*" + domain;
        return local.charAt(0) + "***" + domain;
    }

    @Named("maskPhone")
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return phone;
        return phone.substring(0, phone.length() - 4).replaceAll("\\d", "*")
                + phone.substring(phone.length() - 4);
    }
}
