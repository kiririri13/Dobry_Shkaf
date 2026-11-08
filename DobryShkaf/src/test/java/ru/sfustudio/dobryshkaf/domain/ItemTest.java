package ru.sfustudio.dobryshkaf.domain;

import org.junit.jupiter.api.Test;
import ru.sfustudio.dobryshkaf.exception.ConflictException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    private final User owner = new User("anna@example.com", "hash", "Анна", UserRole.USER);

    @Test
    void newItemIsDraftWithAllPublicationFieldsMissing() {
        Item item = new Item(owner, "Куртка");

        assertThat(item.getStatus()).isEqualTo(ItemStatus.DRAFT);
        assertThat(item.missingForPublication())
                .containsExactly("description", "condition", "categoryId", "districtId", "photos");
    }

    @Test
    void sizeIsRequiredOnlyForCategoriesThatNeedIt() {
        Item clothes = completeItem(category(true), null);
        Item books = completeItem(category(false), null);

        assertThat(clothes.missingForPublication()).containsExactly("size");
        assertThat(books.missingForPublication()).isEmpty();
    }

    @Test
    void publishesDraft() {
        Item item = completeItem(category(false), null);

        item.publish(NOW);

        assertThat(item.getStatus()).isEqualTo(ItemStatus.PUBLISHED);
        assertThat(item.getPublishedAt()).isEqualTo(NOW);
        assertThat(item.isPublic()).isTrue();
    }

    @Test
    void cannotPublishTwice() {
        Item item = completeItem(category(false), null);
        item.publish(NOW);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> item.publish(NOW));
    }

    @Test
    void closedItemCannotBeChanged() {
        Item item = completeItem(category(false), null);
        item.close();

        assertThat(item.isPublic()).isFalse();
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> item.edit("Новое", null, null, null, null, null));
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> item.addPhoto("key", "image/jpeg", 1));
        assertThatExceptionOfType(ConflictException.class).isThrownBy(item::close);
    }

    @Test
    void photosKeepUploadOrderAfterRemoval() {
        Item item = new Item(owner, "Куртка");
        ItemPhoto first = item.addPhoto("a", "image/jpeg", 1);
        item.addPhoto("b", "image/jpeg", 1);
        item.removePhoto(first);

        ItemPhoto third = item.addPhoto("c", "image/jpeg", 1);

        assertThat(third.getSortOrder()).isEqualTo(2);
        assertThat(item.getPhotos()).extracting(ItemPhoto::getStorageKey).containsExactly("b", "c");
    }

    @Test
    void limitsPhotosToFive() {
        Item item = new Item(owner, "Куртка");
        for (int i = 0; i < Item.MAX_PHOTOS; i++) {
            assertThat(item.hasRoomForPhoto()).isTrue();
            item.addPhoto("key-" + i, "image/jpeg", 1);
        }

        assertThat(item.hasRoomForPhoto()).isFalse();
    }

    private Item completeItem(Category category, String size) {
        Item item = new Item(owner, "Куртка");
        item.edit("Куртка", "Тёплая", ItemCondition.LIKE_NEW, size, category, mock(District.class));
        item.addPhoto("key", "image/jpeg", 1);
        return item;
    }

    private static Category category(boolean sizeRequired) {
        Category category = mock(Category.class);
        when(category.isSizeRequired()).thenReturn(sizeRequired);
        return category;
    }
}
