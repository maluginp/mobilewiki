package app.obsidianmd.onboarding

/** Статус одного пункта проверки доступа. */
enum class CheckStatus {
    /** Проверка прошла. */
    Passed,

    /** Проверка не прошла (доступа нет). */
    Failed,

    /** Не проверялось / не удалось проверить. */
    Pending,
}

/**
 * Разложенный по пунктам результат проверки доступа для экрана проверки: отдельно чтение и запись,
 * можно ли продолжать и нужно ли предупреждение про read-only.
 */
data class AccessChecklist(
    val read: CheckStatus,
    val write: CheckStatus,
    val canContinue: Boolean,
    val readOnlyWarning: Boolean,
    val showRetry: Boolean,
)

/**
 * Маппинг состояния проверки в пункты чек-листа. `null` для [ValidationState.Checking] — там ещё
 * нечего показывать (крутится индикатор).
 *
 * Правила: чтение недоступно (Denied) → продолжить нельзя; чтение есть, но записи нет
 * (Ok(canWrite=false)) → продолжить можно с предупреждением про read-only; не смогли проверить
 * (Unknown) → продолжить можно (не блокируем жёстко), но предлагаем повтор.
 */
fun accessChecklist(state: ValidationState): AccessChecklist? = when (state) {
    ValidationState.Checking -> null
    is ValidationState.Ok -> AccessChecklist(
        read = CheckStatus.Passed,
        write = if (state.canWrite) CheckStatus.Passed else CheckStatus.Failed,
        canContinue = true,
        readOnlyWarning = !state.canWrite,
        showRetry = false,
    )
    is ValidationState.Denied -> AccessChecklist(
        read = CheckStatus.Failed,
        write = CheckStatus.Pending,
        canContinue = false,
        readOnlyWarning = false,
        showRetry = true,
    )
    is ValidationState.Unknown -> AccessChecklist(
        read = CheckStatus.Pending,
        write = CheckStatus.Pending,
        canContinue = true,
        readOnlyWarning = false,
        showRetry = true,
    )
}
