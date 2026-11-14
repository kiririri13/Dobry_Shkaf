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
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;
import ru.sfustudio.dobryshkaf.dto.TransferEventResponse;
import ru.sfustudio.dobryshkaf.dto.TransferRequest;
import ru.sfustudio.dobryshkaf.dto.TransferResponse;
import ru.sfustudio.dobryshkaf.service.CommandResult;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.TransferService;

import java.util.List;
import java.util.UUID;

@Tag(name = "6. Передачи", description = "Запрос → выбор получателя → подтверждения обоих. "
        + "availableActions подсказывает, какие кнопки показать. Повтор команды возвращает текущее состояние, конфликт — 409")
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Operation(summary = "Попросить вещь", description = "201 — новый запрос; 200 — ваш запрос на эту вещь уже есть. "
            + "Заодно открывается диалог с владельцем")
    @PostMapping
    public ResponseEntity<TransferResponse> request(
            @Valid @RequestBody TransferRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        CommandResult<TransferResponse> result = transferService.request(request.itemId(), CurrentUser.require(authentication));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    @Operation(summary = "Мои передачи", description = "С itemId: владелец видит все запросы на вещь, остальные — только свои")
    @GetMapping
    public List<TransferResponse> list(
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) TransferStatus status,
            @Parameter(hidden = true) Authentication authentication) {
        return transferService.list(itemId, status, CurrentUser.require(authentication));
    }

    @Operation(summary = "Передача по id")
    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return transferService.get(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "История передачи", description = "Все команды и смены статуса по порядку")
    @GetMapping("/{id}/events")
    public List<TransferEventResponse> events(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return transferService.events(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "Выбрать получателя", description = "Только владелец. Вещь уходит в резерв, остальные запросы отклоняются")
    @PostMapping("/{id}/select")
    public TransferResponse select(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return transferService.select(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "Подтвердить передачу", description = "После подтверждения обоими передача завершается, вещь отмечается переданной")
    @PostMapping("/{id}/confirm")
    public TransferResponse confirm(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return transferService.confirm(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "Отменить",
            description = "Запрос: владелец отклоняет, получатель отзывает. Резерв: отмена. После первого подтверждения — просьба об отмене")
    @PostMapping("/{id}/cancel")
    public TransferResponse cancel(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return transferService.cancel(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "Согласиться с отменой", description = "Второй участник; вещь возвращается в каталог")
    @PostMapping("/{id}/cancellation/accept")
    public TransferResponse acceptCancellation(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return transferService.acceptCancellation(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "Отказаться от отмены", description = "Второй участник; снова ждём второго подтверждения")
    @PostMapping("/{id}/cancellation/decline")
    public TransferResponse declineCancellation(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return transferService.declineCancellation(id, CurrentUser.require(authentication));
    }
}
