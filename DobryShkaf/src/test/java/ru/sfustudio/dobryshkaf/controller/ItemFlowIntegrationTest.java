package ru.sfustudio.dobryshkaf.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.domain.Category;
import ru.sfustudio.dobryshkaf.domain.District;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.ItemCondition;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.domain.UserRole;
import ru.sfustudio.dobryshkaf.repository.CategoryRepository;
import ru.sfustudio.dobryshkaf.repository.DistrictRepository;
import ru.sfustudio.dobryshkaf.repository.EmailConfirmationTokenRepository;
import ru.sfustudio.dobryshkaf.repository.ItemPhotoRepository;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;
import ru.sfustudio.dobryshkaf.storage.StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ItemFlowIntegrationTest {

    private static final byte[] JPEG = image(0xFF, 0xD8, 0xFF, 0xE0);
    private static final byte[] PNG = image(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n');
    private static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemPhotoRepository itemPhotoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailConfirmationTokenRepository tokenRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DistrictRepository districtRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private StorageProperties storageProperties;

    private User owner;
    private User stranger;
    private User admin;
    private Category kidsClothing;
    private Category books;
    private District kirovsky;
    private District leninsky;

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        owner = userRepository.save(confirmedUser("owner@example.com", UserRole.USER));
        stranger = userRepository.save(confirmedUser("stranger@example.com", UserRole.USER));
        admin = userRepository.save(confirmedUser("admin@example.com", UserRole.ADMIN));
        kidsClothing = category("kids-clothing");
        books = category("books");
        kirovsky = district("Кировский");
        leninsky = district("Ленинский");
    }

    @Test
    void publishedItemWithPhotoIsFoundByFilters() throws Exception {
        UUID id = createDraft(owner, itemJson("Зимний комбинезон", "Тёплый, на рост 122", "LIKE_NEW", "122",
                kidsClothing.getId(), kirovsky.getId()));
        uploadPhoto(owner, id, JPEG, "photo.jpg", "image/jpeg").andExpect(status().isCreated());

        perform(post("/api/items/{id}/publish", id), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].contentType").value("image/jpeg"));

        search("q", "КОМБИНЕЗОН", "categoryId", kidsClothing.getId().toString(), "districtId", kirovsky.getId().toString(),
                "condition", "LIKE_NEW", "size", "122", "available", "true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].category.name").value("Детская одежда"))
                .andExpect(jsonPath("$.items[0].district.name").value("Кировский"))
                .andExpect(jsonPath("$.items[0].owner.name").value("Пользователь"))
                .andExpect(jsonPath("$.items[0].owner.email").doesNotExist());

        search("size", "128").andExpect(jsonPath("$.totalElements").value(0));
        search("condition", "NEW").andExpect(jsonPath("$.totalElements").value(0));
        search("districtId", leninsky.getId().toString()).andExpect(jsonPath("$.totalElements").value(0));
        search("categoryId", books.getId().toString()).andExpect(jsonPath("$.totalElements").value(0));
        search("q", "куртка").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void incompleteItemCannotBePublished() throws Exception {
        UUID id = createDraft(owner, itemJson("Куртка", null, null, null, null, null));

        perform(post("/api/items/{id}/publish", id), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITEM_INCOMPLETE"))
                .andExpect(jsonPath("$.fields.description").exists())
                .andExpect(jsonPath("$.fields.condition").exists())
                .andExpect(jsonPath("$.fields.categoryId").exists())
                .andExpect(jsonPath("$.fields.districtId").exists())
                .andExpect(jsonPath("$.fields.photos").exists());

        perform(get("/api/items/{id}", id), owner).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void sizeIsRequiredOnlyByCategory() throws Exception {
        UUID clothes = createDraft(owner, itemJson("Куртка", "Тёплая", "USED", null, kidsClothing.getId(), kirovsky.getId()));
        uploadPhoto(owner, clothes, JPEG, "a.jpg", "image/jpeg").andExpect(status().isCreated());
        UUID novel = createDraft(owner, itemJson("Роман", "Как новая", "NEW", null, books.getId(), kirovsky.getId()));
        uploadPhoto(owner, novel, JPEG, "b.jpg", "image/jpeg").andExpect(status().isCreated());

        perform(post("/api/items/{id}/publish", clothes), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.size").value("Укажите размер"));
        perform(post("/api/items/{id}/publish", novel), owner).andExpect(status().isOk());
    }

    @Test
    void rejectsFileThatIsNotAnImage() throws Exception {
        UUID id = createDraft(owner, itemJson("Куртка", null, null, null, null, null));

        // Имя и тип файла говорят «JPEG», но содержимое — текст
        uploadPhoto(owner, id, "<script>alert(1)</script>".getBytes(), "photo.jpg", "image/jpeg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PHOTO_FORMAT"));

        perform(get("/api/items/{id}", id), owner).andExpect(jsonPath("$.photos.length()").value(0));
    }

    @Test
    void rejectsPhotoLargerThanFiveMegabytes() throws Exception {
        UUID id = createDraft(owner, itemJson("Куртка", null, null, null, null, null));
        byte[] tooLarge = new byte[MAX_PHOTO_BYTES + 1];
        System.arraycopy(JPEG, 0, tooLarge, 0, JPEG.length);

        uploadPhoto(owner, id, tooLarge, "big.jpg", "image/jpeg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
    }

    @Test
    void rejectsSixthPhoto() throws Exception {
        UUID id = createDraft(owner, itemJson("Куртка", null, null, null, null, null));
        for (int i = 0; i < Item.MAX_PHOTOS; i++) {
            uploadPhoto(owner, id, PNG, "p.png", "image/png").andExpect(status().isCreated());
        }

        uploadPhoto(owner, id, PNG, "p.png", "image/png")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTO_LIMIT_REACHED"));
    }

    @Test
    void strangerCannotChangeItemAndDataStaysIntact() throws Exception {
        UUID id = publishedItem(owner, "Санки");

        perform(put("/api/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(itemJson("Чужое название", "Другое", "NEW", null, books.getId(), leninsky.getId())), stranger)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        perform(post("/api/items/{id}/close", id), stranger).andExpect(status().isForbidden());
        uploadPhoto(stranger, id, PNG, "p.png", "image/png").andExpect(status().isForbidden());
        String photoId = JsonPath.read(perform(get("/api/items/{id}", id), owner).andReturn().getResponse().getContentAsString(), "$.photos[0].id");
        perform(delete("/api/items/{id}/photos/{photoId}", id, photoId), stranger).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/items/{id}", id))
                .andExpect(jsonPath("$.title").value("Санки"))
                .andExpect(jsonPath("$.description").value("Описание"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.photos.length()").value(1));
    }

    @Test
    void adminCanEditAnyItem() throws Exception {
        UUID id = publishedItem(owner, "Санки");

        perform(put("/api/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(itemJson("Санки детские", "Описание", "USED", null, books.getId(), kirovsky.getId())), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Санки детские"));
    }

    @Test
    void draftIsVisibleOnlyToOwner() throws Exception {
        UUID id = createDraft(owner, itemJson("Черновик", null, null, null, null, null));

        mockMvc.perform(get("/api/items/{id}", id)).andExpect(status().isNotFound());
        perform(get("/api/items/{id}", id), stranger).andExpect(status().isNotFound());
        perform(put("/api/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(itemJson("Взлом", null, null, null, null, null)), stranger)
                .andExpect(status().isNotFound());
        perform(get("/api/items/{id}", id), owner).andExpect(status().isOk());

        search().andExpect(jsonPath("$.totalElements").value(0));
        perform(get("/api/items").param("mine", "true"), owner)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"));
        perform(get("/api/items").param("mine", "true"), stranger).andExpect(jsonPath("$.totalElements").value(0));
        search("mine", "true")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void publishedItemCannotLoseRequiredFields() throws Exception {
        UUID id = publishedItem(owner, "Санки");

        perform(put("/api/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(itemJson("Санки", null, "USED", null, books.getId(), kirovsky.getId())), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.description").exists());

        String photoId = JsonPath.read(perform(get("/api/items/{id}", id), owner).andReturn().getResponse().getContentAsString(), "$.photos[0].id");
        perform(delete("/api/items/{id}/photos/{photoId}", id, photoId), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.photos").exists());

        mockMvc.perform(get("/api/items/{id}", id))
                .andExpect(jsonPath("$.description").value("Описание"))
                .andExpect(jsonPath("$.photos.length()").value(1));
    }

    @Test
    void closedItemDisappearsFromCatalogAndIsReadOnly() throws Exception {
        UUID id = publishedItem(owner, "Санки");

        perform(post("/api/items/{id}/close", id), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        search().andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/items/{id}", id)).andExpect(status().isNotFound());
        perform(put("/api/items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(itemJson("Санки", "Описание", "USED", null, books.getId(), kirovsky.getId())), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));
        uploadPhoto(owner, id, PNG, "p.png", "image/png").andExpect(status().isConflict());
    }

    @Test
    void availabilityFilterSeparatesReservedItems() throws Exception {
        UUID available = publishedItem(owner, "Санки");
        UUID reserved = publishedItem(owner, "Лыжи");
        jdbcTemplate.update("UPDATE items SET status = 'RESERVED' WHERE id = ?", reserved);

        search().andExpect(jsonPath("$.totalElements").value(2));
        search("available", "true")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(available.toString()));
        search("available", "false")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(reserved.toString()));
    }

    @Test
    void catalogReturnsTwentyNewestItemsPerPage() throws Exception {
        Instant base = Instant.parse("2026-11-01T00:00:00Z");
        for (int i = 0; i <= 20; i++) {
            Item item = new Item(owner, "Вещь " + i);
            item.edit("Вещь " + i, "Описание", ItemCondition.NEW, null, books, kirovsky);
            item.addPhoto("test/" + UUID.randomUUID() + ".jpg", "image/jpeg", 1);
            item.publish(base.plusSeconds(i));
            itemRepository.save(item);
        }

        search()
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].title").value("Вещь 20"))
                .andExpect(jsonPath("$.items[19].title").value("Вещь 1"));
        search("page", "1")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Вещь 0"));
    }

    @Test
    void searchTreatsWildcardsLiterally() throws Exception {
        publishedItem(owner, "Детская куртка");

        search("q", "%").andExpect(jsonPath("$.totalElements").value(0));
        search("q", "_").andExpect(jsonPath("$.totalElements").value(0));
        search("q", "курт").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void photoIsServedAndRemovedFromStorage() throws Exception {
        UUID id = createDraft(owner, itemJson("Куртка", null, null, null, null, null));
        String body = uploadPhoto(owner, id, PNG, "p.png", "image/png")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID photoId = UUID.fromString(JsonPath.read(body, "$.photos[0].id"));
        String key = itemPhotoRepository.findById(photoId).orElseThrow().getStorageKey();
        assertThat(key).startsWith("items/" + id + "/").endsWith(".png");

        perform(get("/api/photos/{photoId}", photoId), owner)
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        // Фото черновика посторонним не отдаётся
        mockMvc.perform(get("/api/photos/{photoId}", photoId)).andExpect(status().isNotFound());

        perform(delete("/api/items/{id}/photos/{photoId}", id, photoId), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(0));

        assertThatThrownBy(() -> s3Client.headObject(request -> request.bucket(storageProperties.bucket()).key(key)))
                .isInstanceOfSatisfying(S3Exception.class, ex -> assertThat(ex.statusCode()).isEqualTo(404));
        perform(get("/api/photos/{photoId}", photoId), owner).andExpect(status().isNotFound());
    }

    @Test
    void publishedPhotoIsPublic() throws Exception {
        UUID id = publishedItem(owner, "Санки");
        String photoUrl = JsonPath.read(mockMvc.perform(get("/api/items/{id}", id))
                .andReturn().getResponse().getContentAsString(), "$.photos[0].url");

        mockMvc.perform(get(photoUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void validatesItemFields() throws Exception {
        perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                .content(itemJson(" ", null, null, null, null, null)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists());
        perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                .content(itemJson("Куртка", null, null, null, UUID.randomUUID(), null)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.categoryId").value("Категория не найдена"));
        perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                .content(itemJson("Куртка", null, "BROKEN", null, null, null)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        search("categoryId", "not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.categoryId").value("Некорректное значение"));
        search("page", "-1").andExpect(status().isBadRequest());
    }

    @Test
    void anonymousUserCannotCreateItems() throws Exception {
        mockMvc.perform(post("/api/items").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("Куртка", null, null, null, null, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void referenceDataIsPublic() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("kids-clothing"))
                .andExpect(jsonPath("$[0].sizeRequired").value(true));
        mockMvc.perform(get("/api/districts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
        mockMvc.perform(get("/api/item-conditions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].label").value("Почти новое"));
    }

    private UUID publishedItem(User user, String title) throws Exception {
        UUID id = createDraft(user, itemJson(title, "Описание", "USED", null, books.getId(), kirovsky.getId()));
        uploadPhoto(user, id, JPEG, "photo.jpg", "image/jpeg").andExpect(status().isCreated());
        perform(post("/api/items/{id}/publish", id), user).andExpect(status().isOk());
        return id;
    }

    private UUID createDraft(User user, String json) throws Exception {
        String body = perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content(json), user)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private ResultActions uploadPhoto(User user, UUID itemId, byte[] content, String filename, String contentType) throws Exception {
        return perform(multipart("/api/items/{id}/photos", itemId)
                .file(new MockMultipartFile("file", filename, contentType, content)), user);
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request, User user) throws Exception {
        return mockMvc.perform(request.with(as(user)).with(csrf()));
    }

    private ResultActions search(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/items");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private static RequestPostProcessor as(User user) {
        return user(user.getId().toString()).roles(user.getRole().name());
    }

    private static String itemJson(String title, String description, String condition, String size,
                                   UUID categoryId, UUID districtId) {
        return "{" + Arrays.stream(new String[][]{
                        {"title", title}, {"description", description}, {"condition", condition}, {"size", size},
                        {"categoryId", categoryId == null ? null : categoryId.toString()},
                        {"districtId", districtId == null ? null : districtId.toString()}})
                .map(pair -> "\"%s\": %s".formatted(pair[0], pair[1] == null ? "null" : "\"" + pair[1] + "\""))
                .collect(Collectors.joining(", ")) + "}";
    }

    private static User confirmedUser(String email, UserRole role) {
        User user = new User(email, "{noop}unused", "Пользователь", role);
        user.confirmEmail();
        return user;
    }

    private Category category(String code) {
        return categoryRepository.findAll().stream().filter(c -> c.getCode().equals(code)).findFirst().orElseThrow();
    }

    private District district(String name) {
        return districtRepository.findAll().stream().filter(d -> d.getName().equals(name)).findFirst().orElseThrow();
    }

    private static byte[] image(int... signature) {
        byte[] content = new byte[64];
        for (int i = 0; i < signature.length; i++) {
            content[i] = (byte) signature[i];
        }
        return content;
    }
}
