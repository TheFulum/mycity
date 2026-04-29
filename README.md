# MyCity (Android)

Android-приложение для сообщений о городских проблемах: создание заявок, карта, комментарии, уведомления, профиль и админ-модуль.

## Технологический стек

- `Java 17`
- `Android SDK`: min 27, target 34, compile 36
- `AGP 8.13.2`, `Gradle` (wrapper в проекте)
- `UI`: ViewBinding, Material Components, ConstraintLayout, RecyclerView, ViewPager2, CardView, SwipeRefreshLayout
- `Auth/Backend`: Firebase Auth + Cloud Firestore
- `Фото`: Cloudinary Android SDK
- `Карта`: osmdroid (OpenStreetMap / MAPNIK)
- `Геопозиция`: Google Play Services Location
- `Геокодинг`: Retrofit + Gson + Nominatim API
- `Медиа/UI`: Glide, CircleImageView, Facebook Shimmer

## Библиотеки и зависимости

Основные зависимости подключены в `app/build.gradle`:

- Firebase BOM + `firebase-auth`, `firebase-firestore`
- `com.cloudinary:cloudinary-android`
- `androidx.appcompat`, `material`, `constraintlayout`, `viewpager2`, `recyclerview`
- `androidx.navigation` (утилиты навигации, без единого nav-graph)
- `glide`
- `de.hdodenhof:circleimageview`
- `com.facebook.shimmer:shimmer`
- `org.osmdroid:osmdroid-android`
- `retrofit` + `converter-gson`
- `play-services-location`

## Архитектура проекта

Проект построен по модульной структуре в рамках Android app:

- `data/model` — модели домена (`Issue`, `Comment`, `UserProfile`, `Notification`)
- `data/repository` — доступ к Firestore и бизнес-операции данных
- `ui/*` — экраны и адаптеры по функциональным зонам (`auth`, `feed`, `map`, `profile`, `admin`, `main`)
- `util` — инфраструктурные утилиты (даты, валидация, сессия, cloud upload, гео и т.д.)

Подход: **Fragment + Repository + callback/listener**.

Почему так:

- быстрое и понятное разделение по зонам ответственности;
- Firestore удобно слушать в realtime через listeners;
- для текущего масштаба проще поддерживать, чем вводить более тяжелую архитектуру.

## Что где работает (по функционалу)

### 1) Авторизация и вход

- Экран старта: `ui/auth/SplashActivity`
- Email/пароль + телефон (OTP): `ui/auth/LoginActivity`, `ui/auth/PhoneAuthBottomSheet`
- Регистрация и сброс пароля: `ui/auth/RegisterActivity`, `ui/auth/ForgotPasswordActivity`
- Источник данных: Firebase Auth

Почему так:

- Firebase Auth закрывает и email flow, и SMS OTP без своего backend;
- гостевой режим хранится локально (через `SessionManager`) для быстрого входа без регистрации.

### 2) Главный контейнер и навигация

- Хост: `ui/main/MainActivity`
- Основные разделы: Feed + Map в `ViewPager2`
- Остальные экраны открываются как host-fragments поверх через `FragmentManager`

Почему так:

- упрощает UX: два главных режима всегда под рукой;
- глубокие сценарии (детали, профиль, админка) не ломают основной поток.

### 3) Заявки (лента, создание, детали)

- Лента: `ui/feed/FeedFragment`, карточки `IssueCardAdapter`
- Создание: `ui/create/CreateIssueFragment`
- Детали: `ui/feed/IssueDetailFragment`
- Репозиторий: `data/repository/IssueRepository`

Почему так:

- единый репозиторий заявок уменьшает дублирование запросов;
- детали заявки и комментарии слушаются realtime, чтобы пользователи видели актуальный статус.

### 4) Комментарии и треды

- UI: `ui/feed/CommentAdapter`
- Данные: `data/repository/CommentRepository`
- Поддержка ответов на комментарий (`parentCommentId`) и переход к исходному комменту

Почему так:

- древовидные ответы делают обсуждение понятнее;
- инкремент `commentCount` в заявке ускоряет сортировку по активности.

### 5) Карта и гео

- Карта заявок: `ui/map/MapFragment`
- Полноэкранная карта: `ui/map/MapFullscreenFragment`
- Геокодинг/адрес: `util` + Retrofit/Nominatim
- Геопозиция пользователя: FusedLocationProviderClient

