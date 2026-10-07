package io.github.barszczmm.dzienniczek.api.hebe

import kotlinx.serialization.json.Json

internal val dzienniczekJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}
