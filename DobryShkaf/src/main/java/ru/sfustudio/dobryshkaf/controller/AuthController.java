package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.dto.LoginRequest;
import ru.sfustudio.dobryshkaf.dto.PasswordResetConfirmRequest;
import ru.sfustudio.dobryshkaf.dto.PasswordResetRequest;
import ru.sfustudio.dobryshkaf.dto.MessageResponse;
import ru.sfustudio.dobryshkaf.dto.RegisterRequest;
import ru.sfustudio.dobryshkaf.dto.ResendConfirmationRequest;
import ru.sfustudio.dobryshkaf.dto.UserResponse;
import ru.sfustudio.dobryshkaf.exception.AuthenticationRequiredException;
import ru.sfustudio.dobryshkaf.service.AccountService;

import java.util.UUID;

@Tag(name = "1. Вход и регистрация")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

    @Operation(summary = "Регистрация", description = "На почту придёт письмо со ссылкой подтверждения")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(accountService.register(request));
    }

    @Operation(summary = "Подтверждение почты по токену из письма")
    @GetMapping("/confirm")
    public MessageResponse confirm(@RequestParam String token) {
        accountService.confirmEmail(token);
        return new MessageResponse("Почта подтверждена. Теперь можно войти.");
    }

    @Operation(summary = "Отправить письмо подтверждения ещё раз")
    @PostMapping("/resend-confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendConfirmation(@Valid @RequestBody ResendConfirmationRequest request) {
        accountService.resendConfirmation(request.email());
    }

    @Operation(summary = "Запросить сброс пароля",
            description = "Ответ одинаковый для известной и неизвестной почты. Письмо со ссылкой действует 30 минут")
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        accountService.requestPasswordReset(request.email());
    }

    @Operation(summary = "Задать новый пароль по токену из письма", description = "Ссылка срабатывает один раз")
    @PostMapping("/password-reset/confirm")
    public MessageResponse resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        accountService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Пароль изменён. Теперь можно войти.");
    }

    @Operation(summary = "Вход по почте или телефону", description = "Сессия сохраняется в cookie браузера")
    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest login,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.login(), login.password()));

        // Новый идентификатор сессии защищает от её фиксации
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return currentUser(authentication);
    }

    @Operation(summary = "Текущий пользователь")
    @GetMapping("/me")
    public UserResponse me(@Parameter(hidden = true) Authentication authentication,
                           HttpServletRequest request, HttpServletResponse response) {
        UserResponse account = currentUser(authentication);
        String role = "ROLE_" + account.role().name();
        if (authentication.getAuthorities().stream().noneMatch(authority -> role.equals(authority.getAuthority()))) {
            sessionLogout.logout(request, response, authentication);
            throw new AuthenticationRequiredException();
        }
        return account;
    }

    private UserResponse currentUser(Authentication authentication) {
        return UserResponse.from(accountService.getById(UUID.fromString(authentication.getName())));
    }
}
