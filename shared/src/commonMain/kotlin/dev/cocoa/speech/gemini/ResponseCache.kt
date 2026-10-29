package dev.cocoa.speech.gemini

// Stores model answers by request content so repeated test runs spend no API requests. Debug builds use a disk cache
// (androidMain FileResponseCache); release builds use None. key is the request's content, in parts; compute runs only
// on a miss, and only a successful answer is stored.
interface ResponseCache {
    suspend fun getOrPut(key: List<ByteArray>, compute: suspend () -> String): String

    object None : ResponseCache {
        override suspend fun getOrPut(key: List<ByteArray>, compute: suspend () -> String): String = compute()
    }
}
