package ru.sfustudio.dobryshkaf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.exception.ForbiddenException;
import ru.sfustudio.dobryshkaf.exception.NotFoundException;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

@Component
@RequiredArgsConstructor
class ActiveAccounts {

    private final UserRepository users;

    User lockActive(CurrentUser user) {
        User account = users.findForUpdateById(user.id()).orElseThrow(() -> new NotFoundException("Профиль не найден"));
        if (account.isBlocked()) {
            throw new ForbiddenException("Аккаунт заблокирован");
        }
        return account;
    }
}
