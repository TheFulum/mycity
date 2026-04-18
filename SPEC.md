# Мой город — техническое задание

Android-приложение для жителей Могилёва: подача заявок о городских проблемах, просмотр, модерация, статистика.

> Живой документ. Обновляется после каждого завершённого чанка изменений.

---

## 1. Технологический стек

| Слой               | Технология                                                          |
|--------------------|---------------------------------------------------------------------|
| Язык               | Java 17                                                             |
| Min SDK            | 27 · Target SDK 34 · Compile SDK 36                                 |
| UI                 | ViewBinding, Material Components 1.12, ConstraintLayout, ViewPager2 |
| Навигация          | FragmentManager + addToBackStack (без Jetpack Nav Graph)            |
| Auth               | Firebase Auth (email+пароль, телефон+OTP, гостевой режим)           |
| БД                 | Cloud Firestore                                                     |
| Хранилище фото     | Cloudinary (не Firebase Storage)                                    |
| Карта              | osmdroid 6.1.18 (OpenStreetMap, тайлы MAPNIK)                       |
| Геокодер           | Retrofit + Nominatim (reverse geocoding)                            |
| Геопозиция         | Google Play Services Location (FusedLocationProviderClient)         |
| Изображения        | Glide 4.16, CircleImageView 3.1, Shimmer 0.5                        |
| Точка по умолчанию | Могилёв (см. `GeoUtils.MOGILEV_LAT/LNG`)                            |

## 2. Структура проекта

```
com.app.mycity
├── MyCityApplication
├── data
│   ├── model        — Issue, Comment, UserProfile, Notification 
│   └── repository   — IssueRepository, CommentRepository, UserRepository, NotificationRepository
├── ui
│   ├── admin        — Moderation / Users / Stats (фрагменты + адаптеры)
│   ├── archive      — архив закрытых заявок
│   ├── auth         — Splash, Login, Register, ForgotPassword, PhoneAuthBottomSheet
│   ├── create       — CreateIssueFragment (+ osmdroid map picker)
│   ├── feed         — FeedFragment, IssueDetailFragment, PhotoPagerAdapter, CommentAdapter, IssueCardAdapter
│   ├── main         — MainActivity (хост, FAB-меню, toolbar, колокольчик), NotificationsFragment, NotificationAdapter
│   ├── map          — MapFragment (osmdroid + маркеры активных заявок)
│   ├── profile      — ProfileFragment, EditProfileBottomSheet
│   └── splash       — (см. auth.SplashActivity)
└── util             — CloudinaryManager, DateUtils, DraftStore, FirebaseErrors, GeoUtils, SessionManager, ValidationUtils
```

## 3. Роли пользователей

| Роль | Доступ |
|---|---|
| **Гость** (`SessionManager.isGuest = true`) | Просмотр ленты и карты, подача заявки с указанием имени и контакта. Нет профиля, нет комментариев под своим именем. |
| **Пользователь** (`role = "user"`) | Всё, что у гостя, + личный кабинет, свои заявки (активные/закрытые), комментарии, редактирование профиля, привязка email/телефона. |
| **Админ** (`role = "admin"`) | Всё, что у пользователя, + пункты админки в FAB-меню (красные, отделены divider): модерация (открыть/закрыть/удалить любую заявку), управление пользователями (поиск + выдача/снятие прав), статистика. |

## 4. Модель данных (Firestore)

### Коллекция `users/{uid}` — `UserProfile`
`uid, displayName, email, phone, avatarUrl, issueCount, createdAt, role` (`"user"` | `"admin"`).

### Коллекция `issues/{id}` — `Issue`
- Идентификация: `id` (DocumentId), `authorId`, `authorName`, `authorContact` (для гостей)
- Содержимое: `title`, `description`, `photoUrls: List<String>`
- Локация: `lat`, `lng`, `address`
- Статус: `status` = `ACTIVE` | `RESOLVED`
- Даты: `createdAt`, `resolvedAt`
- Закрытие: `resolvedBy`, `resolveReport`, `reportPhotoUrls: List<String>`
- Метрики: `commentCount`

