# План (TDD) — UX выбора AI-модели

1. **ModelInfo + форматтеры** (OpenRouterClient.kt)
   - RED: тесты `contextLabel()` («128K ctx», null → ""), `priceLabel()` («$0.15/M», "" → "").
   - GREEN: добавить `contextLength`, `pricing: ModelPricing`, extension-форматтеры.

2. **SettingsViewModel.reloadModels()**
   - RED: тест — `reloadModels()` перезагружает список даже когда он уже не пуст.
   - GREEN: force-флаг в загрузке.

3. **ModelPickerScreen** (новый ui/ModelPickerScreen.kt)
   - RED (Robolectric compose): вертелка при loading+пусто; строки моделей; тап → onSelect(id);
     фильтр по query.
   - GREEN: PullToRefreshBox + LazyColumn + ListItem.

4. **SettingsScreen** — строка модели + Edit
   - RED: тест — показывает имя модели и по тапу Edit зовёт onEditModel; автокомплита нет.
   - GREEN: заменить ModelField; обновить сигнатуру (убрать onSaveModel, добавить onEditModel);
     починить существующие вызовы в тестах.

5. **App.kt проводка** — showModelPicker/modelSearching/modelQuery, ветки AppBar, рендер экрана,
   `onEditModel`. (Проверяется приёмочными кейсами — UI-интеграция.)

6. Строки в strings.xml (`title_model_picker`, `settings_model_none`, `cd_edit_model` и т.п.).

7. Прогнать все тесты — зелёные.
