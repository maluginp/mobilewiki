package app.obsidianmd.vault

data class MdFile(val name: String, val path: String)

/** Строка списка vault: папка или .md-файл. */
data class VaultEntry(val name: String, val path: String, val isFolder: Boolean)
