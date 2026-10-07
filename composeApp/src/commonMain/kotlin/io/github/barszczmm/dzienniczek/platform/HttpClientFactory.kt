package io.github.barszczmm.dzienniczek.platform

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
