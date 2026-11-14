package ru.sfustudio.dobryshkaf.dto;

import ru.sfustudio.dobryshkaf.domain.TransferCommand;
import ru.sfustudio.dobryshkaf.domain.TransferEvent;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferEventResponse(
        UUID id,
        TransferCommand command,
        UUID actorId,
        TransferStatus fromStatus,
        TransferStatus toStatus,
        Instant createdAt
) {

    public static TransferEventResponse from(TransferEvent event) {
        return new TransferEventResponse(
                event.getId(),
                event.getCommand(),
                event.getActor().getId(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getCreatedAt());
    }
}
