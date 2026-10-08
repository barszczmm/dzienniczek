package io.github.barszczmm.dzienniczek.platform

/** Opens the system share sheet with the given text (no-op where not supported). */
expect fun shareText(title: String, text: String)