### Подколлекция `issues/{id}/comments/{cid}` — `Comment`
`id, authorId, authorName, rating (1..5), text, createdAt`.

## 5. Внешние сервисы

- **Firebase Auth** — регистрация/вход по email+паролю, вход по телефону (SMS OTP), гостевой режим (без Firebase).
- **Firestore** — коллекции `users`, `issues` (+ подколлекция `comments`). Ордер заявок по `createdAt` или `commentCount`.
- **Cloudinary** — `CloudinaryManager.upload(uri, folder, callback)`, возвращает `secureUrl`. Папка вида `issues/{issueId}`.
- **Nominatim** — `NominatimClient.get().reverse(ua, lat, lng)` → `shortAddress()`. Обязателен User-Agent.
- **osmdroid** — `TileSourceFactory.MAPNIK`, `MapView` + `Marker` + `MapEventsOverlay`.
- **Google Play Services Location** — `FusedLocationProviderClient.getLastLocation`.

## 6. Дизайн-система

Тёмная тема. Основная палитра:

- Фоны: `bg_primary #1A1A1E`, `bg_secondary #242428`, `bg_card #2C2C30`
- Текст: `text_primary #FFFFFF`, `text_secondary #9E9E9E`, `text_hint #5C5C5C`
- Акценты: `accent_blue #5B5FBE` (основной), `accent_green #27AE60` (успех/гость), `accent_red #C0392B` (админка/ошибки), `accent_yellow #F1C40F`
- Статусы заявок: `status_active #27AE60`, `status_resolved #7F8C8D`
- Shimmer: `shimmer_base #2A2A2E`, `shimmer_highlight #3A3A3E`

Шрифты — системные. Углы карточек ≈12dp, padding карточек 12dp, paddingBottom списков 100dp (чтобы FAB не перекрывал).

## 7. Экраны и текущий функционал

### 7.1. Авторизация
- **SplashActivity** — логотип, автонавигация на Login или Main.
- **LoginActivity** — email + пароль, ссылка на регистрацию/восстановление/«войти по телефону»/«войти как гость».
- **RegisterActivity** — регистрация по email+паролю с валидацией.
- **ForgotPasswordActivity** — сброс пароля по email.
- **PhoneAuthBottomSheet** — отправка OTP и вход по номеру.

### 7.2. MainActivity
- Хост-контейнер: toolbar (аватар 40dp + заголовок 15sp bold по центру) + `ViewPager2` (Feed↔Map) + `FrameLayout fragment_host` для всех остальных.
- **FAB-меню** (правый нижний угол, кнопка-триггер 48dp с поворотом 180° при открытии):
  - `fab_home` (Главная) — синяя
  - `fab_add` (Новая заявка) — синяя
  - `fab_archive` (Архив) — синяя
  - *divider (красная линия)* — показывается только у админа
  - `fab_admin_moderate` — красная, админ
  - `fab_admin_users` — красная, админ
  - `fab_admin_stats` — красная, админ
- Активный раздел подсвечивается цветом, остальные — `bg_secondary`. Определение активной кнопки — `MainActivity.refreshActiveFab()`.
- Роль админа подтягивается live через `UserRepository.listen(uid)` → `watchRole()`. Аватарка в toolbar обновляется оттуда же.
- Навигация: `openHostFragment(fragment, tag)` кладёт фрагмент на backstack и скрывает `ViewPager2`; `popHost()` возвращает назад.

### 7.3. Feed (лента активных заявок)
- `FragmentFeed` + `IssueCardAdapter`.
- Сортировка: по дате / по активности (`commentCount`), в обе стороны.
- Карточка заявки: обложка, заголовок, адрес, счётчик комментариев, бейдж статуса.
- Клик по карточке → `openIssueDetail(issueId)` в MainActivity.

### 7.4. Map (карта активных заявок)
- osmdroid, центр — Могилёв.
- Маркеры всех активных заявок, клик по маркеру → IssueDetail.

