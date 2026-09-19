# Bleyzos AI — Android (без Compose и сторонних библиотек)

Нативный Android-клиент для ai.bleyzos.ru. Никаких Compose, AndroidX, OkHttp, Retrofit, Glide:
UI — обычные `View`, нарисованные вручную; сеть — `HttpURLConnection`; иконки Lucide рисуются
на `Canvas` из SVG-путей собственным парсером.

```
modules/
  bleyzosAIApiModule/      всё, что про API и состояние (без единого View)
    BleyzosAIClient        точка входа: auth, chat, files, store
    chat/ChatApi           POST /api/chat/ → NDJSON-стрим (text/tool/toolres/artifact/done)
    chat/ChatEngine        порт chat-app.tsx: сессии, отправка, правка с версиями ‹1/2›, стоп
    chat/FilesApi          /api/files/ → байты и сохранение в «Загрузки»
    auth/AuthManager       Device Flow: /api/auth/device + /api/auth/device/verify
    store/DialogStore      локальная история (по владельцу)
    net/                   HTTP-клиент и хранилище кук
    model/                 Message, Part, ToolCall, Artifact, Session…
  bleyzosAIDesignModule/   весь дизайн
    theme/                 палитра Bleyzos, шрифты Onest/Unbounded, фоны, тени
    icons/                 Lucide: пути + SVG-парсер + BzIconView
    widgets/               кнопки, чипы, drawer, popup, toast, диалог…
    markdown/              markdown-lite и блоки кода
    chat/                  сообщения, ввод, сайдбар, панель артефактов, BzChatScreen
app/                       тонкая оболочка: один Activity (BzChatScreen)
```

## Подключение

```kotlin
class MainActivity : Activity() {
    private lateinit var screen: BzChatScreen
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        BzSystemBars.apply(this)
        screen = BzChatScreen(this)
        setContentView(screen)
    }
    override fun onBackPressed() { if (!screen.handleBack()) super.onBackPressed() }
    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        if (!screen.handleActivityResult(r, c, d)) super.onActivityResult(r, c, d)
    }
}
```

Модуль дизайна зависит только от модуля API; API-модуль — ни от чего.
minSdk 29, compileSdk 35. Сборка: открыть в Android Studio (или `gradle wrapper && ./gradlew :app:assembleDebug`).

## Что нужно знать про сервер

1. **Гостевая кука.** `/api/chat/` без куки `bleyzos_guest` отвечает 401, а middleware выдаёт её только
   в ответ на запрос. Клиент делает «прогревочный» GET перед первым чатом и хранит куку.
2. **Вход через Device Flow сейчас не даёт авторизованную сессию** (серверная правка, 2 строки).
   В `src/app/api/auth/device/link/route.ts` токен для приложения — случайный `crypto.randomUUID()`,
   которого нет в БД, а `getOwner()` принимает только куку `bleyzos_session`. Исправление:
   ```ts
   import { createSession } from "@/lib/db";
   // вместо: await startSession(user); const sessionToken = crypto.randomUUID();
   const sessionToken = await createSession(user);
   ```
   Приложение уже отправляет полученный токен как куку `bleyzos_session`, больше ничего менять не нужно.
3. **История диалогов хранится на устройстве.** На сервере она доступна только через server actions
   Next.js (`loadDialogsAction` и др.), из Android их не вызвать. Нужны REST-эндпоинты — клиент
   легко подключить в `ChatEngine`.

## Шрифты

Onest и Unbounded (OFL) лежат в `bleyzosAIDesignModule/src/main/assets/fonts/`. Без них
дизайн падает на системный sans-serif.