Почему так:

- osmdroid (OSM) не требует Google Maps SDK и подходит для свободных карт;
- Nominatim дает удобный человекочитаемый адрес по координатам.

### 6) Профиль и админ-модуль

- Профиль: `ui/profile/ProfileFragment`, редактирование `EditProfileBottomSheet`
- Админ: `ui/admin/*` (модерация, пользователи, статистика)
- Уведомления: `ui/main/NotificationsFragment`, `NotificationRepository`

Почему так:

- role-based доступ (`user/admin`) централизован в пользовательском профиле;
- админ-функции изолированы в отдельном пакете, не усложняют пользовательский путь.

## Подробно по функциональным сценариям

Ниже разобрано не только "что делает", но и "как именно реализовано".

### Лента заявок: фильтры, сортировка, поиск

Файл: `app/src/main/java/com/app/mycity/ui/feed/FeedFragment.java`

- Источник данных: realtime-подписка `IssueRepository.listen(...)`.
- Сортировка:
  - по дате (`createdAt`) или по числу комментариев (`commentCount`);
  - повторный тап по чипу инвертирует направление (`ASC/DESC`).
- Фильтр статуса:
  - цикл `ALL -> ACTIVE -> RESOLVED -> ALL`;
  - переключение статуса перезапускает подписку.
- Поиск по названию:
  - локальный (по уже полученному списку), регистронезависимый;
  - строка нормализуется через `toLowerCase(Locale.ROOT)`.
- Pull-to-refresh:
  - в UI это `SwipeRefreshLayout`, логически делает переподписку.

Почему так:

- серверная сортировка + локальный поиск = быстрый отклик UI и минимум сложных запросов;
- realtime listener убирает ручные синхронизации при изменениях в Firestore.

### Озвучка текста заявки (Text-to-Speech)

Файл: `app/src/main/java/com/app/mycity/ui/feed/IssueCardAdapter.java`

Как работает:

- Кнопка озвучки на карточке (`btnSpeak`) вызывает `speakIssue(issue)`.
- `TextToSpeech` инициализируется лениво при первом нажатии.
- Язык задается `Locale("ru")`, проверяется поддержка языка:
  - если язык не поддержан, озвучка не запускается;
  - если поддержан, вызывается `speakIssueText(issue)`.
- Озвучиваемая строка собирается из:
  - названия,
  - описания,
  - адреса (через `GeoUtils.displayAddress(...)`).
- Пропуски закрываются fallback-значениями (`"Без названия"`, `"Описание отсутствует"` и т.д.).
- В `onDestroyView` фрагмента вызывается `adapter.release()`, где `tts.stop()` и `tts.shutdown()`.

Что именно озвучивается:

- "Название: ... Описание: ... Адрес: ..."

Почему так:

- ленивый init экономит ресурсы;
- принудительный `shutdown` предотвращает утечки аудио-сервиса и контекста.

### Карта: маркеры, карточка точки и переход в детали

Файл: `app/src/main/java/com/app/mycity/ui/map/MapFragment.java`

Как строится карта:

- `osmdroid MapView` с `TileSourceFactory.MAPNIK`;
- стартовая точка — Могилёв (`GeoUtils.MOGILEV_LAT/LNG`);
- масштаб по умолчанию — `GeoUtils.DEFAULT_ZOOM`.

Как отображаются заявки:

- репозиторий отдает список заявок;
- для каждой создается `Marker` с координатами `issue.lat/lng`;
- клик по маркеру не открывает сразу экран, а показывает popup-карточку.

Что именно открывается при клике по маркеру:

- нижний popup-блок внутри `fragment_map.xml`:
  - заголовок заявки,
  - короткий адрес,
  - описание,
  - первая фотография (если есть),
  - кнопка "Перейти".
- Нажатие "Перейти" вызывает `MainActivity.openIssueDetail(issueId)`.
- Уже в `IssueDetailFragment` показываются все детали: галерея, описание, карта, комментарии и действия.

Доп. действия на карте:

- "Моё местоположение" — через `FusedLocationProviderClient.getLastLocation`;
- "Показать все заявки" — вычисляется `BoundingBox` по всем координатам и выполняется `zoomToBoundingBox(...)`;
- ручной zoom-in/zoom-out отдельными кнопками.