### 7.5. IssueDetail
- Шапка: заголовок, статус-чип, дата.
- Слайдер фото (`PhotoPagerAdapter`).
- Описание, адрес, координаты.
- Блок комментариев (`CommentAdapter`): рейтинг (1–5 звёзд) + текст + имя автора + дата.
- Поле ввода комментария — для авторизованных. (Пока для закрытых заявок блокируется — см. TODO G6.)
- Для админа — тумблер закрытия и удаление (текущая реализация базовая).

### 7.6. CreateIssue
- Форма: `etTitle` (мин. 5 символов), `etDescription`, список фото (до 5), выбор локации.
- Фото: камера (через FileProvider) + галерея (мультивыбор `OpenMultipleDocuments` с `takePersistableUriPermission`).
- Локация: автоматически (FusedLocation) + кнопка «Моё местоположение» + тап по карте `osmdroid` (маркер на точке).
- Reverse geocoding через Nominatim, адрес подставляется в поле.
- Для гостей — поля имя/контакт.
- **Черновик**: `DraftStore` (SharedPreferences) — сохраняется в `onPause`, восстанавливается при открытии, очищается после успешного сабмита. Недоступные Uri фото отфильтровываются.
- Последовательная загрузка фото в Cloudinary → `Issue.save()` → `userRepo.incrementIssueCount` → toast + возврат.

### 7.7. Profile (личный кабинет)
- Аватар (с загрузкой через Cloudinary), имя, email, телефон, счётчик заявок.
- **Привязка email** и **привязка телефона** к текущему аккаунту (линковка FirebaseAuth), кнопки `+ Привязать email` / `+ Привязать телефон` показываются только если соответствующий канал не привязан.
- Кнопка редактирования профиля (`EditProfileBottomSheet`) — имя, аватар.
- Табы активные / выполненные заявки автора.
- Выход из аккаунта.

### 7.8. Archive
- Лента закрытых заявок пользователя (или общая — см. реализацию), в стиле Feed.

### 7.9. Admin — Moderation
- Список всех заявок + действия: открыть детально, переключить статус (ACTIVE↔RESOLVED), удалить с подтверждением.

### 7.10. Admin — Users
- Живой список `UserProfile` через `UserRepository.listenAll`.
- Поиск по email / телефону / нику (нижний регистр, case-insensitive).
- Кнопка выдачи/снятия прав админа (с подтверждением `AlertDialog`). Самому себе снять нельзя.

### 7.11. Admin — Stats
- Счётчики: всего / активные / выполнены, пользователи всего / админы, за неделю: новые / закрытые.
- Ячейки — `item_stat_cell.xml`, подключаются через `<include>`.

## 8. Changelog

