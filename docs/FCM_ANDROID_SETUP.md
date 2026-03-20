# Настройка push-уведомлений (FCM) — Android

## Мобильное приложение

1. В [Firebase Console](https://console.firebase.google.com) создайте проект и приложение Android с package name `com.example.servicedeskapk`.
2. Скачайте **google-services.json** и замените им файл `app/google-services.json` в проекте.

Без валидного `google-services.json` сборка может завершиться ошибкой плагина Google Services.

## Бэкенд (отправка push)

Используется **FCM HTTP v1 API** (Legacy API отключён Google).

1. В Firebase Console → **Project settings** → **Service accounts** нажмите **Generate new private key**.
2. Сохраните JSON-файл на сервере (например `service-account.json`).
3. Установите зависимости:
   ```bash
   pip install google-auth requests
   ```
4. Задайте переменные окружения:
   ```bash
   GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
   ```
   или
   ```bash
   FCM_SERVICE_ACCOUNT_JSON=/path/to/service-account.json
   ```
   При необходимости явно укажите project ID:
   ```bash
   FCM_PROJECT_ID=servicedeskapk
   ```

При отсутствии настроек push не отправляется, уведомления сохраняются в БД и доступны в веб-интерфейсе.
