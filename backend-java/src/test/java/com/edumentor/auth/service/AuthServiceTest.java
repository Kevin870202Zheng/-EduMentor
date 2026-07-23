package com.edumentor.auth.service;

import com.edumentor.auth.dto.LoginRequest;
import com.edumentor.auth.dto.RefreshTokenRequest;
import com.edumentor.auth.dto.RegisterRequest;
import com.edumentor.entity.enums.UserRole;
import com.edumentor.auth.dto.TokenResponse;
import com.edumentor.common.constant.RoleConstant;
import com.edumentor.config.JwtTokenProvider;
import com.edumentor.config.JwtTokenProvider.TokenPair;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link AuthService} 的单元测试。
 * <p>
 * 测试覆盖：注册、登录、Token 刷新、获取当前用户四大核心流程，
 * 以及所有异常路径（重复用户名、非法角色、密码错误、账号禁用、过期 Token 等）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — 认证服务单元测试")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private User mockUser;
    private UUID userId;
    private TokenResponse mockTokenResponse;
    private TokenPair mockTokenPair;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("testuser");
        mockUser.setPasswordHash("$2a$10$encodedhash");
        mockUser.setDisplayName("测试用户");
        mockUser.setEmail("test@example.com");
        mockUser.setRole(UserRole.STUDENT);
        mockUser.setIsActive(true);
        mockUser.setLastLoginAt(LocalDateTime.now());

        mockTokenPair = new TokenPair("access-token-123", "refresh-token-456", 1800L);
        mockTokenResponse = TokenResponse.builder()
                .accessToken("access-token-123")
                .refreshToken("refresh-token-456")
                .expiresIn(1800L)
                .build();
    }

    @Nested
    @DisplayName("register() — 用户注册")
    class RegisterTests {

        @Test
        @DisplayName("正常注册学生角色 — 应返回 Token 对")
        void registerStudentSuccess() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newstudent");
            request.setPassword("password123");
            request.setDisplayName("新同学");
            request.setEmail("new@example.com");
            request.setRole("STUDENT");

            when(userRepository.existsByUsername("newstudent")).thenReturn(false);
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encodedhash");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });
            when(jwtTokenProvider.generateTokenPair(any(UUID.class), eq("STUDENT")))
                    .thenReturn(mockTokenPair);

            TokenResponse result = authService.register(request);

            assertThat(result).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("access-token-123");
            assertThat(result.getRefreshToken()).isEqualTo("refresh-token-456");

            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getUsername()).isEqualTo("newstudent");
            assertThat(savedUser.getRole()).isEqualTo(UserRole.STUDENT);
            assertThat(savedUser.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("注册时角色为空 — 应默认 STUDENT 角色")
        void registerWithNullRoleDefaultsToStudent() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("student2");
            request.setPassword("password123");

            when(userRepository.existsByUsername("student2")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(jwtTokenProvider.generateTokenPair(any(UUID.class), eq("STUDENT")))
                    .thenReturn(mockTokenPair);

            authService.register(request);

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getRole()).isEqualTo("STUDENT");
        }

        @Test
        @DisplayName("用户名已存在 — 应抛出 IllegalArgumentException")
        void usernameAlreadyExists() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("existing");
            request.setPassword("password123");
            request.setRole("STUDENT");

            when(userRepository.existsByUsername("existing")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("已存在");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("邮箱已注册 — 应抛出 IllegalArgumentException")
        void emailAlreadyExists() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setEmail("dup@example.com");
            request.setRole("STUDENT");

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("已被注册");
        }

        @Test
        @DisplayName("非法角色 — 应抛出 IllegalArgumentException")
        void invalidRole() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setRole("SUPER_ADMIN");

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非法角色");
        }

        @Test
        @DisplayName("注册教师角色 — 应正确保存角色")
        void registerTeacher() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("teacher1");
            request.setPassword("password123");
            request.setDisplayName("张老师");
            request.setEmail("teacher@school.com");
            request.setRole("TEACHER");

            when(userRepository.existsByUsername("teacher1")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(jwtTokenProvider.generateTokenPair(any(UUID.class), eq("TEACHER")))
                    .thenReturn(mockTokenPair);

            authService.register(request);

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getRole()).isEqualTo("TEACHER");
        }
    }

    @Nested
    @DisplayName("login() — 用户登录")
    class LoginTests {

        @Test
        @DisplayName("正常登录 — 应返回 Token 对并更新 lastLoginAt")
        void loginSuccess() {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");
            Authentication authentication = mock(Authentication.class);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(authentication.getName()).thenReturn(userId.toString());
            doReturn(java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))).when(authentication).getAuthorities();
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(jwtTokenProvider.generateTokenPair(userId, "STUDENT"))
                    .thenReturn(mockTokenPair);

            TokenResponse result = authService.login(request);

            assertThat(result.getAccessToken()).isEqualTo("access-token-123");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("密码错误 — 应抛出 BadCredentialsException")
        void badCredentials() {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("wrongpass");

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("用户名或密码错误");
        }

        @Test
        @DisplayName("账号被禁用 — 应抛出 DisabledException")
        void disabledAccount() {
            LoginRequest request = new LoginRequest();
            request.setUsername("disabled_user");
            request.setPassword("password123");

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new DisabledException("User is disabled"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(DisabledException.class)
                    .hasMessageContaining("已被禁用");
        }

        @Test
        @DisplayName("登录后用户不存在于数据库 — 应仍能签发 Token")
        void loginUserNotFoundInDbStillGeneratesToken() {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");
            Authentication authentication = mock(Authentication.class);

            when(authenticationManager.authenticate(any()))
                    .thenReturn(authentication);
            when(authentication.getName()).thenReturn(userId.toString());
            doReturn(java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))).when(authentication).getAuthorities();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            when(jwtTokenProvider.generateTokenPair(userId, "STUDENT"))
                    .thenReturn(mockTokenPair);

            TokenResponse result = authService.login(request);

            assertThat(result).isNotNull();
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("refreshToken() — Token 刷新")
    class RefreshTokenTests {

        @Test
        @DisplayName("有效 Refresh Token — 应返回新的 Token 对")
        void validRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("valid-refresh-token");

            when(jwtTokenProvider.isRefreshToken("valid-refresh-token")).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken("valid-refresh-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(jwtTokenProvider.generateTokenPair(userId, "STUDENT"))
                    .thenReturn(mockTokenPair);

            TokenResponse result = authService.refreshToken(request);

            assertThat(result.getAccessToken()).isEqualTo("access-token-123");
        }

        @Test
        @DisplayName("非 Refresh Token 类型 — 应抛出 IllegalArgumentException")
        void notRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("access-token");

            when(jwtTokenProvider.isRefreshToken("access-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无效的 Refresh Token");
        }

        @Test
        @DisplayName("Refresh Token 已过期 — 应抛出 IllegalArgumentException")
        void expiredRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("expired-token");

            when(jwtTokenProvider.isRefreshToken("expired-token")).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken("expired-token")).thenReturn(null);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("已过期");
        }

        @Test
        @DisplayName("用户不存在 — 应抛出 IllegalArgumentException")
        void userNotFound() {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("valid-token");

            when(jwtTokenProvider.isRefreshToken("valid-token")).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户不存在");
        }

        @Test
        @DisplayName("账号已被禁用 — 应抛出 IllegalArgumentException")
        void userDisabled() {
            mockUser.setIsActive(false);
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("valid-token");

            when(jwtTokenProvider.isRefreshToken("valid-token")).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("已被禁用");
        }
    }

    @Nested
    @DisplayName("getCurrentUser() — 获取当前用户")
    class GetCurrentUserTests {

        @Test
        @DisplayName("已认证用户 — 应返回用户实体")
        void authenticatedUser() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn(userId.toString());
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            User result = authService.getCurrentUser(authentication);

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("未认证用户 — 应抛出 IllegalStateException")
        void unauthenticatedUser() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(false);

            assertThatThrownBy(() -> authService.getCurrentUser(authentication))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未认证");
        }

        @Test
        @DisplayName("Authentication 为 null — 应抛出 IllegalStateException")
        void nullAuthentication() {
            assertThatThrownBy(() -> authService.getCurrentUser(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未认证");
        }

        @Test
        @DisplayName("当前用户在数据库中已被删除 — 应抛出 IllegalStateException")
        void userDeleted() {
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn(userId.toString());
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getCurrentUser(authentication))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("当前用户不存在");
        }
    }
}