- **2026-04-18 · G12** — Центр уведомлений. Модель `Notification` (`userId, issueId, issueTitle, message, createdAt, read`). `NotificationRepository` (`send`, `listenUnreadCount`, `listenAll`, `markAllRead`). Колокольчик `ic_bell` в toolbar справа с красным бейджем `tv_bell_badge` (скрыт при 0). Клик → `NotificationsFragment` (список + «Прочитать все»). `NotificationAdapter` с DiffUtil, непрочитанные — `bg_secondary`, клик → `openIssueDetail`. `AdminResolveBottomSheet` после успешного закрытия отправляет уведомление автору заявки (если он не совпадает с текущим пользователем). `MainActivity.watchBell()` подписывается на непрочитанные через `listenUnreadCount`. `NotificationsFragment` при открытии сразу вызывает `markAllRead`.
- **2026-04-18 · G11** — Клик по бейджу счётчика заявок (`tv_badge`) в `ProfileFragment` открывает `MaterialAlertDialog` с объяснением значения бейджа.
- **2026-04-18 · G10** — `FirebaseErrors.humanize(Throwable)`: маппинг кодов `FirebaseAuthException` и fallback по строке сообщения на человекочитаемый русский текст. Применено в `LoginActivity`, `RegisterActivity`, `ForgotPasswordActivity`, `PhoneAuthBottomSheet`, `ProfileFragment` (linkEmail + linkPhoneCredential). Удалён приватный `mapAuthError()` из `LoginActivity`.
- **2026-04-18 · G8** — Отчёт админа: `AdminResolveBottomSheet` (описание ≥20 символов + фото ≥1, загрузка в `issues/{id}/report`). `IssueRepository.resolve()`. Кнопка «Закрыть заявку» для админа в `IssueDetailFragment` (только активные заявки, роль загружается разово). `AdminModerationFragment` — переключение в RESOLVED теперь через BottomSheet; ACTIVE←RESOLVED по-прежнему напрямую.
- **2026-04-18 · G7** — Контекстное меню ⋮ в `IssueDetailFragment`: видно только автору. Активная заявка: «Удалить» + «Изменить»; закрытая: «Удалить» + «Возобновить». `EditIssueFragment` (title/desc/фото, без локации). Диалог «Возобновить» с обязательной причиной ≥10 символов → сброс полей resolved + статус ACTIVE.
- **2026-04-18 · G6** — Комментарии к закрытым заявкам: проверка отсутствует по умолчанию, форма видна авторизованным независимо от статуса заявки (подтверждено).
- **2026-04-18 · G5** — Карта osmdroid в `IssueDetailFragment`: 200dp под описанием, скруглённые углы через `bg_card` + `clipToOutline`. MAPNIK, мультитач, скрытый зум-контроллер, маркер `ic_marker` в точке заявки. Клик по карте — гео-интент. Lifecycle проксируется на `mapView`.
- **2026-04-18 · G4** — Полноэкранная галерея: `ZoomImageView` (пинч-зум + двойной тап), `PhotoSwipeAdapter`, `PhotoGalleryActivity` (чёрный фон, счётчик, кнопка закрытия, fullscreen). `PhotoPagerAdapter` получил `setOnPhotoClick` + `getUrls()`. Клик по фото в `IssueDetailFragment` открывает галерею; то же для фото отчёта.
- **2026-04-18 · G15** — Корона `ic_crown.xml` (жёлтый вектор). Оверлей в `fragment_profile.xml` и `fragment_user_profile_view.xml`. Иконка короны в `item_comment.xml` рядом с именем. Поле `authorRole` в `Comment`; роль проставляется при отправке; `CommentAdapter` показывает корону если `role == "admin"`.
- **2026-04-18 · G9** — Плавная анимация вкладок Profile: fade-out (110ms) → смена листенера → fade-in (180ms). `setItemAnimator(null)` уже был из G13.
- **2026-04-18 · G14** — Имя вместо ника (`bottom_sheet_edit_profile`, `fragment_admin_users`). Имя автора в карточках и `IssueDetailFragment` (кликабельно → `openUserProfile`). Поле `resolvedByName` в `Issue`. Новый `UserProfileViewFragment` (read-only: аватар, имя, email, телефон, дата, счётчик, табы заявок). `openUserProfile(uid)` в `MainActivity`.
- **2026-04-18 · G13** — Фикс мигания списков: DiffUtil в `IssueCardAdapter`, `AdminIssueAdapter`, `CommentAdapter`; `setItemAnimator(null)` в Feed, Archive, AdminModeration, Profile.
- **2026-04-18 · G3** — Навигация «Главная» и возврат после submit. Добавлен `popHostToRoot()` (`popBackStackImmediate` с `POP_BACK_STACK_INCLUSIVE`). `CreateIssueFragment` после успешного сохранения вызывает `popHostToRoot()` + `openIssueDetail(id)` вместо `popHost()`.
- **2026-04-18 · G2** — Toolbar: аватар 32→40dp, заголовок 12sp→15sp bold белый. Аватар грузится live из `watchRole()`. Удалён `loadAvatar()`.
- **2026-04-18 · G1** — Выравнивание админ-FAB (bottom margin 210/255/300dp, divider 192dp); `refreshActiveFab` подсвечивает активный раздел корректно.
- **2026-04-18 · Admin pivot** — админский раздел переехал из отдельного фрагмента с табами в три красные мини-FAB кнопки с divider, появляющиеся только у админов.
- **2026-04-18 · Admin Users/Stats** — добавлены `AdminUsersFragment` (поиск + выдача ролей), `AdminStatsFragment` (сводка), `UserRepository.updateRole/listenAll`, модель `UserProfile.role`.
- **2026-04-18 · CreateIssue** — интерактивная osmdroid-карта (тап = маркер), черновики через `DraftStore`, кнопка «Моё местоположение».
- **2026-04-18 · Auth linking** — привязка email/телефона к текущему аккаунту в Profile.
- **2026-04-17** — модель `Issue` расширена полями `resolvedAt/resolvedBy/resolveReport/reportPhotoUrls` (для будущего отчёта админа).

