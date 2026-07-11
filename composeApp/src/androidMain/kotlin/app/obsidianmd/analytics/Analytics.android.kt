package app.obsidianmd.analytics

import io.appmetrica.analytics.AppMetrica

actual object Analytics {
    actual fun event(name: String, params: Map<String, String>) {
        if (params.isEmpty()) AppMetrica.reportEvent(name)
        else AppMetrica.reportEvent(name, params)
    }
}
