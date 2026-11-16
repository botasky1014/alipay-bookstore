package com.example.bookstore.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.common.ApiException;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /** Login only checks that the username exists; there are no passwords (design 6.1). */
    public UserDto login(String username) {
        return users.findByUsername(username.trim())
                .map(UserDto::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Username does not exist"));
    }

    public User require(Long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
