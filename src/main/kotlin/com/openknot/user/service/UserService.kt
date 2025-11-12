package com.openknot.user.service

import com.openknot.user.entity.User
import com.openknot.user.exception.BusinessException
import com.openknot.user.exception.ErrorCode
import com.openknot.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional(readOnly = true)
@Service
class UserService(
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    suspend fun getUserById(id: UUID): User {
        return userRepository.findById(id)?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
    }
}