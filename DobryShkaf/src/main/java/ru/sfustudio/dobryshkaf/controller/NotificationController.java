package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.NotificationService;

import java.util.UUID;

@Tag(name = "12. Уведомления")
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    @GetMapping("/api/notifications")
    public NotificationService.Feed list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @Parameter(hidden = true) Authentication auth) {
        return notifications.list(CurrentUser.require(auth), unreadOnly, page);
    }

    @PutMapping("/api/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable UUID id, @Parameter(hidden = true) Authentication auth) {
        notifications.markRead(CurrentUser.require(auth), id);
    }

    @PutMapping("/api/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@Parameter(hidden = true) Authentication auth) {
        notifications.markAllRead(CurrentUser.require(auth));
    }

    @PutMapping("/api/profile/me/reviews/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readReview(@PathVariable UUID id, @Parameter(hidden = true) Authentication auth) {
        notifications.markReviewRead(CurrentUser.require(auth), id);
    }
}
