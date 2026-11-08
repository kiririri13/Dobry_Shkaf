package ru.sfustudio.dobryshkaf.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.sfustudio.dobryshkaf.TestcontainersConfiguration;
import ru.sfustudio.dobryshkaf.domain.Category;
import ru.sfustudio.dobryshkaf.domain.District;
import ru.sfustudio.dobryshkaf.repository.CategoryRepository;
import ru.sfustudio.dobryshkaf.repository.CollectionPointRepository;
import ru.sfustudio.dobryshkaf.repository.DistrictRepository;
import ru.sfustudio.dobryshkaf.repository.OrganizationRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PointIntegrationTest {

    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");
    private static final RequestPostProcessor REGULAR_USER = user(UUID.randomUUID().toString()).roles("USER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CollectionPointRepository pointRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DistrictRepository districtRepository;

    private Category kidsClothing;
    private Category books;
    private District kirovsky;
    private District leninsky;

    @BeforeEach
    void setUp() {
        pointRepository.deleteAll();
        organizationRepository.deleteAll();
        kidsClothing = categoryRepository.findAll().stream().filter(c -> c.getCode().equals("kids-clothing")).findFirst().orElseThrow();
        books = categoryRepository.findAll().stream().filter(c -> c.getCode().equals("books")).findFirst().orElseThrow();
        kirovsky = districtRepository.findAll().stream().filter(d -> d.getName().equals("Кировский")).findFirst().orElseThrow();
        leninsky = districtRepository.findAll().stream().filter(d -> d.getName().equals("Ленинский")).findFirst().orElseThrow();
    }

    @Test
    void newActiveExchangePointIsShownOnMapAndInList() throws Exception {
        String body = admin(post("/api/admin/points"), exchangePoint())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.organization.name").value("Фонд «Тест»"))
                .andExpect(jsonPath("$.district.name").value("Кировский"))
                .andExpect(jsonPath("$.latitude").value(56.851))
                .andExpect(jsonPath("$.receivingConditions").value("Бесплатно"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].temporarilyClosed").value(false))
                .andExpect(jsonPath("$[0].categories[0].code").value("kids-clothing"));
        mockMvc.perform(get("/api/points/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value("Пн–Пт 10:00–19:00"))
                .andExpect(jsonPath("$.verifiedAt").value("2026-11-01"));
    }

    @Test
    void sameFiltersReturnSamePointsWithoutGeolocation() throws Exception {
        create(exchangePoint());
        create(point(Map.of("type", "COLLECTION", "name", "Пункт приёма книг", "districtId", leninsky.getId(),
                "categoryIds", List.of(books.getId()), "receivingConditions", "null")));

        String byDistrict = mockMvc.perform(get("/api/points").param("districtId", leninsky.getId().toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Пункт приёма книг"))
                .andExpect(jsonPath("$[0].receivingConditions").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/points").param("districtId", leninsky.getId().toString()))
                .andExpect(content().json(byDistrict, true));

        mockMvc.perform(get("/api/points").param("type", "EXCHANGE"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("EXCHANGE"));
        mockMvc.perform(get("/api/points").param("categoryId", books.getId().toString()))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/points").param("q", "КНИГ"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/points").param("q", "тестовая улица"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void pointWithSeveralCategoriesIsNotDuplicated() throws Exception {
        create(point(Map.of("categoryIds", List.of(kidsClothing.getId(), books.getId()))));

        mockMvc.perform(get("/api/points").param("categoryId", books.getId().toString()))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/points"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categories.length()").value(2));
    }

    @Test
    void temporarilyClosedPointIsMarkedAndCanBeHidden() throws Exception {
        String id = create(exchangePoint());

        admin(put("/api/admin/points/{id}", id), point(Map.of("status", "TEMPORARILY_CLOSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TEMPORARILY_CLOSED"));

        mockMvc.perform(get("/api/points"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].temporarilyClosed").value(true));
        mockMvc.perform(get("/api/points").param("openOnly", "true"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void archivedPointDisappearsFromMapAndList() throws Exception {
        String id = create(exchangePoint());

        admin(post("/api/admin/points/{id}/archive", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/points")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/points/{id}", id)).andExpect(status().isNotFound());
        admin(get("/api/admin/points").param("status", "ARCHIVED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        admin(put("/api/admin/points/{id}", id), exchangePoint())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINT_ARCHIVED"));
    }

    @Test
    void regularUserGetsForbidden() throws Exception {
        String id = create(exchangePoint());

        mockMvc.perform(post("/api/admin/points").with(REGULAR_USER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(exchangePoint()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/points/{id}", id).with(REGULAR_USER).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(exchangePoint()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/points/{id}/archive", id).with(REGULAR_USER).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/points").with(REGULAR_USER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/points")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/points/{id}", id)).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void validatesRequiredFields() throws Exception {
        admin(post("/api/admin/points"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.type").exists())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.organization").exists())
                .andExpect(jsonPath("$.fields.address").exists())
                .andExpect(jsonPath("$.fields.districtId").exists())
                .andExpect(jsonPath("$.fields.latitude").exists())
                .andExpect(jsonPath("$.fields.longitude").exists())
                .andExpect(jsonPath("$.fields.hours").exists())
                .andExpect(jsonPath("$.fields.contacts").exists())
                .andExpect(jsonPath("$.fields.categoryIds").exists())
                .andExpect(jsonPath("$.fields.conditions").exists())
                .andExpect(jsonPath("$.fields.source").exists())
                .andExpect(jsonPath("$.fields.verifiedAt").exists());
    }

    @Test
    void exchangePointRequiresReceivingConditions() throws Exception {
        admin(post("/api/admin/points"), point(Map.of("receivingConditions", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.receivingConditionsProvided")
                        .value("Для пункта обмена укажите условия получения вещей"));
    }

    @Test
    void rejectsInvalidValues() throws Exception {
        admin(post("/api/admin/points"), point(Map.of("latitude", 91, "longitude", -181)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.latitude").exists())
                .andExpect(jsonPath("$.fields.longitude").exists());
        admin(post("/api/admin/points"), point(Map.of("verifiedAt", LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.verifiedAt").value("Дата проверки не может быть в будущем"));
        admin(post("/api/admin/points"), point(Map.of("categoryIds", List.of(UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.categoryIds").value("Категория не найдена"));
        admin(post("/api/admin/points"), point(Map.of("type", "SHOP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        admin(post("/api/admin/points"), point(Map.of("status", "ARCHIVED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.statusAllowed").exists());

        assertThat(pointRepository.count()).isZero();
    }

    @Test
    void reusesOrganizationIgnoringCase() throws Exception {
        String first = create(point(Map.of("organization", "Фонд «Тест»")));
        String second = create(point(Map.of("organization", "  ФОНД «ТЕСТ»  ", "name", "Второй пункт")));

        String firstOrg = JsonPath.read(admin(get("/api/admin/points/{id}", first)).andReturn().getResponse().getContentAsString(), "$.organization.id");
        String secondOrg = JsonPath.read(admin(get("/api/admin/points/{id}", second)).andReturn().getResponse().getContentAsString(), "$.organization.id");
        assertThat(secondOrg).isEqualTo(firstOrg);
        assertThat(organizationRepository.count()).isEqualTo(1);
    }

    private String create(String json) throws Exception {
        String body = admin(post("/api/admin/points"), json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions admin(MockHttpServletRequestBuilder request, String json) throws Exception {
        return admin(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions admin(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mockMvc.perform(request.with(ADMIN).with(csrf()));
    }

    private String exchangePoint() {
        return point(Map.of());
    }

    /** Корректная карточка пункта обмена; {@code overrides} заменяют отдельные поля, строка "null" — пустое значение. */
    private String point(Map<String, Object> overrides) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", "EXCHANGE");
        fields.put("name", "Добрый пункт");
        fields.put("organization", "Фонд «Тест»");
        fields.put("address", "Екатеринбург, тестовая улица, 1");
        fields.put("districtId", kirovsky.getId());
        fields.put("latitude", 56.851);
        fields.put("longitude", 60.648);
        fields.put("hours", "Пн–Пт 10:00–19:00");
        fields.put("contacts", "+7 000 000-00-00");
        fields.put("categoryIds", List.of(kidsClothing.getId()));
        fields.put("conditions", "Чистые и целые вещи");
        fields.put("receivingConditions", "Бесплатно");
        fields.put("source", "Сайт организации");
        fields.put("verifiedAt", LocalDate.of(2026, 11, 1));
        fields.putAll(overrides);
        return fields.entrySet().stream()
                .map(entry -> "\"%s\": %s".formatted(entry.getKey(), json(entry.getValue())))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String json(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Collection<?> values) {
            return values.stream().map(PointIntegrationTest::json).collect(Collectors.joining(", ", "[", "]"));
        }
        return "null".equals(value) ? "null" : "\"" + value + "\"";
    }
}
