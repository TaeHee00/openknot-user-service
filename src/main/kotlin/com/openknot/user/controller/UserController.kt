package com.openknot.user.controller

import com.openknot.user.dto.UserInfoResponse
import com.openknot.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping("/me")
    suspend fun getCurrentUser(
        @RequestParam(value = "id", required = true) id: UUID, // TODO: [DEV] gateway 구현 이후 토큰으로 변경 예정
    ): ResponseEntity<UserInfoResponse> {
        return ResponseEntity.ok(
            UserInfoResponse.fromEntity(userService.getUserById(id)),
        )
    }
}