Почему так:

- popup перед переходом снижает случайные открытия;
- пользователь сразу видит контекст точки, не теряя положение на карте.

### Создание заявки: фото, гео, голосовой ввод, черновик

Файл: `app/src/main/java/com/app/mycity/ui/create/CreateIssueFragment.java`

Валидация перед отправкой:

- заголовок минимум 5 символов;
- описание обязательно;
- минимум 1 фото;
- координаты должны быть определены;
- для гостя обязательны имя и контакт.

Фото:

- максимум `MAX_PHOTOS = 5`;
- камера через `FileProvider` + `TakePicture`;
- галерея через `OpenMultipleDocuments` с `takePersistableUriPermission`;
- удаление выбранных фото на превью.

Геолокация и адрес:

- координаты из `FusedLocationProviderClient` или fallback на центр города;
- можно выбрать точку:
  - тапом по встроенной карте (`MapEventsOverlay`);
  - либо на полноэкранном пикере (`openMapPicker` / `MapFullscreenFragment`).
- адрес запрашивается в два шага:
  - быстрый local `Geocoder`;
  - затем Nominatim (Retrofit), как уточнение.

Голосовой ввод:

- отдельные end-icon у title/description;
- запрос `RECORD_AUDIO`;
- `RecognizerIntent` с `ru-RU`;
- результат вставляется в выбранное поле (title или description).

Черновик:

- сохраняется в `onPause` через `DraftStore`;
- восстанавливается при открытии формы;
- недоступные URI отфильтровываются;
- после успешной отправки черновик очищается.

Публикация:

- сначала создается `issueId`;
- фото грузятся в Cloudinary **последовательно** в папку `issues/{issueId}`;
- после получения URL-ов сохраняется `Issue` в Firestore;
- для авторизованного пользователя инкрементируется `issueCount`.

Почему так:

- последовательная загрузка упрощает обработку ошибок и предсказуемость порядка;
- черновик критичен для длинной формы и нестабильного соединения.

### Комментарии и ответы (треды)

Файлы:

- `app/src/main/java/com/app/mycity/ui/feed/IssueDetailFragment.java`
- `app/src/main/java/com/app/mycity/ui/feed/CommentAdapter.java`
- `app/src/main/java/com/app/mycity/data/repository/CommentRepository.java`

Как работает:

- комментарии слушаются realtime по `createdAt ASC`;
- в адаптере строится плоский список из дерева по `parentCommentId`;
- у ответов показывается связь с родительским комментарием (автор + превью текста);
- клик по ссылке ответа скроллит к родительскому комментарию (`findPositionById`).

CRUD:

- `upsert` добавляет/редактирует комментарий;
- новый комментарий увеличивает `issue.commentCount`;
- удаление уменьшает `commentCount`.

Модерация:

- админ может редактировать/удалять чужие комментарии;
- требуется причина (минимальная длина);
- отправляется отдельное уведомление о модерации.

### Уведомления

Файлы:

- `app/src/main/java/com/app/mycity/ui/main/NotificationsFragment.java`
- `app/src/main/java/com/app/mycity/data/repository/NotificationRepository.java`

Как работает:

- realtime-список уведомлений по текущему `userId`;
- автосортировка по `createdAt DESC` на клиенте;
- при открытии экрана вызывается `markAllRead`.

Действия:

- тап по уведомлению (если есть `issueId`) открывает детали заявки;
- удаление одного уведомления (с подтверждением);
- массовая очистка всех уведомлений;
- индикатор непрочитанных в toolbar (`listenUnreadCount` в `MainActivity`).

### Вход по телефону (OTP)

Файл: `app/src/main/java/com/app/mycity/ui/auth/PhoneAuthBottomSheet.java`

Поток:

- номер нормализуется в формат E.164 (`+375xxxxxxxxx`);
- `PhoneAuthProvider.verifyPhoneNumber(...)` отправляет SMS;
- после `onCodeSent` показывается поле ввода кода;
- при успешной верификации выполняется `signInWithCredential`.

Особенности:

- поддержан автоподхват кода через `onVerificationCompleted`;
- есть повторная отправка кода;
- ошибки приводятся к человекочитаемому виду через `FirebaseErrors.humanize`.

## Темы и дизайн-система

