package com.rethinkingstudio.clawlink.core.state.chat

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object RemoteImageCache {
    private const val CACHE_DIR_NAME = "clawlink_chat_remote_images"
    private const val CACHE_FILE_SUFFIX = ".png"
    private val cache = LruCache<String, Bitmap>(64)
    private val initializedContexts = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        val contextKey = context.applicationContext.packageName
        if (!initializedContexts.add(contextKey)) return
        appContext = context.applicationContext
    }

    fun get(key: String): Bitmap? {
        cache.get(key)?.let { return it }
        val file = cacheFileForKey(key) ?: return null
        if (!file.exists()) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()?.also { cache.put(key, it) }
    }

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
        val file = cacheFileForKey(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    fun cachedFile(key: String): File? {
        return cacheFileForKey(key)?.takeIf { it.exists() }
    }

    fun remove(key: String) {
        cache.remove(key)
        cacheFileForKey(key)?.delete()
    }

    fun cachedBitmapSize(key: String): Pair<Int, Int>? {
        cache.get(key)?.let { return it.width to it.height }
        val file = cacheFileForKey(key) ?: return null
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                bounds.outWidth to bounds.outHeight
            } else {
                null
            }
        }.getOrNull()
    }

    fun clearSession(gatewayId: String, sessionKey: String) {
        val scopeKey = sessionScopeKey(gatewayId, sessionKey) ?: return
        val cachePrefix = "$scopeKey|"
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.all?.keys?.filter { it.startsWith(SIZE_PREFIX + cachePrefix) }?.forEach { key ->
            prefs.edit().remove(key).apply()
        }
        cache.snapshot().keys.filter { it.startsWith(cachePrefix) }.forEach { cache.remove(it) }
        val scopeDir = File(baseCacheDir(), sha256(scopeKey))
        scopeDir.deleteRecursively()
    }

    private fun cacheFileForKey(cacheKey: String): File? {
        val scopeKey = sessionScopeKeyFromCacheKey(cacheKey)
        val baseDir = baseCacheDir() ?: return null
        val scopeDir = if (scopeKey != null) File(baseDir, sha256(scopeKey)) else File(baseDir, "global")
        return File(scopeDir, "${sha256(cacheKey)}$CACHE_FILE_SUFFIX")
    }

    private fun baseCacheDir(): File? {
        val context = appContext ?: return null
        return File(context.cacheDir, CACHE_DIR_NAME)
    }

    private fun sessionScopeKey(gatewayId: String, sessionKey: String): String? {
        val normalizedGateway = gatewayId.trim()
        val normalizedSession = sessionKey.trim().ifBlank { "main" }
        if (normalizedGateway.isBlank()) return null
        return "$normalizedGateway|$normalizedSession"
    }

    private fun sessionScopeKeyFromCacheKey(cacheKey: String): String? {
        val scope = cacheKey.substringBeforeLast("|", missingDelimiterValue = "")
        return scope.takeIf { it.isNotBlank() && cacheKey.contains("|") }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray())
        return buildString(bytes.size * 2) {
            bytes.forEach { append("%02x".format(it)) }
        }
    }

    private const val PREFS_NAME = "clawlink_chat_remote_image_sizes"
    private const val SIZE_PREFIX = "size:"
}

object RemoteAttachmentCache {
    private const val CACHE_DIR_NAME = "clawlink_chat_remote_files"
    private const val CACHE_FILE_SUFFIX = ".bin"
    private const val META_PREFS = "clawlink_chat_remote_file_meta"
    private const val EXT_PREFIX = "ext:"
    private val initializedContexts = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var appContext: Context? = null
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val app = context.applicationContext
        val contextKey = app.packageName
        if (!initializedContexts.add(contextKey)) return
        appContext = app
        prefs = app.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
    }

    fun cachedFile(key: String): File? {
        val file = cacheFileForKey(key, extensionForKey(key))
        if (file?.exists() == true) return file
        return cacheDirectory()?.listFiles()?.firstOrNull { candidate ->
            candidate.nameWithoutExtension == sha256(key)
        }
    }

    fun put(key: String, fileName: String?, bytes: ByteArray): File? {
        val file = cacheFileForKey(key, extensionFromName(fileName))
        if (file == null) return null
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { output -> output.write(bytes) }
            prefs?.edit()?.putString(extensionPrefsKey(key), file.extension.ifBlank { "bin" })?.apply()
        }
        return file.takeIf { it.exists() }
    }

    fun remove(key: String) {
        cachedFile(key)?.delete()
        prefs?.edit()?.remove(extensionPrefsKey(key))?.apply()
        cacheDirectory()?.listFiles()?.filter { it.nameWithoutExtension == sha256(key) }?.forEach { it.delete() }
    }

    fun clearSession(gatewayId: String, sessionKey: String) {
        val scopeKey = sessionScopeKey(gatewayId, sessionKey) ?: return
        val cachePrefix = "$scopeKey|"
        prefs?.let { sharedPrefs ->
            val editor = sharedPrefs.edit()
            sharedPrefs.all.keys
                .filter { it.startsWith(EXT_PREFIX + cachePrefix) }
                .forEach { editor.remove(it) }
            editor.apply()
        }
        cacheDirectory()?.listFiles()?.filter { candidate ->
            candidate.name.startsWith(sha256(cachePrefix))
        }?.forEach { it.delete() }
        val scopeDir = cacheScopeDir(scopeKey)
        scopeDir.deleteRecursively()
    }

    private fun cacheFileForKey(key: String, extension: String?): File? {
        val baseDir = cacheDirectory() ?: return null
        val scopeKey = sessionScopeKeyFromCacheKey(key)
        val scopeDir = if (scopeKey != null) cacheScopeDir(scopeKey) else File(baseDir, "global")
        val suffix = extension?.takeIf { it.isNotBlank() }?.let { ".$it" } ?: CACHE_FILE_SUFFIX
        return File(scopeDir, "${sha256(key)}$suffix")
    }

    private fun cacheDirectory(): File? {
        val context = appContext ?: return null
        return File(context.cacheDir, CACHE_DIR_NAME)
    }

    private fun cacheScopeDir(scopeKey: String): File {
        val baseDir = cacheDirectory() ?: error("RemoteAttachmentCache not initialized")
        return File(baseDir, sha256(scopeKey))
    }

    private fun extensionForKey(key: String): String? {
        return prefs?.getString(extensionPrefsKey(key), null)
    }

    private fun extensionPrefsKey(key: String): String = "$EXT_PREFIX$key"

    private fun extensionFromName(fileName: String?): String? {
        val normalized = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.trim()?.lowercase().orEmpty()
        return normalized.takeIf { it.isNotBlank() && it.length <= 8 && it.all { ch -> ch.isLetterOrDigit() } }
    }

    private fun sessionScopeKey(gatewayId: String, sessionKey: String): String? {
        val normalizedGateway = gatewayId.trim()
        val normalizedSession = sessionKey.trim().ifBlank { "main" }
        if (normalizedGateway.isBlank()) return null
        return "$normalizedGateway|$normalizedSession"
    }

    private fun sessionScopeKeyFromCacheKey(cacheKey: String): String? {
        val scope = cacheKey.substringBeforeLast("|", missingDelimiterValue = "")
        return scope.takeIf { it.isNotBlank() && cacheKey.contains("|") }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray())
        return buildString(bytes.size * 2) {
            bytes.forEach { append("%02x".format(it)) }
        }
    }
}

