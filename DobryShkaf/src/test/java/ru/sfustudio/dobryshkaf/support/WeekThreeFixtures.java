package ru.sfustudio.dobryshkaf.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.sfustudio.dobryshkaf.domain.Category;
import ru.sfustudio.dobryshkaf.domain.District;
import ru.sfustudio.dobryshkaf.domain.Item;
import ru.sfustudio.dobryshkaf.domain.ItemCondition;
import ru.sfustudio.dobryshkaf.domain.User;
import ru.sfustudio.dobryshkaf.domain.UserRole;
import ru.sfustudio.dobryshkaf.repository.CategoryRepository;
import ru.sfustudio.dobryshkaf.repository.DistrictRepository;
import ru.sfustudio.dobryshkaf.repository.ItemRepository;
import ru.sfustudio.dobryshkaf.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

/** Общие данные для тестов диалогов, передач и сброса пароля. */
@TestComponent
public class WeekThreeFixtures {

    public static final String PASSWORD = "secret-password";

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final DistrictRepository districtRepository;

    public WeekThreeFixtures(JdbcTemplate jdbcTemplate, UserRepository userRepository, ItemRepository itemRepository,
                             CategoryRepository categoryRepository, DistrictRepository districtRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.districtRepository = districtRepository;
    }

    /** Справочники категорий и районов остаются, всё остальное очищается. */
    public void clean() {
        jdbcTemplate.execute("""
                TRUNCATE transfer_events, transfers, messages, chats, item_photos, items,
                         password_reset_tokens, email_confirmation_tokens, users CASCADE""");
    }

    /** Подтверждённый пользователь; пароль хранится как {noop}, чтобы входить им в тестах. */
    public User user(String email) {
        User user = new User(email, "{noop}" + PASSWORD, email.substring(0, email.indexOf('@')), UserRole.USER);
        user.confirmEmail();
        return userRepository.save(user);
    }

    public Item publishedItem(User owner) {
        Category books = categoryRepository.findAll().stream()
                .filter(category -> category.getCode().equals("books")).findFirst().orElseThrow();
        District district = districtRepository.findAll().getFirst();
        Item item = new Item(owner, "Книга");
        item.edit("Книга", "Описание", ItemCondition.NEW, null, books, district);
        item.addPhoto("test/" + UUID.randomUUID() + ".jpg", "image/jpeg", 1);
        item.publish(Instant.now());
        return itemRepository.save(item);
    }
}
