package com.openknot.user.service

import com.openknot.user.entity.User
import com.openknot.user.exception.BusinessException
import com.openknot.user.exception.ErrorCode
import com.openknot.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

/**
 * UserService 단위 테스트
 *
 * 테스트 전략:
 * - Repository를 MockK로 모킹하여 외부 의존성 제거
 * - 코루틴 테스트를 위해 runTest {} 블록 사용
 * - BDD 스타일의 given-when-then 패턴 적용
 * - Happy path와 Error handling 시나리오 모두 커버
 */
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        userService = UserService(userRepository)
    }

    @Test
    @DisplayName("getUserById - 유저가 존재할 때 정상적으로 User 엔티티를 반환한다")
    fun `given existing user id, when getUserById, then should return user entity`() = runTest {
        // given: 존재하는 유저 ID와 해당 유저 엔티티
        val userId = UUID.randomUUID()
        val expectedUser = User(
            id = userId,
            email = "test@example.com",
            password = "hashedPassword123",
            name = "테스트 유저",
            profileImageUrl = "https://example.com/profile.jpg",
            description = "테스트 설명",
            githubLink = "https://github.com/testuser",
            createdAt = LocalDateTime.now(),
            modifiedAt = LocalDateTime.now()
        )

        coEvery { userRepository.findById(userId) } returns expectedUser

        // when: getUserById 메서드를 호출할 때
        val result = userService.getUserById(userId)

        // then: 올바른 User 엔티티가 반환되어야 한다
        result shouldNotBe null
        result.id shouldBe userId
        result.email shouldBe "test@example.com"
        result.name shouldBe "테스트 유저"
        result.profileImageUrl shouldBe "https://example.com/profile.jpg"
        result.description shouldBe "테스트 설명"
        result.githubLink shouldBe "https://github.com/testuser"

        // verify: repository의 findById가 정확히 한 번 호출되었는지 검증
        coVerify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    @DisplayName("getUserById - 유저가 존재하지 않을 때 BusinessException(USER_NOT_FOUND)을 발생시킨다")
    fun `given non-existing user id, when getUserById, then should throw BusinessException with USER_NOT_FOUND`() = runTest {
        // given: 존재하지 않는 유저 ID
        val nonExistingUserId = UUID.randomUUID()

        coEvery { userRepository.findById(nonExistingUserId) } returns null

        // when & then: getUserById 호출 시 BusinessException이 발생해야 한다
        val exception = shouldThrow<BusinessException> {
            userService.getUserById(nonExistingUserId)
        }

        // then: 예외의 에러 코드가 USER_NOT_FOUND여야 한다
        exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND

        // verify: repository의 findById가 정확히 한 번 호출되었는지 검증
        coVerify(exactly = 1) { userRepository.findById(nonExistingUserId) }
    }

    @Test
    @DisplayName("getUserById - 프로필 이미지와 설명이 null인 유저도 정상적으로 반환한다")
    fun `given user with null optional fields, when getUserById, then should return user entity with null fields`() = runTest {
        // given: 선택적 필드(profileImageUrl, description, githubLink)가 null인 유저
        val userId = UUID.randomUUID()
        val userWithNullFields = User(
            id = userId,
            email = "minimal@example.com",
            password = "hashedPassword456",
            name = "미니멀 유저",
            profileImageUrl = null,
            description = null,
            githubLink = null,
            createdAt = LocalDateTime.now(),
            modifiedAt = LocalDateTime.now()
        )

        coEvery { userRepository.findById(userId) } returns userWithNullFields

        // when: getUserById 메서드를 호출할 때
        val result = userService.getUserById(userId)

        // then: null 필드를 포함한 User 엔티티가 정상적으로 반환되어야 한다
        result shouldNotBe null
        result.id shouldBe userId
        result.email shouldBe "minimal@example.com"
        result.name shouldBe "미니멀 유저"
        result.profileImageUrl shouldBe null
        result.description shouldBe null
        result.githubLink shouldBe null

        // verify: repository의 findById가 정확히 한 번 호출되었는지 검증
        coVerify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    @DisplayName("getUserById - deleted된 유저도 조회할 수 있다 (Soft Delete)")
    fun `given deleted user id, when getUserById, then should return deleted user entity`() = runTest {
        // given: deletedAt이 설정된 유저 (Soft Delete)
        val userId = UUID.randomUUID()
        val deletedUser = User(
            id = userId,
            email = "deleted@example.com",
            password = "hashedPassword789",
            name = "삭제된 유저",
            createdAt = LocalDateTime.now().minusDays(10),
            modifiedAt = LocalDateTime.now().minusDays(5),
            deletedAt = LocalDateTime.now().minusDays(1)
        )

        coEvery { userRepository.findById(userId) } returns deletedUser

        // when: getUserById 메서드를 호출할 때
        val result = userService.getUserById(userId)

        // then: deletedAt이 설정된 유저도 정상적으로 반환되어야 한다
        result shouldNotBe null
        result.id shouldBe userId
        result.deletedAt shouldNotBe null

        // verify: repository의 findById가 정확히 한 번 호출되었는지 검증
        coVerify(exactly = 1) { userRepository.findById(userId) }
    }
}