object RemoteImageSizeCache {
    private val cache = LruCache<String, Pair<Float, Float>>(256)
    private val initializedContexts = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        val key = appContext.packageName
        if (!initializedContexts.add(key)) return
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun get(key: String): Pair<Float, Float>? {
        cache.get(key)?.let { return it }
        val stored = prefs?.getString(sizePrefsKey(key), null) ?: return null
        val parts = stored.split("x", limit = 2)
        val width = parts.getOrNull(0)?.toFloatOrNull() ?: return null
        val height = parts.getOrNull(1)?.toFloatOrNull() ?: return null
        return width to height
    }

    fun put(key: String, size: Pair<Float, Float>) {
        cache.put(key, size)
        prefs?.edit()?.putString(sizePrefsKey(key), "${size.first}x${size.second}")?.apply()
    }

    fun clearSession(gatewayId: String, sessionKey: String) {
        val scopeKey = sessionScopeKey(gatewayId, sessionKey) ?: return
        val prefix = "$scopeKey|"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
        prefs?.let { sharedPrefs ->
            val editor = sharedPrefs.edit()
            sharedPrefs.all.keys.filter { it.startsWith("$SIZE_PREFIX$prefix") }.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    private fun sizePrefsKey(cacheKey: String): String = "$SIZE_PREFIX$cacheKey"

    private fun sessionScopeKey(gatewayId: String, sessionKey: String): String? {
        val normalizedGateway = gatewayId.trim()
        val normalizedSession = sessionKey.trim().ifBlank { "main" }
        if (normalizedGateway.isBlank()) return null
        return "$normalizedGateway|$normalizedSession"
    }

    private const val PREFS_NAME = "clawlink_chat_image_sizes"
    private const val SIZE_PREFIX = "size:"
}

object VoicePlaybackReadStore {
    private const val PREFS_NAME = "clawlink_chat_voice_read"
    private const val KEY_IDENTIFIERS = "read_identifiers"
    private val initializedContexts = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        val key = appContext.packageName
        if (!initializedContexts.add(key)) return
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getReadIdentifiers(): Set<String> {
        return prefs?.getStringSet(KEY_IDENTIFIERS, emptySet()) ?: emptySet()
    }

    fun markRead(storageKey: String) {
        val current = getReadIdentifiers().toMutableSet()
        if (current.add(storageKey)) {
            // Keep it capped at a reasonable size, e.g., 1024
            val updated = if (current.size > 1024) {
                current.sorted().takeLast(1024).toSet()
            } else {
                current
            }
            prefs?.edit()?.putStringSet(KEY_IDENTIFIERS, updated)?.apply()
        }
    }
}

fun RelayChatContentBlock.chatImageCacheKey(): String? {
    val assetKey = chatImageAssetKey() ?: return null
    val scopeKey = chatImageScopeKey()
    return scopeKey?.let { "$it|$assetKey" } ?: assetKey
}

fun RelayChatContentBlock.chatImageScopeKey(): String? {
    val normalizedGatewayId = gatewayId?.trim().orEmpty()
    if (normalizedGatewayId.isBlank()) return null
    val normalizedSessionKey = sessionKey?.trim()?.ifBlank { "main" } ?: "main"
    return "$normalizedGatewayId|$normalizedSessionKey"
}

fun RelayChatContentBlock.chatImageAssetKey(): String? {
    return fileId?.trim()?.takeIf { it.isNotEmpty() }
        ?: fileDownloadURLString?.trim()?.takeIf { it.isNotEmpty() }
        ?: downloadPath?.trim()?.takeIf { it.isNotEmpty() }
}

fun RelayChatContentBlock.chatAttachmentCacheKey(): String? {
    return chatImageCacheKey()
}
