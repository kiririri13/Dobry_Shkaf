# Добрый шкаф

Бэкенд платформы «Добрый шкаф»: регистрация и вход, восстановление пароля, объявления «Отдать вещь» с фото, каталог, диалоги по вещам с доставкой через WebSocket, передача вещей, карта пунктов приёма и обмена, профиль, избранное, отзывы, модерация и экологический учёт.

Этот README написан для фронтенд-разработчика: как запустить сервер у себя, как к нему подключиться и **какие данные брать из каких запросов** для каждого экрана.

## Содержание

1. [Запуск бэкенда у себя](#1-запуск-бэкенда-у-себя)
2. [Подключение фронтенда](#2-подключение-фронтенда)
3. [Вход, сессия и CSRF](#3-вход-сессия-и-csrf)
4. [Экраны и откуда брать данные](#4-экраны-и-откуда-брать-данные)
5. [Сценарии с кодом](#5-сценарии-с-кодом)
6. [Диалоги, передачи и сброс пароля](#6-диалоги-передачи-и-сброс-пароля)
7. [Все адреса API](#7-все-адреса-api)
8. [Фильтры](#8-фильтры)
9. [Модели ответов](#9-модели-ответов)
10. [Значения перечислений](#10-значения-перечислений)
11. [Ошибки](#11-ошибки)
12. [Типы TypeScript](#12-типы-typescript)
13. [Профиль, модерация и статистика](#13-профиль-модерация-и-статистика)
14. [Чего пока нет](#14-чего-пока-нет)

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
      '/ws': { target: 'ws://localhost:8080', ws: true },
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
- Токен **не нужен** только для `register`, `login`, `resend-confirmation`, `password-reset` и `password-reset/confirm`.
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
| Фильтры | параметры из [раздела 7](#8-фильтры); варианты для выпадающих списков — из справочников |

### Карточка вещи

| Что показать | Откуда взять |
|---|---|
| Галерея | `photos[]` по порядку (`sortOrder`), картинка — `url` |
| Описание, категория, район | `description`, `category.name`, `district.name` |
| Дата публикации | `publishedAt` (UTC, ISO 8601) |
| Кнопки «Изменить», «Закрыть», управление фото | показывать, если `item.owner.id === me.id` или `me.role === 'ADMIN'` |
| Кнопки «Написать» и «Забрать» | скрыть для своей вещи; запросы — в [разделе 6](#6-диалоги-передачи-и-сброс-пароля) |

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
| Метка на карте | `latitude`, `longitude` (WGS 84, подходят для Яндекс Карт и Leaflet) |
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

## 6. Диалоги, передачи и сброс пароля

### Диалог по вещи

На каждую пару «вещь + заинтересованный пользователь» есть **один** диалог. Владелец вещи сам диалог не открывает: он отвечает в тех, что открыли ему.

| Что показать | Откуда взять |
|---|---|
| Кнопка «Написать» в карточке вещи | `POST /api/chats` с `{ itemId }`. Новый диалог — `201`, уже существующий — `200` с тем же `id`. Свою вещь — `409 OWN_ITEM` |
| Список диалогов | `GET /api/chats`: `item`, `counterpart.name`, `lastMessage`, `unreadCount`, свежие сверху |
| История | `GET /api/chats/{id}/messages` — последние 50 сообщений по возрастанию `seq` и флаг `hasMore` |
| Подгрузка старых при прокрутке вверх | `?beforeSeq=<seq самого старого на экране>&limit=50` (не больше 100) |
| Отправка | `POST /api/chats/{id}/messages` с `{ text, requestId }`; `text` непустой, до 2000 символов |
| «Прочитано» | `POST /api/chats/{id}/read` с `{ upToSeq }` — отмечает прочитанными чужие сообщения до этого номера, ответ `204` |
| Галочки «прочитано» у своих сообщений | `readAt !== null` |

Сообщения нумеруются в диалоге по порядку: поле `seq` (1, 2, 3, …). По нему строится вся подгрузка.

**`requestId` защищает от двойной отправки.** Фронтенд создаёт его один раз на сообщение (`crypto.randomUUID()`) и повторяет при повторной попытке, например после обрыва сети. Повтор с тем же `requestId` вернёт `200` и уже сохранённое сообщение, дубликата не будет.

```js
async function sendMessage(chatId, text) {
  const requestId = crypto.randomUUID();
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      return await api(`/api/chats/${chatId}/messages`, { method: 'POST', json: { text, requestId } });
    } catch (error) {
      if (error.status) throw error;   // ответ сервера: повторять бессмысленно
    }                                  // сетевая ошибка: пробуем ещё раз с тем же requestId
  }
  throw new Error('Не удалось отправить сообщение');
}
```

### Доставка в реальном времени (WebSocket)

Новые сообщения, отметки о прочтении и изменения передачи приходят по **STOMP поверх WebSocket**.

| | |
|---|---|
| Адрес | `/ws` (тот же домен, что и страница) |
| Вход | cookie сессии, отдельный токен не нужен. Без входа соединение не устанавливается |
| Подписка | `/topic/chats.{chatId}` — только участникам диалога, остальным сервер отвечает ошибкой |
| Отправка | только через `POST /api/chats/{id}/messages`; отправлять сообщения в сам сокет нельзя |

В `vite.config.js` нужно добавить прокси для сокета. `changeOrigin` не включайте: сервер принимает подключение только с того же адреса, что и страница.

```js
proxy: {
  '/api': 'http://localhost:8080',
  '/ws': { target: 'ws://localhost:8080', ws: true },
},
```

Каждое событие — JSON `{ type, payload }`:

| `type` | `payload` | Что сделать на фронтенде |
|---|---|---|
| `MESSAGE_CREATED` | сообщение, как в истории | Добавить в ленту, если такого `id` ещё нет (своё сообщение приходит и в ответе `POST`, и в сокет) |
| `MESSAGES_READ` | `{ readerId, upToSeq }` | Если `readerId` — собеседник, отметить свои сообщения с `seq <= upToSeq` прочитанными |
| `TRANSFER_UPDATED` | передача, как в `/api/transfers` | Обновить статус передачи. `myRole` и `availableActions` в событии посчитаны для того, кто совершил действие, поэтому кнопки перезапросите через `GET /api/transfers/{id}` |

**После переподключения** сокет не досылает пропущенное. Запомните `seq` последнего сообщения на экране и после переподключения загрузите разницу из истории: `GET /api/chats/{id}/messages?afterSeq=<последний seq>`. Если `hasMore === true`, повторите запрос с новым последним `seq`.

```js
// npm install @stomp/stompjs
import { Client } from '@stomp/stompjs';

export function watchChat(chatId, { lastSeq, onEvent }) {
  const client = new Client({
    brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
    reconnectDelay: 3000,
    onConnect: async () => {
      client.subscribe(`/topic/chats.${chatId}`, (frame) => onEvent(JSON.parse(frame.body)));
      // догружаем то, что пришло, пока соединения не было
      let page;
      do {
        page = await api(`/api/chats/${chatId}/messages?afterSeq=${lastSeq()}`);
        page.messages.forEach((message) => onEvent({ type: 'MESSAGE_CREATED', payload: message }));
      } while (page.hasMore);
    },
  });
  client.activate();
  return () => client.deactivate();
}
```

### Передача вещи

Кнопки передачи определяет сервер: показывайте те, что есть в `availableActions`, и вызывайте соответствующую команду. Новый статус тоже считает сервер, фронтенд его не вычисляет.

| `availableActions` | Кнопка | Команда |
|---|---|---|
| `SELECT` | «Отдать этому человеку» (видит владелец) | `POST /api/transfers/{id}/select` |
| `CONFIRM` | «Вещь передана» / «Вещь получена» | `POST /api/transfers/{id}/confirm` |
| `CANCEL` | «Отклонить», «Отозвать заявку», «Отменить» или «Попросить отмену» | `POST /api/transfers/{id}/cancel` |
| `ACCEPT_CANCELLATION` | «Согласиться на отмену» | `POST /api/transfers/{id}/cancellation/accept` |
| `DECLINE_CANCELLATION` | «Продолжить передачу» | `POST /api/transfers/{id}/cancellation/decline` |

| Что показать | Откуда взять |
|---|---|
| Кнопка «Забрать» в карточке вещи | `POST /api/transfers` с `{ itemId }`. Ответ — заявка и `chatId` диалога с владельцем. Повтор вернёт `200` и ту же заявку |
| Заявки на мою вещь (для владельца) | `GET /api/transfers?itemId=...`; можно добавить `&status=REQUESTED` |
| Мои передачи | `GET /api/transfers` без параметров — где я отдаю или получаю |
| Моя роль | `myRole`: `GIVER` отдаёт, `RECEIVER` получает |
| Кто уже подтвердил | `giverConfirmed`, `receiverConfirmed` |
| Кто попросил отмену | `cancellationRequestedBy`: `GIVER` или `RECEIVER` |
| История передачи | `GET /api/transfers/{id}/events`: кто, какая команда, из какого статуса в какой |

Как меняется статус:

| Сейчас | Действие | Станет |
|---|---|---|
| `REQUESTED` заявка | владелец выбирает получателя | `RESERVED`, остальные заявки — `REJECTED`, вещь пропадает из каталога |
| `REQUESTED` | владелец отклоняет (`cancel`) | `REJECTED` |
| `REQUESTED` | получатель отзывает (`cancel`) | `CANCELLED` |
| `RESERVED` резерв | первое подтверждение | `AWAITING_CONFIRMATION` |
| `RESERVED` | любой отменяет | `CANCELLED`, вещь возвращается в каталог |
| `AWAITING_CONFIRMATION` | второе подтверждение | `COMPLETED`, вещь `TRANSFERRED` |
| `AWAITING_CONFIRMATION` | любой просит отмену | `CANCELLATION_REQUESTED` |
| `CANCELLATION_REQUESTED` | второй участник соглашается | `CANCELLED`, вещь возвращается в каталог |
| `CANCELLATION_REQUESTED` | второй участник отказывается | `AWAITING_CONFIRMATION` |

- Пока получатель не выбран, на одну вещь может быть несколько заявок; от одного человека — только одна активная.
- Повтор той же команды безопасен: сервер вернёт `200` и текущее состояние. Например, повторное первое подтверждение оставит `AWAITING_CONFIRMATION`.
- Команда, которая не подходит к текущему статусу, отвечает `409 TRANSFER_STATE_CONFLICT`. Скорее всего, второй участник успел что-то сделать: перезапросите передачу.
- Пока обсуждается отмена, подтвердить передачу нельзя.
- Посторонний получает `403` на любую команду, на просмотр передачи и диалога и на подписку в сокете.
- Если владелец закрывает объявление, открытые заявки автоматически становятся `REJECTED`.

### Восстановление пароля

| Шаг | Запрос |
|---|---|
| Форма «Забыли пароль?» | `POST /api/auth/password-reset` с `{ email }`. Ответ всегда `202` без тела — и для известной, и для неизвестной почты. Покажите «Если такая почта есть, мы отправили письмо» |
| Письмо | приходит в Mailpit (http://localhost:8025) со ссылкой `<адрес>/reset-password?token=...`. Ссылка действует 30 минут и срабатывает один раз |
| Страница `/reset-password` (её нужно сделать на фронтенде) | берёт `token` из адреса и отправляет `POST /api/auth/password-reset/confirm` с `{ token, newPassword }` |
| Успех | `200` с `message`; дальше обычный вход с новым паролем |
| Ссылка устарела или уже использована | `400` с `code: "INVALID_RESET_TOKEN"`; предложите запросить письмо ещё раз |

Оба запроса работают без входа и без заголовка `X-XSRF-TOKEN`. Новый пароль проверяется так же, как при регистрации: не меньше 8 символов.

По умолчанию ссылка ведёт на `http://localhost:8080/reset-password`. Чтобы она открывала dev-сервер фронтенда, запустите бэкенд с переменной `APP_PASSWORD_RESET_URL=http://localhost:5173/reset-password`.

---

## 7. Все адреса API

Доступ: **гость** — без входа, **вход** — нужна сессия, **владелец** — автор объявления или администратор, **админ** — только роль `ADMIN`, **участник** — двое участников диалога или передачи, **владелец вещи** — только автор объявления.

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
| POST | `/api/auth/password-reset` | гость | 202 | Письмо со ссылкой сброса (202 даже для неизвестной почты) |
| POST | `/api/auth/password-reset/confirm` | гость | 200 | Новый пароль по токену из письма |
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
| POST | `/api/chats` | вход | 201 / 200 | Открыть диалог по вещи или вернуть существующий |
| GET | `/api/chats` | вход | 200 | Мои диалоги |
| GET | `/api/chats/{id}` | участник | 200 | Диалог |
| GET | `/api/chats/{id}/messages` | участник | 200 | История: `beforeSeq`, `afterSeq`, `limit` |
| POST | `/api/chats/{id}/messages` | участник | 201 / 200 | Отправить сообщение; повтор с тем же `requestId` — 200 |
| POST | `/api/chats/{id}/read` | участник | 204 | Отметить прочитанным до `upToSeq` |
| POST | `/api/transfers` | вход | 201 / 200 | Заявка «Забрать»; повтор — 200 и та же заявка |
| GET | `/api/transfers` | вход | 200 | Мои передачи; `itemId`, `status` |
| GET | `/api/transfers/{id}` | участник | 200 | Передача |
| GET | `/api/transfers/{id}/events` | участник | 200 | История передачи |
| POST | `/api/transfers/{id}/select` | владелец вещи | 200 | Выбрать получателя |
| POST | `/api/transfers/{id}/confirm` | участник | 200 | Подтвердить передачу |
| POST | `/api/transfers/{id}/cancel` | участник | 200 | Отклонить, отозвать, отменить или попросить отмену |
| POST | `/api/transfers/{id}/cancellation/accept` | участник | 200 | Согласиться на отмену |
| POST | `/api/transfers/{id}/cancellation/decline` | участник | 200 | Отказаться от отмены |
| WS | `/ws` | вход | — | STOMP; подписка `/topic/chats.{chatId}` для участников |

Изменяющие объявления запросы (`PUT`, `publish`, `close`, фото) возвращают объявление целиком, его можно сразу подставить в состояние без повторного `GET`.

`POST /api/auth/logout` обрабатывает сам Spring Security, поэтому в Swagger его нет. Он работает как описано: нужен заголовок `X-XSRF-TOKEN`, тела нет, ответ `204`.

---

## 8. Фильтры

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

## 9. Модели ответов

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

## 10. Значения перечислений

| Поле | Значения |
|---|---|
| `condition` | `NEW` новое, `LIKE_NEW` почти новое, `USED` поношенное, `NEEDS_CARE` требует ухода. Подписи отдаёт `/api/item-conditions` |
| `item.status` | `DRAFT` черновик, `PUBLISHED` опубликовано, `RESERVED` зарезервировано, `TRANSFERRED` передано, `CLOSED` закрыто. `RESERVED` и `TRANSFERRED` ставит передача |
| `transfer.status` | `REQUESTED` заявка, `RESERVED` зарезервировано, `AWAITING_CONFIRMATION` ждёт второго подтверждения, `CANCELLATION_REQUESTED` обсуждается отмена, `COMPLETED` передано, `REJECTED` отклонено, `CANCELLED` отменено |
| `myRole`, `cancellationRequestedBy` | `GIVER` отдаёт, `RECEIVER` получает |
| `availableActions[]` | `SELECT`, `CONFIRM`, `CANCEL`, `ACCEPT_CANCELLATION`, `DECLINE_CANCELLATION` |
| событие сокета `type` | `MESSAGE_CREATED`, `MESSAGES_READ`, `TRANSFER_UPDATED` |
| `point.type` | `EXCHANGE` пункт обмена, `COLLECTION` пункт приёма |
| `point.status` | `ACTIVE` работает, `TEMPORARILY_CLOSED` временно закрыт, `ARCHIVED` в архиве (виден только в админке) |
| `user.role` | `USER`, `VOLUNTEER`, `ORGANIZER`, `ADMIN` |
| `category.code` | `kids-clothing`, `adult-clothing`, `shoes` — размер обязателен; `toys`, `books`, `furniture` — без размера |

---

## 11. Ошибки

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
| 400 | `INVALID_RESET_TOKEN` | Ссылка сброса пароля устарела или уже использована |
| 401 | `INVALID_CREDENTIALS` | Неверный логин или пароль |
| 401 | `UNAUTHORIZED` | Нужен вход (например, `mine=true` без сессии) |
| 403 | `EMAIL_NOT_CONFIRMED` | Пароль верный, но почта не подтверждена |
| 403 | `FORBIDDEN` | Чужое объявление, чужой диалог или чужая передача |
| 404 | `NOT_FOUND` | Объявление, фото или пункт не найдены или скрыты |
| 409 | `USER_ALREADY_EXISTS` | Почта или телефон уже зарегистрированы |
| 409 | `ITEM_NOT_DRAFT` | Опубликовать можно только черновик |
| 409 | `ITEM_NOT_EDITABLE` | Закрытое объявление изменить нельзя |
| 409 | `POINT_ARCHIVED` | Архивный пункт изменить нельзя |
| 409 | `OWN_ITEM` | Нельзя писать о своей вещи или забирать её |
| 409 | `ITEM_NOT_AVAILABLE` | Вещь уже зарезервирована, передана или закрыта |
| 409 | `TRANSFER_STATE_CONFLICT` | Команда не подходит к текущему статусу передачи; перезапросите её |
| 409 | `CONFLICT` | Одновременный запрос изменил те же данные; повторите |

**Ответы без поля `code`:**

- `401` с пустым телом — запрос к закрытому адресу без сессии. Отправьте пользователя на вход.
- `403` в стандартном формате Spring — нет заголовка `X-XSRF-TOKEN` или роли администратора. Если токен отсутствует, вызовите `GET /api/auth/me` и повторите запрос.

---

## 12. Типы TypeScript

Типы запросов и ответов можно сгенерировать из описания API, пока бэкенд запущен:

```bash
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts
```

В схемах запросов есть служебные поля проверок: `phoneValid` и `passwordWithinLimit` у регистрации, `receivingConditionsProvided` и `statusAllowed` у пункта. Отправлять их не нужно, сервер их игнорирует; в сгенерированных типах их можно сделать необязательными.

Если README и Swagger расходятся, верен Swagger на работающем сервере. Исключение — выход (`POST /api/auth/logout`), которого в Swagger нет (см. [раздел 7](#7-все-адреса-api)).

---

## 13. Профиль, модерация и статистика

Новые запросы доступны в Swagger. Изменяющие запросы используют сессию и заголовок `X-XSRF-TOKEN` по правилам из раздела 3.

### Профиль и отзывы

| Запрос | Данные и поведение |
|---|---|
| `GET /api/profile/me` | Собственные сведения в `profile`, почта в `email`, телефон в `phone`. |
| `PUT /api/profile/me` | Передайте `name` и `districtId`. Значение `null` очищает район. |
| `GET /api/profiles/{id}` | Публичные имя, район, средняя оценка и число завершённых передач. |
| `GET /api/items?mine=true` | Свои объявления во всех статусах. |
| `GET /api/profile/me/history` | Завершённые передачи, где пользователь отдавал или получал вещь. |
| `GET /api/profile/me/favorites` | Избранные объявления и пункты в массивах `items` и `points`. Скрытые, закрытые и архивные записи исключаются из выдачи. |
| `PUT /api/profile/me/favorites/{type}/{id}` | Добавить объект. `type` принимает `ITEMS` или `POINTS`. Повтор возвращает тот же `204`. |
| `DELETE /api/profile/me/favorites/{type}/{id}` | Удалить запись, в том числе если объект больше не виден в выдаче. Ответ `204`. |
| `POST /api/transfers/{id}/reviews` | Один отзыв от каждого участника после подтверждённой передачи. Передайте `rating` от 1 до 5 и необязательный `text` до 1000 символов. Повтор возвращает `409 REVIEW_EXISTS`. |
| `GET /api/profiles/{id}/reviews?page=0` | До 20 видимых отзывов, начиная с новых. |

### Модерация

| Запрос | Данные и поведение |
|---|---|
| `GET /api/admin/items?status=PENDING&page=0` | Очередь опубликованных и зарезервированных объявлений с описанием и фото. Фильтр `status` принимает `PENDING`, `REVIEWED`, `HIDDEN`. Без фильтра видна вся очередь. |
| `POST /api/admin/items/{id}/review` | Отметить проверку. |
| `POST /api/admin/items/{id}/hide` | Скрыть. Передайте `reason`. Вещь исчезает из каталога и избранного, ожидающие запросы отклоняются. Действующий резерв и история сохраняются. |
| `POST /api/admin/items/{id}/restore` | Вернуть видимость опубликованной или зарезервированной вещи. |
| `POST /api/admin/reviews/{id}/hide` | Скрыть отзыв с `reason` и пересчитать среднюю оценку. |
| `POST /api/admin/users/{id}/block` | Заблокировать аккаунт с `reason`. Сервер отклоняет вход, создание и публикацию объявлений и отправку сообщений через ранее выданную сессию. |
| `GET /api/admin/moderation-log?page=0` | Журнал действий, включая просмотры переписки. |

`moderationStatus` приходит в карточке отдельно от статуса объявления. Для этих маршрутов нужна роль `ADMIN`.

### Жалобы и история диалога

- Перед отправкой жалобы на чат или сообщение покажите предупреждение из `GET /api/complaints/chat-notice`.
- Отправьте `POST /api/complaints` с `targetType`, `targetId`, `reason`. Допустимые типы — `ITEM`, `POINT`, `PROFILE`, `CHAT`, `MESSAGE`. Для последних двух сервер проверяет участие заявителя в диалоге.
- Для очереди и карточки используйте `GET /api/admin/complaints?status=OPEN&page=0` и `GET /api/admin/complaints/{id}`. Эти маршруты доступны ролям `ADMIN` и `MODERATOR`. Роль `MODERATOR` выдаётся через базу так же, как `ADMIN`.
- В карточке открытой жалобы с `chatId` загружайте `GET /api/admin/complaints/{id}/chats/{chatId}/messages`. Запрос возвращает участников, объявление, связанные передачи и страницу сообщений в `history`. Параметры `beforeSeq`, `afterSeq` и `limit` работают как в обычном чате. Каждый успешный запрос истории записывается в журнал.
- Для решения передайте `outcome` (`UPHELD` или `DISMISSED`) и `explanation` в `POST /api/admin/complaints/{id}/close`. Закрытие прекращает доступ к истории через эту карточку. `POST /api/admin/complaints/{id}/reopen` возобновляет рассмотрение.

### Экологический учёт

`GET /api/eco/methodology` отдаёт название показателя, единицу, условие замещения и текущий справочник. Коды категорий в исходной версии — `COTTON_TSHIRT` для хлопковой футболки, `POLYESTER_TSHIRT` для полиэстеровой футболки, `JEANS` для джинсов, `COTTON_SHIRT` для хлопковой рубашки.

Владелец задаёт состав в `PUT /api/items/{id}/eco` до первого закрытия или завершения передачи. Состав из двух футболок и одних джинсов передаётся так.

```json
{
  "lines": [
    {"category": "COTTON_TSHIRT", "quantity": 2, "weightKg": 0.2, "weightMode": "PER_UNIT"},
    {"category": "JEANS", "quantity": 1}
  ]
}
```

`weightMode` принимает `PER_UNIT` для массы единицы или `BATCH` для массы всей позиции без упаковки. В примере учитывается 0,4 кг известной массы и 33,6 кг CO₂-экв. Для повторной передачи укажите только `previousLineId` вместо полей позиции. Идентификаторы доступных позиций есть в собственном экологическом расчёте или в `GET /api/transfers/{id}/eco-lines` после передачи. Одна исходная позиция даёт вклад один раз.

`POST /api/items/{id}/close` начисляет вклад владельцу опубликованной вещи, в том числе при закрытии без получателя. В сценарии передачи вклад начисляется после подтверждений обеих сторон. `POST /api/items/{id}/reopen` отменяет вклад закрытого объявления. При новом закрытии сервер восстанавливает запись с прежними коэффициентами и проверяет, учитывалась ли уже исходная позиция.

`GET /api/items/{id}/eco` отдаёт владельцу состав, сохранённый расчёт и историю пересчётов. Новые версии коэффициентов добавляются в справочник. Исторический расчёт сохраняется, а явный `POST /api/admin/eco/items/{id}/recalculate` с `reason` записывает новую версию отдельно и обновляет итог.

`GET /api/stats` и `GET /api/profile/me/stats` возвращают счётчики завершённых передач и закрытых объявлений, количество учтённых и оценённых вещей, известную массу и сумму CO₂. Если коэффициента нет, API возвращает пояснение в `estimateNotice`.

Партии в пунктах регистрируются через `PUT /api/admin/eco/point-batches/{id}` с `pointId`, `kind`, `quantity`, `weightKg`. Передайте один идентификатор для повторных запросов той же партии. Сбор в контейнер (`CONTAINER_COLLECTION`) и подтверждённая выдача (`CONFIRMED_DISTRIBUTION`) отражаются отдельными строками в общей статистике.

### Проверка бэкенда

`./mvnw test` запускает тесты, включая `WeekFourIntegrationTest` с PostgreSQL и S3 через Testcontainers. Сценарии интерфейса из материалов аналитика проверяются на тестовом стенде с подключённым фронтендом.

## 14. Чего пока нет

- **Отправка сообщений с фото.** В диалоге пока только текст.
- **Страница `/reset-password` на фронтенде.** Ссылка из письма ведёт на неё, страницу нужно сделать.
- **Несколько экземпляров бэкенда.** События сокета разносятся внутри одного сервера; для нескольких понадобится внешний брокер.
- **Удаление объявлений и пунктов.** Объявление закрывается, пункт уходит в архив: ТЗ требует сохранять историю.
- **Страница подтверждения почты на фронтенде.** Ссылка из письма сейчас открывает JSON бэкенда. Если нужна своя страница, договоримся о её адресе.
- **Выдача пунктов по страницам или по видимой области карты.** Для пилота пунктов несколько десятков, они приходят одним списком.
