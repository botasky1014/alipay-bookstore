package com.example.bookstore.auth;

public record UserDto(Long id, String username, Role role) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getRole());
    }
}
