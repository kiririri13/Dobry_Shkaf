#!/usr/bin/env bash
# Загружает карточки пунктов из JSON-файла через API администратора.
#
# Использование: docker/seed/import-points.sh <файл.json> <почта администратора>
# Пароль запрашивается при запуске. Адрес сервера задаётся в API_URL (по умолчанию http://localhost:8080).
#
# В файле категории указаны кодами, а район — городом и названием, как их удобнее заполнять аналитику;
# скрипт сам подставляет их id из справочников. Повторный запуск создаст дубликаты.
set -euo pipefail

FILE=${1:?Укажите JSON-файл с пунктами}
LOGIN=${2:?Укажите почту администратора}
API_URL=${API_URL:-http://localhost:8080}

read -rsp "Пароль администратора: " PASSWORD
echo
export LOGIN PASSWORD

JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

# Пароль передаётся через stdin, чтобы не попасть в список процессов
login_status=$(jq -n '{login: env.LOGIN, password: env.PASSWORD}' |
    curl -s -o /dev/null -w '%{http_code}' -c "$JAR" -b "$JAR" \
        -X POST "$API_URL/api/auth/login" -H 'Content-Type: application/json' -d @-)
if [ "$login_status" != 200 ]; then
    echo "Не удалось войти: HTTP $login_status" >&2
    exit 1
fi
# Сервер выдаёт CSRF-токен в cookie при первом запросе после входа
curl -s -o /dev/null -c "$JAR" -b "$JAR" "$API_URL/api/auth/me"
XSRF=$(awk '$6 == "XSRF-TOKEN" {print $7}' "$JAR")

categories=$(curl -s "$API_URL/api/categories")
districts=$(curl -s "$API_URL/api/districts")

failed=0
while read -r point; do
    name=$(jq -r .name <<<"$point")
    problems=$(jq -r '._problems | join("; ")' <<<"$point")
    if [ -n "$problems" ]; then
        echo "Пропущен «$name»: $problems"
        failed=1
        continue
    fi
    response=$(jq -c 'del(._problems)' <<<"$point" |
        curl -s -w '\n%{http_code}' -b "$JAR" -X POST "$API_URL/api/admin/points" \
            -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" -d @-)
    status=${response##*$'\n'}
    if [ "$status" = 201 ]; then
        echo "Добавлен «$name»"
    else
        echo "Ошибка HTTP $status для «$name»: ${response%$'\n'*}"
        failed=1
    fi
done < <(jq -c --argjson categories "$categories" --argjson districts "$districts" '
    .[] | . as $point
    | ($districts | map(select(.city == $point.city and .name == $point.district)) | first | .id) as $districtId
    | [$point.categories[] | . as $code | select($categories | map(.code) | index($code) | not)] as $unknown
    | del(.city, .district, .categories)
    + {
        districtId: $districtId,
        categoryIds: [$point.categories[] as $code | $categories[] | select(.code == $code) | .id],
        _problems: (
            (if $districtId == null then ["район «\($point.district)» не найден"] else [] end)
            + ($unknown | map("категория «\(.)» не найдена"))
        )
    }' "$FILE")

exit "$failed"
