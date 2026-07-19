# Задача: блокировка редактирования для read-only репозитория

Tracker: none (free-text). Flow: mr (GitHub remote → MR отложен на ручной PR).

## <intent>

На экране проверки доступа к подключённому git-репозиторию проверять доступ не только на
**чтение**, но и на **запись**. Если у пользователя доступ только на чтение (read-only) — разрешить
просмотр файлов/папок и AI, но заблокировать функционал редактирования и добавления (создание
заметок/папок, сохранение правок).

## <requirements>

- На экране проверки доступа (сейчас — `RepoValidationScreen`, шаг `Validate`) определять,
  есть ли у пользователя право записи (push) в репозиторий, а не только чтение.
- Если доступ **read-only**:
  - разрешено: просмотр списка файлов/папок, открытие заметок на чтение, AI-режим;
  - заблокировано: создание заметки, создание папки, сохранение/редактирование заметки.
- Если доступ **read-write**: поведение как сейчас (всё доступно).
- Признак доступа должен пережить перезапуск (персист), чтобы гейтить UI без повторной сетевой
  проверки на каждом экране.
- Локальный режим (без remote) остаётся полностью доступным на запись — это устройство пользователя.

## <context>

Источник — free-text запрос пользователя (аргумент delivery-loop), дословно:

> Вижу, что добавить файлы и папки для подключенного git репозитория для которого у меня нет
> доступа. Давай на экране проверка доступа проверять доступ на чтение и запись, и следовательно
> блокировать часть функционала для редактирования/добавления - то есть если доступ только чтение,
> то разрешить просматривать файлы/папки и AI.

### Точки в коде (найдено при разведке)

- Проверка доступа: `features/sync/api/.../RepoAccessCheck.kt` (`AccessResult` = Ok/Denied/Unknown),
  реализация `composeApp/.../sync/JGitRepoAccessCheck.kt` — сейчас только `lsRemote` (чтение).
- Экран проверки: `features/onboarding/impl/.../presentation/RepoValidationScreen.kt`,
  VM `RepoValidationViewModel.kt` (`ValidationState` = Checking/Ok/Denied/Unknown), подключён в
  `OnboardingPresentationProviderImpl.kt` шагом `Step.Validate`; на `onContinue` пишет
  `settings.setRemoteUrl(...)` + `setOnboardingDone(true)`.
- Персист настроек: `features/settings/api/.../RepoSettingsStore.kt` (getRemoteUrl/setRemoteUrl,
  onboardingDone), реализация `SharedPrefsRepoSettingsStore`.
- Точки редактирования/добавления (гейтить):
  - `AppNavHost.kt`: `onCreateNote`/`onCreateFolder` (FAB в списке), `onSave` (заметка).
  - Список: `vaultPresentation.ListScreen(...)`; заметка: `notePresentation.NoteScreen(...)`.
- AI не гейтить (пользователь явно просит оставить AI).
- Определение записи для git host-agnostic: попытка открыть push-соединение (git-receive-pack
  advertisement) — read-only пользователь получает auth-ошибку, не мутируя репозиторий.