В приложении поддерживаются:

- системная тема;
- принудительная светлая;
- принудительная тёмная.

Реализация:

- `values/colors.xml` + `values-night/colors.xml` (семантические токены);
- `values/themes.xml` + `values-night/themes.xml`;
- `util/ThemeManager` + переключатель темы в `MainActivity`.

Почему так:

- семантические токены исключают ошибки контраста;
- все ключевые компоненты автоматически адаптируются к light/dark.

### Палитра и токены

Базовые цветовые токены:

- `bg_primary`, `bg_secondary`, `bg_card` — основные поверхности;
- `text_primary`, `text_secondary`, `text_hint` — уровни текста;
- `accent_blue`, `accent_green`, `accent_red`, `accent_yellow` — акценты и статусы;
- `on_accent` — цвет текста/иконок на акцентных кнопках;
- `divider`, `input_bg`, `input_stroke` — разделители и поля ввода;
- `status_active`, `status_resolved` — бейджи статуса заявки.

Дополнительные UI-токены:

- `surface_overlay`, `surface_overlay_stroke`, `surface_muted`, `surface_scrim`;
- `tab_selected`, `success_surface`, `success_stroke`;
- `splash_grad_*`, `splash_glow_*` для фоновых градиентов экрана входа.

Все токены определены в:

- `app/src/main/res/values/colors.xml` (light)
- `app/src/main/res/values-night/colors.xml` (dark)

### Типографика, отступы и форма

- Шрифт: системный (`sans-serif`/Material defaults).
- Иерархия:
  - заголовки экранов: крупные, `bold`;
  - заголовки карточек/секций: `semibold/bold`;
  - вспомогательный текст и подписи: `text_secondary`/`text_hint`.
- Стандартные размеры/ритм:
  - базовый внутренний отступ карточек и блоков: `12-16dp`;
  - вертикальный ритм между блоками: `6-16dp`;
  - радиусы: карточки `~14dp`, кнопки `~16dp`, чипы/бейджи более округлые.

### Компонентные стили

- Поля ввода: `Widget.MyCity.TextInput` (общий стиль для `TextInputLayout`).
- Диалоги: `DialogTheme` (единая поверхность и контраст текста).
- Bottom sheet: `BottomSheetTheme` + `bg_bottom_sheet`.
- Карточки и интерактивные плашки: `bg_card`, `bg_icon_button`, `bg_filter_chip`.

### Принципы применения стилей

- Не использовать hardcoded hex в layout-файлах, только `@color/...` и `?attr/...`.
- Для кнопок с акцентным фоном всегда использовать `on_accent` для текста/иконок.
- Статусы и роли показывать отдельными бейджами (не только цветом текста).
- Контраст проверять в обеих темах для текста, иконок, disabled/error состояний.

## Данные и внешние сервисы

- `Firestore`: коллекции пользователей, заявок, комментариев, уведомлений.
- `Firebase Auth`: email/password + phone OTP.
- `Cloudinary`: хранение фото заявок/аватаров.

Важно:

- в текущей реализации cloud_name/preset заданы в `CloudinaryManager`.
- для production лучше вынести секреты/конфиги в безопасный канал (remote config/secured backend/env pipeline).

## Сборка и запуск

### Требования

- Android Studio (современная версия с AGP 8.x)
- JDK 17
- Android SDK Platform 36

### Шаги

1. Убедиться, что есть `app/google-services.json` (Firebase проект).
2. Синхронизировать Gradle.
3. Собрать debug:

```bash
./gradlew :app:assembleDebug
```

или запуск из Android Studio на эмуляторе/устройстве.

## Почему выбран такой стек

- **Firebase + Firestore**: быстрый старт и realtime для социального взаимодействия (комменты/уведомления).
- **Java + ViewBinding**: стабильная, предсказуемая база без лишней магии.
- **osmdroid + OSM**: независимость от платных картовых SDK.
- **Cloudinary**: удобная загрузка/хостинг изображений отдельно от Firestore.
- **Material Components**: единая UI-платформа и быстрая поддержка тем.

## Краткая дорожная карта

- вынести Cloudinary-конфиг из кода в более безопасную схему;
- добавить тесты для repositories и критичных пользовательских сценариев;
- постепенно унифицировать компоненты через общий набор `Widget.MyCity.*` стилей.