---

## 9. Оставшееся ТЗ

Все пункты ниже — независимые чанки. Порядок работы: **G3 → G13 → G14/G15 → G9 → G4 → G5 → G6 → G7 → G8 → G10 → G11 → G12**. Каждый чанк должен заканчиваться обновлением секции Changelog выше.

### Общие требования
- Стиль — тёмная тема, палитра из §6.
- Все тексты пользовательского интерфейса — на русском.
- Ошибки показывать `Toast.LENGTH_SHORT` с человекочитаемой формулировкой (без raw exception.getMessage() — через `G10`).
- Не добавлять новые зависимости, если функционал достижим существующим стеком.
- После каждого чанка — актуализировать `SPEC.md` (Changelog + соответствующий раздел).

### G3. Навигация: «Главная» и возврат после submit
**Проблема.** `fabHome` и возврат после `CreateIssueFragment.submit()` делают `popBackStack()` один раз, из-за чего юзер попадает на предыдущий фрагмент, а не на корень (ViewPager).

**Задача.**
1. В `MainActivity` добавить `popHostToRoot()` — `getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)`.
2. `fab_home.setOnClickListener` → `collapseFab(); popHostToRoot(); viewPager.setCurrentItem(0, true);`.
3. В `CreateIssueFragment` успех `issueRepo.save` после `uploadPhotosSequentially` должен:
   - вызвать `draftStore.clear()`,
   - `((MainActivity) getActivity()).popHostToRoot()`,
   - затем `((MainActivity) getActivity()).openIssueDetail(issue.getId())` (уже существующий метод).
4. Проверить, что `syncHostVisibility()` корректно отрабатывает, когда стек сначала полностью очищается, а затем сразу добавляется новый фрагмент.

### G13. Фикс мигания списков заявок
**Проблема.** На всех layout, где отображаются заявки (Feed, Archive, AdminModeration, Profile tabs), элементы на мгновение появляются и сразу пропадают.

**Гипотеза.** В адаптерах `IssueCardAdapter` / `AdminIssueAdapter` / профильного адаптера метод `submit(newList)` делает `items.clear(); items.addAll(newList); notifyDataSetChanged();` **до** проверки на идентичность — плюс `addSnapshotListener` Firestore иногда срабатывает дважды (локальный + серверный снапшот), второй раз с теми же данными, вызывая визуальный «мигнуть».

**Задача.**
1. В каждом адаптере списка заявок перейти на `DiffUtil` (или хотя бы проверять `items.equals(newList)` перед `notifyDataSetChanged`).
2. В `IssueRepository.listen*` и `UserRepository.listenAll` проверить — не вызывается ли listener дважды из-за пересоздания фрагмента (e.g. `onViewCreated` запускает листенер, а `onResume` — ещё раз). При необходимости — отписываться в `onDestroyView` и не запускать повторно.
3. Если проблема в анимации RecyclerView — задать `setItemAnimator(null)` для проблемных списков.
4. Проверить в Feed/Archive/AdminModeration/Profile tabs — должно плавно обновляться без «пропадания».

### G14. Имя вместо ника + просмотр чужого профиля
**Задача.**
1. Переименовать UX-терминологию: везде, где сейчас «ник» — использовать «имя». Заменить подписи полей, плейсхолдеры, заголовки в:
   - `fragment_profile.xml`, `bottom_sheet_edit_profile.xml`
   - `fragment_admin_users.xml` (поиск по email/телефону/имени)
   - `CreateIssueFragment` guest-блок: `etName` уже «имя», проверить текст.
