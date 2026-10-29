package dev.cocoa.speech.model

import kotlinx.serialization.json.Json

// Model output may grow fields before the contract does; ignore them rather than fail.
val AppJson = Json { ignoreUnknownKeys = true }
