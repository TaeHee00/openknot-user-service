package com.openknot.user.controller

import com.ninjasquad.springmockk.MockkBean
import com.openknot.user.entity.User
import com.openknot.user.exception.BusinessException
import com.openknot.user.exception.ErrorCode
import com.openknot.user.repository.UserRepository
import com.openknot.user.service.UserService
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.LocalDateTime
import java.util.*

/**
 * UserController 통합 테스트
 *
 * 테스트 전략:
 * - @WebFluxTest를 사용하여 Controller 레이어만 로드 (빠른 테스트)
 * - UserService를 MockK로 모킹
 * - WebTestClient로 HTTP 요청/응답 검증
 * - 성공 케이스와 실패 케이스 모두 테스트
 * - ExceptionHandler의 동작도 함께 검증
 */
@WebFluxTest(
    controllers = [UserController::class],
    excludeAutoConfiguration = [
        R2dbcAutoConfiguration::class,
        R2dbcDataAutoConfiguration::class,
        R2dbcRepositoriesAutoConfiguration::class,
    ],
)
@Import(
    com.openknot.user.handler.ExceptionHandler::class,
    com.openknot.user.converter.StringUuidConverter::class,
    UserControllerTest.TestConfig::class,
)
@ActiveProfiles("test")
@DisplayName("UserController 통합 테스트")
class UserControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun r2dbcEntityTemplate(): R2dbcEntityTemplate = mockk(relaxed = true)

        @Bean
        fun r2dbcMappingContext(): R2dbcMappingContext = R2dbcMappingContext()
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean(relaxUnitFun = true)
    private lateinit var userRepository: UserRepository

    @Test
    @DisplayName("GET /users/me - 유저가 존재할 때 200 OK와 유저 정보를 반환한다")
    fun `given existing user id, when get current user, then should return 200 with user info`() {
        // given: 존재하는 유저 ID와 해당 유저 엔티티
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            email = "test@example.com",
            password = "hashedPassword123",
            name = "테스트 유저",
            profileImageUrl = "https://example.com/profile.jpg",
            description = "테스트 설명",
            githubLink = "https://github.com/testuser",
            createdAt = LocalDateTime.of(2025, 1, 1, 0, 0, 0),
            modifiedAt = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        )

        coEvery { userService.getUserById(userId) } returns user

        // when & then: GET /users/me 요청 시 200 OK와 유저 정보가 반환되어야 한다
        webTestClient.get()
            .uri("/users/me?id={id}", userId.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.email").isEqualTo("test@example.com")
            .jsonPath("$.name").isEqualTo("테스트 유저")
            .jsonPath("$.profileImageUrl").isEqualTo("https://example.com/profile.jpg")
            .jsonPath("$.description").isEqualTo("테스트 설명")
            .jsonPath("$.githubLink").isEqualTo("https://github.com/testuser")
            .jsonPath("$.createdAt").isNotEmpty
            .jsonPath("$.password").doesNotExist()  // 비밀번호는 응답에 포함되지 않아야 함

        // verify: userService.getUserById가 호출되었는지 검증
        coVerify(exactly = 1) { userService.getUserById(userId) }
    }

    @Test
    @DisplayName("GET /users/me - 유저가 존재하지 않을 때 404 NOT_FOUND를 반환한다")
    fun `given non-existing user id, when get current user, then should return 404`() {
        // given: 존재하지 않는 유저 ID
        val nonExistingUserId = UUID.randomUUID()

        coEvery { userService.getUserById(nonExistingUserId) } throws BusinessException(ErrorCode.USER_NOT_FOUND)

        // when & then: GET /users/me 요청 시 404와 에러 응답이 반환되어야 한다
        webTestClient.get()
            .uri("/users/me?id={id}", nonExistingUserId.toString())
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.code").isEqualTo("USER.001")
            .jsonPath("$.message").isEqualTo("유저를 찾을 수 없습니다.")

        // verify: userService.getUserById가 호출되었는지 검증
        coVerify(exactly = 1) { userService.getUserById(nonExistingUserId) }
    }

    @Test
    @DisplayName("GET /users/me - 잘못된 UUID 형식일 때 400 BAD_REQUEST를 반환한다")
    fun `given invalid uuid format, when get current user, then should return 400`() {
        // given: 잘못된 UUID 형식
        val invalidUuidString = "invalid-uuid-format"

        // when & then: GET /users/me 요청 시 400과 에러 응답이 반환되어야 한다
        webTestClient.get()
            .uri("/users/me?id={id}", invalidUuidString)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.error").isEqualTo("Bad Request")

        // verify: userService.getUserById는 호출되지 않아야 함 (Converter에서 예외 발생)
        coVerify(exactly = 0) { userService.getUserById(any()) }
    }

    @Test
    @DisplayName("GET /users/me - id 파라미터가 없을 때 400 BAD_REQUEST를 반환한다")
    fun `given missing id parameter, when get current user, then should return 400`() {
        // when & then: id 파라미터 없이 GET /users/me 요청 시 400이 반환되어야 한다
        webTestClient.get()
            .uri("/users/me")
            .exchange()
            .expectStatus().isBadRequest

        // verify: userService.getUserById는 호출되지 않아야 함
        coVerify(exactly = 0) { userService.getUserById(any()) }
    }

    @Test
    @DisplayName("GET /users/me - 선택적 필드가 null인 유저도 정상적으로 반환한다")
    fun `given user with null optional fields, when get current user, then should return 200 with null fields`() {
        // given: 선택적 필드가 null인 유저
        val userId = UUID.randomUUID()
        val userWithNullFields = User(
            id = userId,
            email = "minimal@example.com",
            password = "hashedPassword456",
            name = "미니멀 유저",
            profileImageUrl = null,
            description = null,
            githubLink = null,
            createdAt = LocalDateTime.of(2025, 1, 1, 0, 0, 0),
            modifiedAt = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        )

        coEvery { userService.getUserById(userId) } returns userWithNullFields

        // when & then: GET /users/me 요청 시 200 OK와 유저 정보가 반환되어야 한다
        webTestClient.get()
            .uri("/users/me?id={id}", userId.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.email").isEqualTo("minimal@example.com")
            .jsonPath("$.name").isEqualTo("미니멀 유저")
            .jsonPath("$.profileImageUrl").isEqualTo(null)
            .jsonPath("$.description").isEqualTo(null)
            .jsonPath("$.githubLink").isEqualTo(null)
            .jsonPath("$.createdAt").isNotEmpty

        // verify: userService.getUserById가 호출되었는지 검증
        coVerify(exactly = 1) { userService.getUserById(userId) }
    }

    @Test
    @DisplayName("GET /users/me - 최소값 UUID로 유저를 정상적으로 조회한다")
    fun `given minimum uuid, when get current user, then should return 200`() {
        // given: 최소값 UUID (모든 비트 0)
        val minUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
        val user = User(
            id = minUuid,
            email = "min@example.com",
            password = "password",
            name = "Min User",
            createdAt = LocalDateTime.now(),
            modifiedAt = LocalDateTime.now()
        )

        coEvery { userService.getUserById(minUuid) } returns user

        // when & then: GET /users/me 요청 시 200 OK가 반환되어야 한다
        webTestClient.get()
            .uri("/users/me?id={id}", minUuid.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.email").isEqualTo("min@example.com")

        // verify: userService.getUserById가 호출되었는지 검증
        coVerify(exactly = 1) { userService.getUserById(minUuid) }
    }

    @Test
    @DisplayName("GET /users/me - 대문자 UUID 문자열로도 정상적으로 조회한다")
    fun `given uppercase uuid string, when get current user, then should return 200`() {
        // given: 대문자 UUID 문자열
        val userId = UUID.randomUUID()
        val uppercaseUuidString = userId.toString().uppercase()
        val user = User(
            id = userId,
            email = "upper@example.com",
            password = "password",
            name = "Upper User",
            createdAt = LocalDateTime.now(),
            modifiedAt = LocalDateTime.now()
        )

        coEvery { userService.getUserById(userId) } returns user

        // when & then: 대문자 UUID로 GET /users/me 요청 시 200 OK가 반환되어야 한다
        webTestClient.get()
            .uri("/users/me?id={id}", uppercaseUuidString)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.email").isEqualTo("upper@example.com")

        // verify: userService.getUserById가 호출되었는지 검증
        coVerify(exactly = 1) { userService.getUserById(userId) }
    }
}
