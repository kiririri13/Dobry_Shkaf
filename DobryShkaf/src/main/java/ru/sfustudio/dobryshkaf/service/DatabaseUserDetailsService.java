package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /** @param login почта или телефон */
    @Override
    public UserDetails loadUserByUsername(String login) {
        return findByLogin(login.trim())
                .map(DatabaseUserDetailsService::toUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private Optional<User> findByLogin(String login) {
        if (login.contains("@")) {
            return userRepository.findByEmailIgnoreCase(login);
        }
        return PhoneNumbers.normalize(login).flatMap(userRepository::findByPhone);
    }

    private static UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getId().toString())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(!user.isEmailConfirmed())
                .build();
    }
}
