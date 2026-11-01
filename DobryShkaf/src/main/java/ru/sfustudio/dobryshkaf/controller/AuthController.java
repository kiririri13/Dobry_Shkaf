package ru.sfustudio.dobryshkaf.controller;

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
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.dto.LoginRequest;
import ru.sfustudio.dobryshkaf.dto.MessageResponse;
import ru.sfustudio.dobryshkaf.dto.RegisterRequest;
import ru.sfustudio.dobryshkaf.dto.ResendConfirmationRequest;
import ru.sfustudio.dobryshkaf.dto.UserResponse;
import ru.sfustudio.dobryshkaf.service.AccountService;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(accountService.register(request));
    }

    @GetMapping("/confirm")
    public MessageResponse confirm(@RequestParam String token) {
        accountService.confirmEmail(token);
        return new MessageResponse("Почта подтверждена. Теперь можно войти.");
    }

    @PostMapping("/resend-confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendConfirmation(@Valid @RequestBody ResendConfirmationRequest request) {
        accountService.resendConfirmation(request.email());
    }

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

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return currentUser(authentication);
    }

    private UserResponse currentUser(Authentication authentication) {
        return UserResponse.from(accountService.getById(UUID.fromString(authentication.getName())));
    }
}
