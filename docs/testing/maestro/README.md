# Maestro UI-тесты

Автоматизированные UI-сценарии для Brainer (`app.obsidianmd`) на [Maestro](https://maestro.dev).
Каждый флоу самодостаточен: `clearState` → онбординг «Use locally (no sync)» → пустой локальный
волт → шаги теста. Внешние зависимости (GitHub-вход, git-синк, реальный AI) не требуются.

## Предпосылки

- Запущенный Android-эмулятор или устройство (проверялось на **Pixel 9a**, Android 17).
- Установленный debug-билд приложения: `./gradlew :composeApp:installDebug`.
- Установленный Maestro (`~/.maestro/bin` в `PATH`).

## Запуск

```bash
# все флоу
maestro test docs/testing/maestro/

# один флоу
maestro test docs/testing/maestro/create-note.yaml
```

> Примечание. Каждый флоу делает `clearState` и холодный старт. На «уставшем» эмуляторе
> подряд идущие холодные старты иногда упираются в ANR — при массовом прогоне давайте
> эмулятору передышку (перезапуск эмулятора возвращает стабильность) или гоняйте флоу
> по одному.

## Ограничения автоматизации

- **Кириллица**: Maestro `inputText` не вводит не-ASCII — в флоу используются латинские имена.
- **Disabled-состояние** Compose-кнопки Maestro читает ненадёжно — кейс «пустое имя →
  Create недоступна» покрыт unit-тестом `VaultListScreenTest`, а не флоу.
- **Совпадение текста**: Maestro матчит по regex с полным совпадением строки — для
  подстроки используйте `.*текст.*` (см. флоу).

## Список флоу

| Файл | Кейс |
|------|------|
| `onboarding-local.yaml` | TC-OB-02 — локальный онбординг → пустой волт |
| `create-note.yaml` | TC-CN-01 — создание заметки, открывается редактор |
| `fab-menu.yaml` | TC-CN-02 — FAB открывает меню создания |
| `create-folder.yaml` | TC-CF-01 — создание папки, папки первыми |
| `create-note-in-subfolder.yaml` | TC-CF-02 — заметка внутри папки |
| `create-validation.yaml` | TC-CV-01 — дубликат имени → ошибка |
| `note-edit-save.yaml` | TC-NE-01 — правка и сохранение заметки |
| `note-unsaved-dialog.yaml` | TC-NE-02 — диалог несохранённых правок |
| `search.yaml` | TC-SR-01 — поиск по имени |
| `settings-open.yaml` | TC-ST-01 — переход в настройки |
| `change-repo-screen.yaml` | TC-RC-01 — экран смены репозитория |
| `folder-navigation.yaml` | TC-NV-01 — вход в папку и назад |
