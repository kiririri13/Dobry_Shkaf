package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.domain.ItemModerationStatus;
import ru.sfustudio.dobryshkaf.dto.ItemResponse;
import ru.sfustudio.dobryshkaf.dto.PageResponse;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.ItemModerationService;
import ru.sfustudio.dobryshkaf.service.ModerationLog;
import ru.sfustudio.dobryshkaf.service.ReviewService;

import java.util.List;
import java.util.UUID;

@Tag(name = "9. Администрирование и модерация")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminModerationController {

    private final ItemModerationService moderation;
    private final ReviewService reviews;
    private final ModerationLog log;

    @GetMapping("/items")
    public PageResponse<ItemResponse> queue(@RequestParam(required = false) ItemModerationStatus status,
                                            @RequestParam(defaultValue = "0") @Min(0) int page) {
        return moderation.queue(status, page);
    }

    @PostMapping("/items/{id}/review")
    public ItemResponse review(@PathVariable UUID id, @Parameter(hidden = true) Authentication auth) {
        return moderation.change(id, ItemModerationService.ItemAction.REVIEW, null, CurrentUser.require(auth));
    }

    @PostMapping("/items/{id}/hide")
    public ItemResponse hide(@PathVariable UUID id, @Valid @RequestBody Reason request,
                             @Parameter(hidden = true) Authentication auth) {
        return moderation.change(id, ItemModerationService.ItemAction.HIDE, request.reason().strip(), CurrentUser.require(auth));
    }

    @PostMapping("/items/{id}/restore")
    public ItemResponse restore(@PathVariable UUID id, @Parameter(hidden = true) Authentication auth) {
        return moderation.change(id, ItemModerationService.ItemAction.RESTORE, null, CurrentUser.require(auth));
    }

    @PostMapping("/reviews/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideReview(@PathVariable UUID id, @Valid @RequestBody Reason request,
                           @Parameter(hidden = true) Authentication auth) {
        reviews.hide(id, request.reason().strip(), CurrentUser.require(auth));
    }

    @PostMapping("/users/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable UUID id, @Valid @RequestBody Reason request,
                      @Parameter(hidden = true) Authentication auth) {
        moderation.block(id, request.reason().strip(), CurrentUser.require(auth));
    }

    @GetMapping("/moderation-log")
    public List<ModerationLog.Entry> log(@RequestParam(defaultValue = "0") @Min(0) int page) {
        return log.list(page);
    }

    public record Reason(@NotBlank @Size(max = 1000) String reason) {
    }
}
