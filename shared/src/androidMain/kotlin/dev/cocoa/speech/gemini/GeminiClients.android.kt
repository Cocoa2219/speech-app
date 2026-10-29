package dev.cocoa.speech.gemini

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

fun createGeminiClient(apiKey: String): GeminiClient = GeminiClient(apiKey, HttpClient(OkHttp))
