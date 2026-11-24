package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.service.ComplaintService;
import ru.sfustudio.dobryshkaf.service.CurrentUser;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "10. Жалобы")
@RestController
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaints;

    @GetMapping("/api/complaints/chat-notice")
    public Map<String, String> chatNotice() {
        return Map.of("message", ComplaintService.CHAT_NOTICE);
    }

    @PostMapping("/api/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintService.Complaint submit(@Valid @RequestBody ComplaintRequest request,
                                             @Parameter(hidden = true) Authentication auth) {
        return complaints.create(request.targetType(), request.targetId(), request.reason().strip(), CurrentUser.require(auth));
    }

    @GetMapping("/api/complaints/{id}")
    public ComplaintService.Complaint submitted(@PathVariable UUID id,
                                                 @Parameter(hidden = true) Authentication auth) {
        return complaints.submitted(id, CurrentUser.require(auth));
    }

    @GetMapping("/api/admin/complaints")
    public List<ComplaintService.Complaint> list(@RequestParam(required = false) ComplaintService.Status status,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @Parameter(hidden = true) Authentication auth) {
        return complaints.list(status, page, CurrentUser.require(auth));
    }

    @GetMapping("/api/admin/complaints/{id}")
    public ComplaintService.Complaint get(@PathVariable UUID id, @Parameter(hidden = true) Authentication auth) {
        return complaints.get(id, CurrentUser.require(auth));
    }

    @PostMapping("/api/admin/complaints/{id}/close")
    public ComplaintService.Complaint close(@PathVariable UUID id, @Valid @RequestBody Decision request,
                                            @Parameter(hidden = true) Authentication auth) {
        return complaints.close(id, request.outcome(), request.explanation().strip(), CurrentUser.require(auth));
    }

    @PostMapping("/api/admin/complaints/{id}/reopen")
    public ComplaintService.Complaint reopen(@PathVariable UUID id, @Parameter(hidden = true) Authentication auth) {
        return complaints.reopen(id, CurrentUser.require(auth));
    }

    @GetMapping("/api/admin/complaints/{id}/chats/{chatId}/messages")
    public ResponseEntity<ComplaintService.Conversation> history(
            @PathVariable UUID id, @PathVariable UUID chatId,
            @RequestParam(required = false) @Min(1) Long beforeSeq,
            @RequestParam(required = false) @Min(0) Long afterSeq,
            @RequestParam(required = false) Integer limit, @Parameter(hidden = true) Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(complaints.history(id, chatId, beforeSeq, afterSeq, limit, CurrentUser.require(auth)));
    }

    public record ComplaintRequest(@NotNull ComplaintService.TargetType targetType, @NotNull UUID targetId,
                                    @NotBlank @Size(max = 1000) String reason) {
    }

    public record Decision(@NotNull ComplaintService.Outcome outcome, @NotBlank @Size(max = 1000) String explanation) {
    }
}