2. **Отображение имени автора** — в карточках ленты/архива и в `IssueDetail` под заголовком показывать `Issue.authorName`. В закрытой заявке дополнительно строка «Закрыл(а): {resolvedByName} • {дата}». Так как `Issue.resolvedBy` сейчас хранит `uid`, дополнить: при закрытии админом сохранять в заявку `resolvedByName` (новое поле в модели `Issue`), либо подгружать `UserProfile` по `uid` и кэшировать имя.
3. **Клик по имени → просмотр профиля.** Новый фрагмент `UserProfileViewFragment` (read-only копия ProfileFragment):
   - принимает `uid` через `newInstance(uid)`,
   - подгружает `UserProfile` через `UserRepository.listen(uid, ...)`,
   - показывает аватар, имя, email (или скрыто если нет), телефон (или скрыто), дату регистрации, счётчик заявок,
   - **никаких кнопок редактирования, привязки email/телефона, выхода** — только просмотр,
   - содержит те же табы «Активные / Закрытые» с заявками этого юзера (reuse существующего адаптера, `IssueRepository.listenByAuthor(uid, resolved)`).
4. В `MainActivity` добавить `openUserProfile(String uid)` аналогично `openIssueDetail`.
5. Кликабельные места: имя автора в `item_issue_card.xml`, в шапке `IssueDetailFragment`, в каждом `item_comment.xml` (по имени комментатора), в закрытой заявке — строка «Закрыл(а): …».

### G15. Корона над аватаркой админа
**Задача.**
1. Добавить drawable `ic_crown.xml` (vector, жёлтая корона, ≈16dp).
2. В `fragment_profile.xml`, `UserProfileViewFragment` (G14), `item_comment.xml` (если автор — админ) — оверлей `ImageView` с `ic_crown` поверх/над аватаркой. Использовать `FrameLayout`/`ConstraintLayout` с позиционированием в верхний-правый угол аватара.
3. Видимость короны — `visibility = userProfile.isAdmin() ? VISIBLE : GONE`.
4. Для комментариев админа — показывать иконку рядом с именем, не оверлей (там нет аватарки — только имя). Tint `accent_yellow`.

### G4. Полноэкранная галерея фото
**Задача.**
1. Новая активити `PhotoGalleryActivity` (или диалог-фрагмент), принимает `ArrayList<String>` URL и `int startIndex`.
2. Layout: полноэкранный `ViewPager2` с чёрным фоном, счётчик `1/5` сверху, кнопка-крестик закрытия.
3. Адаптер страниц — `PhotoSwipeAdapter` с `PhotoView` (либо обычный `ImageView` с жестами зума через `ScaleGestureDetector`).
4. В `IssueDetailFragment` клик по любому фото → запуск галереи с нужным индексом. То же самое для фото-отчёта (G8).
5. Свайпы листают фото, пинч-зум, двойной тап — зум. При свайпе вниз (опционально) — закрытие.

### G5. Интерактивная карта в IssueDetail
**Задача.**
1. В `fragment_issue_detail.xml` добавить `org.osmdroid.views.MapView` высотой ≈200dp, под описанием/адресом, с закруглёнными углами `@drawable/bg_card`.
2. В `IssueDetailFragment`:
   - `setTileSource(TileSourceFactory.MAPNIK)`,
   - `setMultiTouchControls(true)`,
   - `getZoomController().setVisibility(NEVER)`,
   - центр и zoom — `GeoUtils.DEFAULT_ZOOM` на `issue.getLat/getLng`,
   - маркер `@drawable/ic_marker` в точке заявки.
3. Лайфцикл — `onResume`/`onPause` проксировать на `mapView`.
4. Клик по карте (опционально) — открыть внешнюю карту по геоинтенту `geo:lat,lng`.

### G6. Комментарии к закрытым заявкам
**Задача.**
1. В `IssueDetailFragment` сейчас блок ввода комментария, вероятно, скрывается/отключается когда `issue.isResolved()`. Убрать эту проверку: блок ввода доступен для авторизованных юзеров независимо от статуса заявки.
2. Гости по-прежнему не могут комментировать (оставить текущую проверку на авторизацию).

