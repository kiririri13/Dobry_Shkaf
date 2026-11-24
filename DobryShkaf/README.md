# Добрый шкаф

Бэкенд платформы «Добрый шкаф»: регистрация и вход, объявления «Отдать вещь» с фото, каталог, карта пунктов приёма и обмена, админка пунктов.

Этот README написан для фронтенд-разработчика: как запустить сервер у себя, как к нему подключиться и **какие данные брать из каких запросов** для каждого экрана.

## Содержание

1. [Запуск бэкенда у себя](#1-запуск-бэкенда-у-себя)
2. [Подключение фронтенда](#2-подключение-фронтенда)
3. [Вход, сессия и CSRF](#3-вход-сессия-и-csrf)
4. [Экраны и откуда брать данные](#4-экраны-и-откуда-брать-данные)
5. [Сценарии с кодом](#5-сценарии-с-кодом)
6. [Все адреса API](#6-все-адреса-api)
7. [Фильтры](#7-фильтры)
8. [Модели ответов](#8-модели-ответов)
9. [Значения перечислений](#9-значения-перечислений)
10. [Ошибки](#10-ошибки)
11. [Типы TypeScript](#11-типы-typescript)
12. [Чего пока нет](#12-чего-пока-нет)

---

## 1. Запуск бэкенда у себя

Нужны **Java 21+** и запущенный **Docker Desktop**. Из папки `DobryShkaf`:

```bash
./mvnw spring-boot:run
```

Приложение само поднимет контейнеры из `compose.yaml`: PostgreSQL, хранилище фото (SeaweedFS, S3) и тестовый почтовый ящик Mailpit. Первый запуск занимает около минуты.

| Что | Адрес |
|---|---|
| API | http://localhost:8080 |
| Swagger (все запросы можно выполнить кнопками) | http://localhost:8080/swagger-ui.html |
| Описание API в формате OpenAPI | http://localhost:8080/v3/api-docs |
| Письма (подтверждение регистрации) | http://localhost:8025 |

**Тестовые пользователи.** Зарегистрируйтесь через API с любой почтой: письмо придёт в Mailpit, а не на настоящий адрес.

**Администратор.** Роль выдаётся пока только в базе. После смены роли нужно войти заново.

```bash
docker exec dobryshkaf-postgres-1 psql -U dobryshkaf -d dobryshkaf -c "update users set role = 'ADMIN' where email = 'you@example.com'"
```

**Пункты на карте.** Загрузить 10 тестовых пунктов по районам Екатеринбурга (скрипт спросит пароль администратора):

```bash
docker/seed/import-points.sh docker/seed/points-ekaterinburg-test.json you@example.com
```

---

## 2. Подключение фронтенда

Сервер хранит вход в cookie сессии и **не настраивает CORS**. Поэтому фронтенд должен обращаться к API с того же адреса, что и сама страница. В разработке для этого нужен прокси dev-сервера:

```js
// vite.config.js
export default {
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
};
```

Все запросы во фронтенде пишутся относительными путями: `fetch('/api/items')`, а не `fetch('http://localhost:8080/api/items')`.

В бою фронтенд и `/api` тоже должны отдаваться с одного домена, например через nginx. Если понадобится отдельный домен для API, бэкенду нужно будет добавить CORS с `allowCredentials`.

---

## 3. Вход, сессия и CSRF

- После `POST /api/auth/login` сервер ставит cookie `JSESSIONID` (HttpOnly). Браузер отправляет её сам, на фронтенде с ней ничего делать не нужно.
- Запросы `POST`, `PUT`, `DELETE` защищены от CSRF. Сервер кладёт токен в cookie `XSRF-TOKEN` (её можно читать из JavaScript), фронтенд копирует значение в заголовок `X-XSRF-TOKEN`. Без заголовка сервер ответит `403`.
- Токен **не нужен** только для `register`, `login` и `resend-confirmation`.
- Cookie с токеном появляется после первого запроса к серверу. Поэтому при загрузке приложения и сразу после входа вызывайте `GET /api/auth/me`: он заодно вернёт текущего пользователя или `401`, если сессии нет.

Готовая обёртка над `fetch`, через которую удобно делать все запросы:

```js
// src/api.js
function csrfToken() {
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
}

export async function api(path, { method = 'GET', json, form } = {}) {
  const headers = {};
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken() ?? '';
  if (json !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: json !== undefined ? JSON.stringify(json) : form,
  });

  if (response.status === 204) return null;
  const isJson = response.headers.get('content-type')?.includes('json');
  const body = isJson ? await response.json() : null;
  if (!response.ok) {
    // body: { code, message, fields } — см. раздел «Ошибки»
    throw Object.assign(new Error(body?.message ?? response.statusText), { status: response.status, ...body });
  }
  return body;
}
```

---

## 4. Экраны и откуда брать данные

Главная таблица: какой запрос нужен экрану и какие поля ответа куда выводить.

### Шапка сайта и профиль

| Что показать | Откуда взять |
|---|---|
| Вошёл ли пользователь | `GET /api/auth/me`: `200` — вошёл, `401` — гость |
| Имя в шапке | `me.name` |
| Пункт меню «Админка» | показывать, если `me.role === 'ADMIN'` |
| Кнопка «Выйти» | `POST /api/auth/logout`, ответ `204` |

### Регистрация и вход

| Что показать | Откуда взять |
|---|---|
| Форма регистрации | `POST /api/auth/register` с `email`, `name`, `password` и необязательным `phone` |
| Ошибки под полями | `error.fields`: ключ — имя поля, значение — готовый текст на русском |
| Экран «Проверьте почту» | после ответа `201` регистрации |
| Форма входа | `POST /api/auth/login` с `login` (почта или телефон в любом формате) и `password` |
| «Почта не подтверждена» | ответ `403` с `code: "EMAIL_NOT_CONFIRMED"`; кнопка «Отправить письмо ещё раз» вызывает `POST /api/auth/resend-confirmation` с `email` |
| «Неверный логин или пароль» | ответ `401` с `code: "INVALID_CREDENTIALS"` |

### Форма «Отдать вещь»

| Поле формы | Откуда взять варианты | Что отправлять |
|---|---|---|
| Категория (выпадающий список) | `GET /api/categories`: подпись `name`, значение `id` | `categoryId` |
| Размер | показывать и делать обязательным, если у выбранной категории `sizeRequired === true` | `size` |
| Состояние | `GET /api/item-conditions`: подпись `label`, значение `code` | `condition` |
| Район | `GET /api/districts`: подпись `name`, значение `id`; можно группировать по `city` | `districtId` |
| Название, описание | свободный ввод: название до 100 символов, описание до 2000 | `title`, `description` |
| Фото | 1–5 файлов JPEG, PNG или WebP до 5 МБ каждый | `POST /api/items/{id}/photos`, поле формы `file`, по одному |

Порядок запросов: создать черновик → загрузить фото → опубликовать (код в [разделе 5](#отдать-вещь)).

### Каталог

| Что показать | Откуда взять |
|---|---|
| Список карточек | `GET /api/items?…` → `items[]` |
| Фото на карточке | `items[i].photos[0].url` прямо в `<img src>`; если `photos` пуст — заглушка |
| Название, размер | `title`, `size` |
| Состояние | `condition` → подпись из `/api/item-conditions` (`code` → `label`) |
| Район | `district.name` |
| Автор | `owner.name`, рейтинг `owner.rating` (пока всегда `null`) |
| Бейдж «Зарезервировано» | `status === 'RESERVED'` |
| Пагинация | `page`, `totalPages`, `totalElements`; страницы считаются с нуля, по 20 записей |
| Фильтры | параметры из [раздела 7](#7-фильтры); варианты для выпадающих списков — из справочников |

### Карточка вещи

| Что показать | Откуда взять |
|---|---|
| Галерея | `photos[]` по порядку (`sortOrder`), картинка — `url` |
| Описание, категория, район | `description`, `category.name`, `district.name` |
| Дата публикации | `publishedAt` (UTC, ISO 8601) |
| Кнопки «Изменить», «Закрыть», управление фото | показывать, если `item.owner.id === me.id` или `me.role === 'ADMIN'` |
| Кнопка «Забрать» | пока не к чему подключать, см. [раздел 12](#12-чего-пока-нет) |

### Мои объявления

| Что показать | Откуда взять |
|---|---|
| Все свои объявления, включая черновики и закрытые | `GET /api/items?mine=true` |
| Бейдж статуса | `status`: `DRAFT` черновик, `PUBLISHED` опубликовано, `CLOSED` закрыто |
| Кнопка «Опубликовать» у черновика | `POST /api/items/{id}/publish` |

### Карта и список пунктов

Карта и список строятся из **одного** запроса `GET /api/points` с одними фильтрами, поэтому всегда показывают одинаковые пункты.

| Что показать | Откуда взять |
|---|---|
| Метка на карте | `latitude`, `longitude` (WGS 84, подходят для Яндекс Карт) |
| Иконка метки | `type`: `EXCHANGE` — пункт обмена, `COLLECTION` — пункт приёма |
| Приглушённая метка «Временно закрыт» | `temporarilyClosed === true` |
| Подпись в списке | `name`, `address`, `district.name`, `hours` |
| Фильтр по району без геолокации | `?districtId=` из `/api/districts` |
| Фильтр «Что принимают» | `?categoryId=` из `/api/categories` |

### Карточка пункта

| Что показать | Откуда взять |
|---|---|
| Название, организация | `name`, `organization.name` |
| Адрес, часы, контакты | `address`, `hours`, `contacts` (контакты — свободный текст) |
| Что принимают | `categories[].name` |
| Условия приёма | `conditions` |
| Как получить вещи | `receivingConditions`; есть только у `EXCHANGE`, у `COLLECTION` всегда `null` |
| «Данные проверены» | `verifiedAt` (дата `YYYY-MM-DD`) и `source` |

### Админка пунктов (только `role === 'ADMIN'`)

| Что показать | Откуда взять |
|---|---|
| Таблица всех пунктов, включая архивные | `GET /api/admin/points`; отбор по статусу — `?status=ACTIVE`, `TEMPORARILY_CLOSED` или `ARCHIVED` |
| Форма добавления | `POST /api/admin/points`; варианты для категорий и района — из справочников |
| Форма изменения | `PUT /api/admin/points/{id}`; тело как при создании |
| Переключатель «Временно закрыт» | поле `status` в `PUT`: `ACTIVE` или `TEMPORARILY_CLOSED` |
| Кнопка «В архив» | `POST /api/admin/points/{id}/archive` |
| Поле «Как получить вещи» | обязательно, только если `type === 'EXCHANGE'` |

---

## 5. Сценарии с кодом

### Вход

```js
const user = await api('/api/auth/login', {
  method: 'POST',
  json: { login: 'anna@example.com', password: 'secret-password' },
});
await api('/api/auth/me'); // получить cookie XSRF-TOKEN для следующих запросов
```

### «Отдать вещь»

```js
const draft = await api('/api/items', {
  method: 'POST',
  json: {
    title: 'Зимний комбинезон',
    description: 'Тёплый, носили один сезон',
    condition: 'LIKE_NEW',
    size: '122',
    categoryId,   // id из /api/categories
    districtId,   // id из /api/districts
  },
});

for (const file of fileInput.files) {
  const form = new FormData();
  form.append('file', file);
  await api(`/api/items/${draft.id}/photos`, { method: 'POST', form });
}

try {
  await api(`/api/items/${draft.id}/publish`, { method: 'POST' });
} catch (error) {
  if (error.code === 'ITEM_INCOMPLETE') showFieldErrors(error.fields);
  // ключи fields: description, condition, categoryId, size, districtId, photos
}
```

Черновик можно сохранить с одним названием и дописать позже через `PUT /api/items/{id}`.

> **Важно про `PUT`:** тело заменяет **все** поля объявления. Отправляйте весь объект целиком: незаполненное поле станет пустым. У опубликованного объявления нельзя очистить обязательное поле или удалить последнее фото — сервер ответит `400`.

### Каталог с фильтрами

```js
const params = new URLSearchParams({ q: 'комбинезон', size: '122', page: '0' });
const { items, totalPages } = await api(`/api/items?${params}`);
```

### Карта

```js
const points = await api(`/api/points?districtId=${districtId}`);
for (const point of points) {
  addMarker([point.latitude, point.longitude], {
    kind: point.type,                    // EXCHANGE | COLLECTION
    dimmed: point.temporarilyClosed,
  });
}
```

---

## 6. Все адреса API

Доступ: **гость** — без входа, **вход** — нужна сессия, **владелец** — автор объявления или администратор, **админ** — только роль `ADMIN`.

| Метод | Адрес | Доступ | Ответ | Что делает |
|---|---|---|---|---|
| GET | `/api/categories` | гость | 200 | Категории вещей |
| GET | `/api/districts` | гость | 200 | Районы |
| GET | `/api/item-conditions` | гость | 200 | Состояния вещи с подписями |
| POST | `/api/auth/register` | гость | 201 | Регистрация, письмо на почту |
| GET | `/api/auth/confirm?token=` | гость | 200 | Подтверждение почты по ссылке из письма |
| POST | `/api/auth/resend-confirmation` | гость | 202 | Письмо ещё раз (202 даже для неизвестной почты) |
| POST | `/api/auth/login` | гость | 200 | Вход по почте или телефону |
| GET | `/api/auth/me` | вход | 200 | Текущий пользователь |
| POST | `/api/auth/logout` | вход | 204 | Выход |
| GET | `/api/items` | гость | 200 | Каталог; `mine=true` требует входа |
| GET | `/api/items/{id}` | гость | 200 | Объявление; черновик и закрытое видит только владелец |
| POST | `/api/items` | вход | 201 | Создать черновик |
| PUT | `/api/items/{id}` | владелец | 200 | Изменить, тело заменяет все поля |
| POST | `/api/items/{id}/publish` | владелец | 200 | Опубликовать черновик |
| POST | `/api/items/{id}/close` | владелец | 200 | Закрыть, убрать из каталога |
| POST | `/api/items/{id}/photos` | владелец | 201 | Загрузить фото: multipart, поле `file` |
| DELETE | `/api/items/{id}/photos/{photoId}` | владелец | 200 | Удалить фото |
| GET | `/api/photos/{photoId}` | гость | 200 | Файл фото; фото черновика только владельцу |
| GET | `/api/points` | гость | 200 | Пункты для карты и списка |
| GET | `/api/points/{id}` | гость | 200 | Пункт; архивный отвечает 404 |
| GET | `/api/admin/points` | админ | 200 | Все пункты, включая архивные |
| GET | `/api/admin/points/{id}` | админ | 200 | Пункт, включая архивный |
| POST | `/api/admin/points` | админ | 201 | Добавить пункт |
| PUT | `/api/admin/points/{id}` | админ | 200 | Изменить пункт, отметить временное закрытие |
| POST | `/api/admin/points/{id}/archive` | админ | 200 | Убрать пункт в архив |

Изменяющие объявления запросы (`PUT`, `publish`, `close`, фото) возвращают объявление целиком, его можно сразу подставить в состояние без повторного `GET`.

`POST /api/auth/logout` обрабатывает сам Spring Security, поэтому в Swagger его нет. Он работает как описано: нужен заголовок `X-XSRF-TOKEN`, тела нет, ответ `204`.

---

## 7. Фильтры

### `GET /api/items`

| Параметр | Пример | Что делает |
|---|---|---|
| `q` | `комбинезон` | Часть названия, без учёта регистра |
| `categoryId` | uuid | Категория |
| `districtId` | uuid | Район |
| `condition` | `LIKE_NEW` | Состояние |
| `size` | `122` | Размер, точное совпадение без учёта регистра |
| `available` | `true` | `true` — только доступные, `false` — только зарезервированные |
| `mine` | `true` | Только свои объявления во всех статусах; нужен вход |
| `page` | `0` | Номер страницы с нуля, по 20 записей, от новых к старым |

### `GET /api/points`

| Параметр | Пример | Что делает |
|---|---|---|
| `type` | `EXCHANGE` | Пункт обмена или приёма (`COLLECTION`) |
| `categoryId` | uuid | Пункты, которые принимают категорию |
| `districtId` | uuid | Район |
| `q` | `Ленина` | Часть названия или адреса |
| `openOnly` | `true` | Скрыть временно закрытые |

Ответ `/api/points` — полный список без страниц, отсортированный по названию. `GET /api/admin/points` принимает те же параметры и дополнительно `status`.

---

## 8. Модели ответов

### Пользователь (`/api/auth/me`, `login`, `register`)

```json
{
  "id": "57ca6352-e6b1-4c5f-ad31-139998fe5edf",
  "email": "anna@example.com",
  "phone": "+79991234567",
  "name": "Анна",
  "role": "USER",
  "emailConfirmed": true
}
```

### Объявление

```json
{
  "id": "0c943136-6c0d-400d-97bd-7abef3e0b29f",
  "title": "Зимний комбинезон",
  "description": "Тёплый, носили один сезон",
  "condition": "LIKE_NEW",
  "size": "122",
  "status": "PUBLISHED",
  "category": { "id": "…", "code": "kids-clothing", "name": "Детская одежда", "sizeRequired": true },
  "district": { "id": "…", "city": "Екатеринбург", "name": "Кировский" },
  "owner": { "id": "…", "name": "Анна", "rating": null },
  "photos": [
    { "id": "9dbdd415-…", "url": "/api/photos/9dbdd415-…", "contentType": "image/jpeg", "sortOrder": 0 }
  ],
  "publishedAt": "2026-11-08T07:53:41.512Z",
  "createdAt": "2026-11-08T07:52:10.004Z",
  "updatedAt": "2026-11-08T07:53:41.512Z"
}
```

Почту и телефон автора сервер посторонним не отдаёт. У черновика `category`, `district`, `condition` и другие поля могут быть `null`.

### Страница каталога

```json
{ "items": [], "page": 0, "size": 20, "totalElements": 21, "totalPages": 2 }
```

### Пункт

```json
{
  "id": "…",
  "type": "EXCHANGE",
  "name": "Добрый пункт",
  "organization": { "id": "…", "name": "Фонд «Пример»" },
  "address": "Екатеринбург, ул. Примерная, 1",
  "district": { "id": "…", "city": "Екатеринбург", "name": "Кировский" },
  "latitude": 56.851,
  "longitude": 60.648,
  "hours": "Пн–Пт 10:00–19:00",
  "contacts": "+7 000 000-00-00",
  "categories": [{ "id": "…", "code": "kids-clothing", "name": "Детская одежда", "sizeRequired": true }],
  "conditions": "Чистые и целые вещи",
  "receivingConditions": "Бесплатно",
  "source": "Сайт организации",
  "verifiedAt": "2026-11-01",
  "status": "ACTIVE",
  "temporarilyClosed": false,
  "updatedAt": "2026-11-08T07:45:00.123Z"
}
```

### Тело `POST`/`PUT /api/admin/points`

Все поля обязательны, кроме `receivingConditions` (нужно только для `EXCHANGE`) и `status`. Организация указывается названием: если такой нет, сервер её создаст. Дата проверки не может быть в будущем.

```json
{
  "type": "EXCHANGE",
  "name": "Добрый пункт",
  "organization": "Фонд «Пример»",
  "address": "Екатеринбург, ул. Примерная, 1",
  "districtId": "uuid района",
  "latitude": 56.851,
  "longitude": 60.648,
  "hours": "Пн–Пт 10:00–19:00",
  "contacts": "+7 000 000-00-00",
  "categoryIds": ["uuid категории"],
  "conditions": "Чистые и целые вещи",
  "receivingConditions": "Бесплатно",
  "source": "Сайт организации",
  "verifiedAt": "2026-11-01",
  "status": "ACTIVE"
}
```

Время во всех ответах — UTC в формате ISO 8601.

---

## 9. Значения перечислений

| Поле | Значения |
|---|---|
| `condition` | `NEW` новое, `LIKE_NEW` почти новое, `USED` поношенное, `NEEDS_CARE` требует ухода. Подписи отдаёт `/api/item-conditions` |
| `item.status` | `DRAFT` черновик, `PUBLISHED` опубликовано, `RESERVED` зарезервировано, `TRANSFERRED` передано, `CLOSED` закрыто. `RESERVED` и `TRANSFERRED` появятся вместе с передачами |
| `point.type` | `EXCHANGE` пункт обмена, `COLLECTION` пункт приёма |
| `point.status` | `ACTIVE` работает, `TEMPORARILY_CLOSED` временно закрыт, `ARCHIVED` в архиве (виден только в админке) |
| `user.role` | `USER`, `VOLUNTEER`, `ORGANIZER`, `ADMIN` |
| `category.code` | `kids-clothing`, `adult-clothing`, `shoes` — размер обязателен; `toys`, `books`, `furniture` — без размера |

---

## 10. Ошибки

Ошибки приложения приходят в одном формате. Сообщения в `message` и `fields` уже на русском, их можно показывать пользователю как есть.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Проверьте заполнение полей",
  "fields": { "email": "Некорректная почта", "password": "Пароль должен содержать не меньше 8 символов" }
}
```

| HTTP | `code` | Когда |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Поле не прошло проверку; подробности в `fields` |
| 400 | `MALFORMED_REQUEST` | Неверный JSON, неизвестное значение перечисления, некорректный UUID |
| 400 | `ITEM_INCOMPLETE` | Не хватает полей для публикации; список в `fields` |
| 400 | `UNSUPPORTED_PHOTO_FORMAT` | Файл не JPEG, PNG или WebP |
| 400 | `PHOTO_TOO_LARGE` | Фото больше 5 МБ |
| 400 | `PHOTO_LIMIT_REACHED` | Уже 5 фото |
| 400 | `EMPTY_PHOTO` | Пустой файл |
| 400 | `INVALID_CONFIRMATION_TOKEN` | Ссылка из письма недействительна или устарела |
| 401 | `INVALID_CREDENTIALS` | Неверный логин или пароль |
| 401 | `UNAUTHORIZED` | Нужен вход (например, `mine=true` без сессии) |
| 403 | `EMAIL_NOT_CONFIRMED` | Пароль верный, но почта не подтверждена |
| 403 | `FORBIDDEN` | Чужое объявление |
| 404 | `NOT_FOUND` | Объявление, фото или пункт не найдены или скрыты |
| 409 | `USER_ALREADY_EXISTS` | Почта или телефон уже зарегистрированы |
| 409 | `ITEM_NOT_DRAFT` | Опубликовать можно только черновик |
| 409 | `ITEM_NOT_EDITABLE` | Закрытое объявление изменить нельзя |
| 409 | `POINT_ARCHIVED` | Архивный пункт изменить нельзя |
| 409 | `CONFLICT` | Одновременный запрос изменил те же данные; повторите |

**Ответы без поля `code`:**

- `401` с пустым телом — запрос к закрытому адресу без сессии. Отправьте пользователя на вход.
- `403` в стандартном формате Spring — нет заголовка `X-XSRF-TOKEN` или роли администратора. Если токен отсутствует, вызовите `GET /api/auth/me` и повторите запрос.

---

## 11. Типы TypeScript

Типы запросов и ответов можно сгенерировать из описания API, пока бэкенд запущен:

```bash
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts
```

В схемах запросов есть служебные поля проверок: `phoneValid` и `passwordWithinLimit` у регистрации, `receivingConditionsProvided` и `statusAllowed` у пункта. Отправлять их не нужно, сервер их игнорирует; в сгенерированных типах их можно сделать необязательными.

Если README и Swagger расходятся, верен Swagger на работающем сервере. Исключение — выход (`POST /api/auth/logout`), которого в Swagger нет (см. [раздел 6](#6-все-адреса-api)).

---

## 12. Чего пока нет

- **Передачи вещей.** Кнопку «Забрать» пока не к чему подключать: запрос, резервирование, подтверждение и завершение — следующий этап.
- **Чат, отзывы, экологический трекер, восстановление пароля** — ещё не реализованы.
- **Удаление объявлений и пунктов.** Объявление закрывается, пункт уходит в архив: ТЗ требует сохранять историю.
- **Страница подтверждения почты на фронтенде.** Ссылка из письма сейчас открывает JSON бэкенда. Если нужна своя страница, договоримся о её адресе.
- **Выдача пунктов по страницам или по видимой области карты.** Для пилота пунктов несколько десятков, они приходят одним списком.
