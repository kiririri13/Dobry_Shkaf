package ru.sfustudio.dobryshkaf.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String DESCRIPTION = """
            API платформы «Добрый шкаф».

            **Как войти:** выполните `POST /api/auth/login`. Сервер сохранит сессию в cookie браузера,
            и следующие запросы со страницы Swagger будут выполняться от вашего имени.
            CSRF-токен Swagger подставляет сам. Выйти — `POST /api/auth/logout`.

            Разделы «Админка» доступны только пользователю с ролью ADMIN.
            """;

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Добрый шкаф API")
                .version("0.0.1")
                .description(DESCRIPTION));
    }
}
