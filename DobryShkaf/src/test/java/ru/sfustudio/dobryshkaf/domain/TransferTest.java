package ru.sfustudio.dobryshkaf.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.sfustudio.dobryshkaf.exception.ConflictException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Переходы по таблице 5 ТЗ. */
class TransferTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private Item item;
    private Transfer transfer;

    @BeforeEach
    void setUp() {
        User owner = user();
        item = new Item(owner, "Санки");
        item.edit("Санки", "Описание", ItemCondition.USED, null, mock(Category.class), mock(District.class));
        item.addPhoto("key", "image/jpeg", 1);
        item.publish(NOW);
        transfer = new Transfer(item, user(), mock(Chat.class));
    }

    @Test
    void requestKeepsItemInCatalog() {
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.PUBLISHED);
    }

    @Test
    void selectionReservesItemAndRepeatChangesNothing() {
        assertThat(transfer.select()).isTrue();
        assertThat(transfer.select()).isFalse();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.RESERVED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.RESERVED);
    }

    @Test
    void twoConfirmationsCompleteTransferAndMarkItemTransferred() {
        transfer.select();

        assertThat(transfer.confirm(TransferRole.RECEIVER, NOW)).isTrue();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.AWAITING_CONFIRMATION);
        // Повтор первого подтверждения оставляет ожидание второго
        assertThat(transfer.confirm(TransferRole.RECEIVER, NOW)).isFalse();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.AWAITING_CONFIRMATION);

        assertThat(transfer.confirm(TransferRole.GIVER, NOW)).isTrue();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getCompletedAt()).isEqualTo(NOW);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.TRANSFERRED);
        assertThat(transfer.confirm(TransferRole.GIVER, NOW)).isFalse();
    }

    @Test
    void ownerRejectsAndReceiverWithdrawsRequest() {
        Transfer withdrawn = new Transfer(item, user(), mock(Chat.class));

        transfer.cancel(TransferRole.GIVER);
        withdrawn.cancel(TransferRole.RECEIVER);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(withdrawn.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.PUBLISHED);
    }

    @Test
    void cancelBeforeConfirmationReturnsItemToCatalog() {
        transfer.select();

        transfer.cancel(TransferRole.RECEIVER);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.PUBLISHED);
    }

    @Test
    void confirmationIsRejectedWhileCancellationIsNegotiated() {
        transfer.select();
        transfer.confirm(TransferRole.RECEIVER, NOW);

        transfer.cancel(TransferRole.GIVER);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLATION_REQUESTED);
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> transfer.confirm(TransferRole.RECEIVER, NOW));
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> transfer.acceptCancellation(TransferRole.GIVER));
    }

    @Test
    void declinedCancellationResumesWaitingForSecondConfirmation() {
        transfer.select();
        transfer.confirm(TransferRole.RECEIVER, NOW);
        transfer.cancel(TransferRole.GIVER);

        transfer.declineCancellation(TransferRole.RECEIVER);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.AWAITING_CONFIRMATION);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.RESERVED);
        assertThat(transfer.confirm(TransferRole.GIVER, NOW)).isTrue();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void acceptedCancellationReturnsItemToCatalog() {
        transfer.select();
        transfer.confirm(TransferRole.RECEIVER, NOW);
        transfer.cancel(TransferRole.RECEIVER);

        transfer.acceptCancellation(TransferRole.GIVER);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.PUBLISHED);
    }

    @Test
    void rejectedRequestCannotBeSelected() {
        transfer.rejectBecauseClosed();

        assertThatExceptionOfType(ConflictException.class).isThrownBy(transfer::select);
    }

    @Test
    void availableActionsDependOnRoleAndState() {
        assertThat(transfer.availableActions(TransferRole.GIVER)).containsExactlyInAnyOrder(TransferAction.SELECT, TransferAction.CANCEL);
        assertThat(transfer.availableActions(TransferRole.RECEIVER)).containsExactly(TransferAction.CANCEL);
        assertThat(transfer.availableActions(null)).isEmpty();

        transfer.select();
        transfer.confirm(TransferRole.RECEIVER, NOW);
        assertThat(transfer.availableActions(TransferRole.RECEIVER)).containsExactly(TransferAction.CANCEL);
        assertThat(transfer.availableActions(TransferRole.GIVER)).containsExactlyInAnyOrder(TransferAction.CONFIRM, TransferAction.CANCEL);
    }

    private static User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        return user;
    }
}
