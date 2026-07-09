# План (TDD)

1. **VaultEntry + listEntries** — `VaultEntry(name, path, isFolder)`; репо-тест:
   папки первыми, затем `.md` по имени, не-.md отброшены. Хелперы `rootPath`,
   `parentOf`, `isRoot`.
2. **Навигация в VM** — `entries`, `currentDir`, `atRoot` в `VaultState`;
   `openFolder`/`upFolder`; `refresh` грузит entries корня. VM-тест: open→up.
3. **Экран** — `VaultListScreen` на `ListItem`+иконки; nestedScroll-скрытие поиска;
   проброс `onOpenFolder`/`upFolder` в App.kt (back в подпапке = upFolder).
4. Прогнать все тесты зелёными, проверить на эмуляторе.
