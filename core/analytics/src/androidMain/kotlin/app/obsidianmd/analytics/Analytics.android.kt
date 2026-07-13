package app.obsidianmd.analytics

import io.appmetrica.analytics.AppMetrica

actual object Analytics {
    // Аналитика не должна ронять вызывающий код: до AppMetrica.activate (юнит-тесты, ранний старт)
    // reportEvent кидает ValidationException — глушим.
    actual fun event(name: String, params: Map<String, String>) {
        runCatching {
            if (params.isEmpty()) AppMetrica.reportEvent(name)
            else AppMetrica.reportEvent(name, params)
        }
    }
}