### G7. Контекстное меню автора (⋮)
**Задача.**
1. В `IssueDetailFragment` в toolbar/шапке добавить `ImageButton` с `ic_more_vert` — видим только если `issue.getAuthorId().equals(currentUid)`.
2. По клику — `PopupMenu` с пунктами:
   - **Удалить** — `AlertDialog` «Удалить заявку? Действие необратимо.» → `issueRepo.delete(id)` → `popHostToRoot()` + toast.
   - **Изменить** — открывает `EditIssueFragment` (новый, по сути упрощённая версия `CreateIssueFragment` без выбора координат, только `title`/`description`/фото), сохраняет через `issueRepo.save(issue)` с обновлёнными полями.
   - **Возобновить** — только если `issue.isResolved()`. Открывает диалог с `EditText` «Причина возобновления» (обязательное, минимум 10 символов). При подтверждении: `issue.description = issue.description + "\n\n— Возобновлено: " + reason`, `issue.status = ACTIVE`, `issue.resolvedAt = null`, `issue.resolvedBy = null`, `issue.resolveReport = null`, `issue.reportPhotoUrls = []`. Сохранить через `issueRepo.save`.
3. Для закрытой заявки пункт «Изменить» скрыть или заменить на «Возобновить».

### G8. Отчёт админа при закрытии заявки
**Задача.**
1. В админке (moderation) и в `IssueDetailFragment` для админа — при попытке закрыть заявку открывать `AdminResolveBottomSheet`:
   - `EditText` «Описание решения» (обязательно, мин. 20 символов),
   - Фото-ряд с кнопками камера/галерея (обязательно ≥1 фото, до 5),
   - Кнопки «Закрыть заявку» / «Отмена».
2. По «Закрыть заявку»: загрузить фото в Cloudinary (`issues/{id}/report/`), затем обновить `Issue`:
   - `status = RESOLVED`, `resolvedAt = new Date()`, `resolvedBy = currentUid`, `resolvedByName = currentUser.displayName` (см. G14),
   - `resolveReport = text`, `reportPhotoUrls = [...]`.
3. В `IssueDetailFragment` для закрытой заявки показывать блок **«Отчёт о решении»**: имя админа (кликабельно → G14), дата, текст `resolveReport`, ряд фото (клик → полноэкранная галерея G4). Блок выше комментариев.
4. Простое переключение статуса без отчёта — убрать (оставить только через этот bottomsheet).

### G9. Плавная анимация табов Active/Resolved в Profile
**Проблема.** При переключении между табами активных и выполненных заявок в `ProfileFragment` элементы на мгновение появляются и тут же пропадают (визуально рывок).

**Задача.**
1. Переключение табов должно кросс-фейдить контент: `TransitionManager.beginDelayedTransition(container, new Fade())` перед сменой списка.
2. Перед переключением — отписаться от предыдущего `ListenerRegistration`, подписаться на новый только после плавного скрытия RecyclerView.
3. Убедиться, что `RecyclerView.setItemAnimator(null)` (или кастомный короткий) не вызывает мигание при первом `submitList`.
4. Связано с G13 — общий фикс listener-ов заодно решит визуальные артефакты.

### G10. Обработка ошибок Firebase Auth
**Задача.**
1. Создать утилиту `util/FirebaseErrors.java` со статическим методом `String humanize(Throwable t)`.
2. Маппинг Firebase `AuthErrorCode` / исключений → русская формулировка:
   - `ERROR_EMAIL_ALREADY_IN_USE` / `FirebaseAuthUserCollisionException` → «Этот email уже привязан к другому аккаунту»
   - `ERROR_INVALID_EMAIL` → «Неверный формат email»
   - `ERROR_WEAK_PASSWORD` → «Пароль слишком простой (мин. 6 символов)»
   - `ERROR_WRONG_PASSWORD` → «Неверный пароль»
   - `ERROR_USER_NOT_FOUND` → «Пользователь с таким email не найден»
   - `ERROR_USER_DISABLED` → «Аккаунт заблокирован»
   - `ERROR_TOO_MANY_REQUESTS` → «Слишком много попыток, попробуйте позже»
   - `ERROR_INVALID_VERIFICATION_CODE` → «Неверный код из SMS»
   - `ERROR_SESSION_EXPIRED` → «Код устарел, запросите новый»
   - `ERROR_CREDENTIAL_ALREADY_IN_USE` → «Этот номер уже привязан к другому аккаунту»
   - `ERROR_REQUIRES_RECENT_LOGIN` → «Нужно переподключиться: выйдите и войдите снова»
   - `FirebaseNetworkException` → «Нет подключения к интернету»
   - default → «Не удалось выполнить операцию»
