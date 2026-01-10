package com.nofrillsdb.utils

import com.nofrillsdb.user.exception.UserNotFoundException
import com.nofrillsdb.user.repository.UserRepository
import com.nofrillsdb.users.model.db.User
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class UserUtils (val userRepository: UserRepository) {

fun getUser(@AuthenticationPrincipal jwt: Jwt): User {
        val userId = JWTUtils.getUserId(jwt)
        return userRepository.findById(userId).orElseThrow {
            UserNotFoundException("User $userId not found")
        }
    }


}