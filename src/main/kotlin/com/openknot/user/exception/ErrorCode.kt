package com.openknot.user.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
) {
    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER.001"),

    // System
    INVALID_ERROR_CODE(HttpStatus.BAD_REQUEST, "SYSTEM.001")
}