3. Использовать `FirebaseErrors.humanize(e)` во всех `addOnFailureListener` в `LoginActivity`, `RegisterActivity`, `ForgotPasswordActivity`, `PhoneAuthBottomSheet`, `ProfileFragment` (привязка email/телефона).

### G11. Пояснение к баджу возле аватарки
**Задача.**
1. Бадж с числом у аватарки в toolbar — это `issueCount` автора.
2. Клик по баджу → `PopupWindow` или `MaterialAlertDialog` с текстом: «Это количество заявок, которые вы подали. Заявки со статусом «Закрыта» тоже учитываются».
3. Стилистика — полупрозрачный фон, белый текст, кнопка «Понятно».

### G12. Центр уведомлений
**Задача.**
1. **Модель** `data/model/Notification.java`:
   - `id` (DocumentId), `userId` (получатель), `type` (`ISSUE_RESOLVED` и расширяемо), `issueId`, `title`, `body`, `read: boolean`, `createdAt: Date`.
2. **Репозиторий** `data/repository/NotificationRepository.java`:
   - `listen(uid, sortAsc, listener)` → подписка на свои уведомления `notifications` where `userId == uid` order by `createdAt`,
   - `countUnread(uid, listener)` — live-счётчик непрочитанных,
   - `markRead(id)`, `markAllRead(uid)`,
   - `delete(id)`, `deleteAll(uid)` (batch).
3. **Триггер.** При закрытии админом чужой заявки (см. G8) параллельно создаётся `Notification{userId = issue.authorId, type=ISSUE_RESOLVED, issueId=..., title="Ваша заявка закрыта", body=issue.getTitle(), read=false}`.
4. **UI.**
   - В `activity_main.xml` toolbar справа — `FrameLayout` с `ImageButton ic_bell` (24dp) и `TextView tv_badge` поверх (маленький красный круг с числом непрочитанных). При нулевом счётчике — бадж `GONE`.
   - Клик по колокольчику → `openHostFragment(new NotificationsFragment())`.
5. **NotificationsFragment.**
   - Toolbar: заголовок «Уведомления», справа две иконки — «Отметить все прочитанными» (`ic_done_all`) и «Удалить все» (`ic_delete_sweep`), кнопка сортировки (`ic_sort`) для переключения asc/desc по дате.
   - `RecyclerView` + `NotificationAdapter`:
     - элемент — иконка типа (колокольчик/чек), заголовок, тело, время (`DateUtils.relative`), индикатор непрочитанного (точка слева), две action-иконки справа: «Прочитано» (`ic_done`, disable если уже read) и «Удалить» (`ic_close`).
   - Клик по элементу → `markRead(id)` + `MainActivity.openIssueDetail(notification.issueId)`.
   - Пустое состояние: `tv_empty` «Уведомлений нет».
6. **Счётчик в toolbar** подписывается на `NotificationRepository.countUnread` в `MainActivity` (отписка в `onDestroy`).
7. Все действия (mark/delete) — без лишних тостов, кроме подтверждения на «Удалить все» (AlertDialog).

---

## 10. Текущее состояние задач

- ✅ Выполнено: регистрация/вход, создание заявки, лента, карта, детальный просмотр, комментарии (кроме закрытых), профиль, архив, админка (модерация/юзеры/статистика), привязка email/телефона, черновики, osmdroid picker, toolbar с live-аватаром.
- ✅ Выполнено (доп.): G3, G13, G14, G15, G9, G4, G5, G6, G7, G8, G10, G11, G12.
- ✅ Все чанки выполнены.




