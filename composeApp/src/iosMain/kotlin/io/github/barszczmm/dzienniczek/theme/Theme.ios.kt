package io.github.barszczmm.dzienniczek.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
actual fun DzienniczekTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable (() -> Unit)
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme else lightScheme,
        shapes = ExpressiveShapes,
        content = content
    )
}
