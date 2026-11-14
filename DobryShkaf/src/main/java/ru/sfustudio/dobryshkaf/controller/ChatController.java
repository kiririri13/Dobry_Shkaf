package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.dto.ChatMessageResponse;
import ru.sfustudio.dobryshkaf.dto.ChatResponse;
import ru.sfustudio.dobryshkaf.dto.MarkReadRequest;
import ru.sfustudio.dobryshkaf.dto.MessagePageResponse;
import ru.sfustudio.dobryshkaf.dto.OpenChatRequest;
import ru.sfustudio.dobryshkaf.dto.SendMessageRequest;
import ru.sfustudio.dobryshkaf.service.ChatService;
import ru.sfustudio.dobryshkaf.service.CommandResult;
import ru.sfustudio.dobryshkaf.service.CurrentUser;

import java.util.List;
import java.util.UUID;

@Tag(name = "5. Диалоги", description = "Новые сообщения и события приходят через WebSocket: STOMP на /ws, тема /topic/chats.{id}")
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Открыть диалог по вещи", description = "201 — создан новый, 200 — вернулся уже существующий")
    @PostMapping
    public ResponseEntity<ChatResponse> open(
            @Valid @RequestBody OpenChatRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        return respond(chatService.open(request.itemId(), CurrentUser.require(authentication)));
    }

    @Operation(summary = "Мои диалоги", description = "Сначала диалоги с последней активностью")
    @GetMapping
    public List<ChatResponse> list(@Parameter(hidden = true) Authentication authentication) {
        return chatService.list(CurrentUser.require(authentication));
    }

    @Operation(summary = "Диалог по id")
    @GetMapping("/{id}")
    public ChatResponse get(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return chatService.get(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "История частями",
            description = "Без параметров — последние сообщения. beforeSeq — более старые. "
                    + "afterSeq — пропущенные после переподключения. limit — до 100, по умолчанию 50")
    @GetMapping("/{id}/messages")
    public MessagePageResponse messages(
            @PathVariable UUID id,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(required = false) Integer limit,
            @Parameter(hidden = true) Authentication authentication) {
        return chatService.messages(id, CurrentUser.require(authentication), beforeSeq, afterSeq, limit);
    }

    @Operation(summary = "Отправить сообщение",
            description = "201 — отправлено. 200 — повтор с тем же requestId, вернулось прежнее сообщение")
    @PostMapping("/{id}/messages")
    public ResponseEntity<ChatMessageResponse> send(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        return respond(chatService.send(id, request, CurrentUser.require(authentication)));
    }

    @Operation(summary = "Отметить сообщения прочитанными", description = "Все сообщения собеседника до upToSeq включительно")
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @PathVariable UUID id,
            @Valid @RequestBody MarkReadRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        chatService.markRead(id, request.upToSeq(), CurrentUser.require(authentication));
    }

    private static <T> ResponseEntity<T> respond(CommandResult<T> result) {
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }
}
