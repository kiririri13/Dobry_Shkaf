package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.Transfer;
import ru.sfustudio.dobryshkaf.domain.TransferAction;
import ru.sfustudio.dobryshkaf.domain.TransferRole;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * @param myRole           ваша роль в передаче; {@code null} в событиях WebSocket, общих для обоих участников
 * @param availableActions команды, которые вы можете выполнить сейчас
 */
public record TransferResponse(
        UUID id,
        ItemSummaryResponse item,
        UserSummaryResponse giver,
        UserSummaryResponse receiver,
        UUID chatId,
        TransferStatus status,
        boolean giverConfirmed,
        boolean receiverConfirmed,
        TransferRole cancellationRequestedBy,
        TransferRole myRole,
        Set<TransferAction> availableActions,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static TransferResponse from(Transfer transfer, TransferRole viewerRole) {
        return new TransferResponse(
                transfer.getId(),
                ItemSummaryResponse.from(transfer.getItem()),
                UserSummaryResponse.from(transfer.getGiver()),
                UserSummaryResponse.from(transfer.getReceiver()),
                transfer.getChat().getId(),
                transfer.getStatus(),
                transfer.isConfirmedBy(TransferRole.GIVER),
                transfer.isConfirmedBy(TransferRole.RECEIVER),
                transfer.getCancellationRequestedBy(),
                viewerRole,
                transfer.availableActions(viewerRole),
                transfer.getCompletedAt(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt());
    }
}
