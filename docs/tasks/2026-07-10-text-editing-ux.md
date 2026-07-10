<intent>
Улучшить UX редактирования заметок: панель форматирования, безопасное сохранение,
чистый полноэкранный редактор.
</intent>

<requirements>
- Панель форматирования над клавиатурой в режиме правки: Bold, Italic, Heading, List,
  Checkbox, Link — оборачивают выделение соответствующим markdown (или вставляют пустые
  маркеры с кареткой внутри, если выделения нет); Heading/List/Checkbox — префикс строки.
- Безопасное сохранение: выход «Назад» с несохранёнными изменениями показывает диалог
  (Сохранить / Не сохранять / Отмена); индикатор наличия несохранённого.
- Чистый редактор: без рамки, на весь экран, читабельный шрифт и отступы.
</requirements>

<context>
Tracker: none (key-less). Ветка: text-editing-ux. Build directly, no PR.
Текущее редактирование: App.kt держит `draft: String`, режим правки в MarkdownScreen —
один OutlinedTextField с сырым markdown; сохранение по ✓ в AppBar; «Назад» молча
отбрасывает правки. material-icons-extended уже подключён (можно использовать иконки
FormatBold/FormatItalic/Title/FormatListBulleted/CheckBox/Link).
Вне scope: живое превью при редактировании, создание новых заметок.
</context>
