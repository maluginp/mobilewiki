# План (TDD)

1. **Резолвер** — `VaultFile` + `resolveWikiLink(target, files)`; тесты: имя/приоритет корня,
   путь, явное расширение, не найдено. (чистая логика)
2. **renderNote** — блоки Text/Image + linkTargets; тесты: ссылка→`[label](wikilink:idx)`,
   ненайденная→литерал, `![[img.png]]`→Image, ненайденная картинка→литерал.
3. **VaultRepository.allFiles()** рекурсивно (dot-каталоги мимо) + `readBytes(path)`; тест.
4. **VaultViewModel.openPath(absPath)**; тест.
5. **decodeImage** expect/actual (androidMain BitmapFactory) + MarkdownScreen: блоки,
   UriHandler-перехват `wikilink:`, Image для картинок.
6. Все тесты зелёные; проверка на эмуляторе (5 приёмочных кейсов).
