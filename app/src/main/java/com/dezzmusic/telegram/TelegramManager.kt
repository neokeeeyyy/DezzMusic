package com.dezzmusic.telegram

import android.content.Context
import com.dezzmusic.db.Song
import com.dezzmusic.MusicRepository
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class TelegramManager private constructor(private val context: Context) {

    private var apiId: String = ""
    private var apiHash: String = ""
    private var isAuthenticated = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)

    private var client: TdlClient? = null
    private var botChatId: Long = 0
    private var botUserId: Long = 0

    companion object {
        @Volatile
        private var instance: TelegramManager? = null

        const val DEFAULT_API_ID = "36775534"
        const val DEFAULT_API_HASH = "9326795e8a9162e7bd455c3a35d151ef"
        const val BOT_USERNAME = "deezload2bot"

        fun getInstance(context: Context): TelegramManager {
            return instance ?: synchronized(this) {
                instance ?: TelegramManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        apiId = prefs.getString("api_id", DEFAULT_API_ID) ?: DEFAULT_API_ID
        apiHash = prefs.getString("api_hash", DEFAULT_API_HASH) ?: DEFAULT_API_HASH
        isAuthenticated = prefs.getBoolean("is_authenticated", false)
    }

    fun setCredentials(apiId: String, apiHash: String) {
        this.apiId = apiId
        this.apiHash = apiHash
        prefs.edit()
            .putString("api_id", apiId)
            .putString("api_hash", apiHash)
            .apply()
    }

    private suspend fun ensureClient(): TdlClient {
        if (client == null) {
            client = TdlClient.create()
        }
        return client!!
    }

    private suspend fun initTdlib() {
        val dbPath = context.filesDir.absolutePath + "/tdlib"
        File(dbPath).mkdirs()
        val c = ensureClient()
        c.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = dbPath,
            filesDirectory = dbPath + "/files",
            databaseEncryptionKey = ByteArray(0),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = apiId.toInt(),
            apiHash = apiHash,
            systemLanguageCode = "es",
            deviceModel = "DezzMusic",
            systemVersion = "1.0",
            applicationVersion = "1.0"
        )
    }

    suspend fun login(phoneNumber: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val c = ensureClient()

            val stateResult = c.getAuthorizationState()
            when (stateResult) {
                is TdlResult.Success -> {
                    val state = stateResult.result
                    when (state) {
                        is AuthorizationStateReady -> {
                            isAuthenticated = true
                            prefs.edit().putBoolean("is_authenticated", true).apply()
                            return@withContext LoginResult.Success
                        }
                        is AuthorizationStateWaitTdlibParameters -> {
                            initTdlib()
                        }
                        is AuthorizationStateWaitPhoneNumber -> {}
                        is AuthorizationStateWaitCode -> {
                            return@withContext LoginResult.CodeSent
                        }
                        is AuthorizationStateWaitRegistration -> {
                            return@withContext LoginResult.CodeSent
                        }
                        else -> {}
                    }
                }
                is TdlResult.Failure -> {
                    return@withContext LoginResult.Error("Error getting auth state: ${stateResult.message}")
                }
            }

            val sendResult = c.setAuthenticationPhoneNumber(
                phoneNumber = phoneNumber,
                settings = PhoneNumberAuthenticationSettings(
                    allowFlashCall = false,
                    allowMissedCall = false,
                    isCurrentPhoneNumber = true,
                    hasUnknownPhoneNumber = false,
                    allowSmsRetrieverApi = false,
                    firebaseAuthenticationSettings = null,
                    authenticationTokens = emptyArray()
                )
            )

            when (sendResult) {
                is TdlResult.Success -> LoginResult.CodeSent
                is TdlResult.Failure -> LoginResult.Error("Error al enviar código: ${sendResult.message}")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Error desconocido")
        }
    }

    suspend fun verifyCode(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = ensureClient()
            val result = c.checkAuthenticationCode(code = code)

            when (result) {
                is TdlResult.Success -> {
                    val stateResult = c.getAuthorizationState()
                    if (stateResult is TdlResult.Success) {
                        when (stateResult.result) {
                            is AuthorizationStateWaitRegistration -> {
                                c.registerUser(firstName = "DezzMusic", lastName = "", disableNotification = false)
                                isAuthenticated = true
                                prefs.edit().putBoolean("is_authenticated", true).apply()
                                true
                            }
                            is AuthorizationStateReady -> {
                                isAuthenticated = true
                                prefs.edit().putBoolean("is_authenticated", true).apply()
                                true
                            }
                            else -> {
                                isAuthenticated = true
                                prefs.edit().putBoolean("is_authenticated", true).apply()
                                true
                            }
                        }
                    } else {
                        isAuthenticated = true
                        prefs.edit().putBoolean("is_authenticated", true).apply()
                        true
                    }
                }
                is TdlResult.Failure -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun ensureBotChat(): Long {
        if (botChatId != 0L) return botChatId

        val c = ensureClient()

        val result = c.searchPublicChat(username = BOT_USERNAME)
        if (result is TdlResult.Success) {
            val chat = result.result
            botChatId = chat.id
            if (chat.type is ChatTypePrivate) {
                botUserId = (chat.type as ChatTypePrivate).userId
            }
            return botChatId
        }

        val searchResult = c.searchChats(query = BOT_USERNAME, limit = 1)
        if (searchResult is TdlResult.Success) {
            val chatIds = searchResult.result
            if (chatIds.chatIds.isNotEmpty()) {
                botChatId = chatIds.chatIds.first()
                return botChatId
            }
        }

        return 0L
    }

    suspend fun searchMusic(query: String): List<MusicSearchResult> = withContext(Dispatchers.IO) {
        try {
            val c = ensureClient()
            val chatId = ensureBotChat()
            if (chatId == 0L) return@withContext emptyList()

            val results = mutableListOf<MusicSearchResult>()
            val thumbDir = File(context.cacheDir, "thumbnails")
            thumbDir.mkdirs()

            c.sendMessage(
                chatId = chatId,
                inputMessageContent = InputMessageText(
                    text = FormattedText(text = query, entities = emptyArray()),
                    linkPreviewOptions = null,
                    clearDraft = false
                )
            )

            delay(4000)

            val historyResult = c.getChatHistory(
                chatId = chatId,
                fromMessageId = 0,
                offset = 0,
                limit = 30,
                onlyLocal = false
            )

            if (historyResult is TdlResult.Success) {
                val messages = historyResult.result.messages
                for (msg in messages) {
                    if (msg == null) continue
                    val content = msg.content

                    when (content) {
                        is MessageAudio -> {
                            val audio = content.audio
                            var albumArtPath: String? = null
                            audio.albumCoverMinithumbnail?.let { mini ->
                                try {
                                    val thumbFile = File(thumbDir, "thumb_${audio.audio.id}.jpg")
                                    thumbFile.writeBytes(mini.data)
                                    albumArtPath = thumbFile.absolutePath
                                } catch (_: Exception) {}
                            }

                            results.add(
                                MusicSearchResult(
                                    title = audio.title,
                                    artist = audio.performer,
                                    duration = audio.duration * 1000L,
                                    fileId = audio.audio.id.toString(),
                                    messageId = msg.id,
                                    chatId = chatId,
                                    albumArt = albumArtPath
                                )
                            )
                        }
                        is MessageText -> {
                            val text = content.text.text
                            val parsed = parseTextResults(text, chatId)
                            results.addAll(parsed)
                        }
                        else -> {}
                    }
                }
            }

            delay(3000)

            val secondBatch = c.getChatHistory(
                chatId = chatId,
                fromMessageId = 0,
                offset = 0,
                limit = 50,
                onlyLocal = false
            )

            if (secondBatch is TdlResult.Success) {
                val newIds = results.map { it.messageId }.toSet()
                for (msg in secondBatch.result.messages) {
                    if (msg == null || msg.id in newIds) continue
                    val content = msg.content
                    when (content) {
                        is MessageAudio -> {
                            val audio = content.audio
                            if (results.none { it.fileId == audio.audio.id.toString() }) {
                                var albumArtPath: String? = null
                                audio.albumCoverMinithumbnail?.let { mini ->
                                    try {
                                        val thumbFile = File(thumbDir, "thumb_${audio.audio.id}.jpg")
                                        thumbFile.writeBytes(mini.data)
                                        albumArtPath = thumbFile.absolutePath
                                    } catch (_: Exception) {}
                                }
                                results.add(
                                    MusicSearchResult(
                                        title = audio.title,
                                        artist = audio.performer,
                                        duration = audio.duration * 1000L,
                                        fileId = audio.audio.id.toString(),
                                        messageId = msg.id,
                                        chatId = chatId,
                                        albumArt = albumArtPath
                                    )
                                )
                            }
                        }
                        is MessageText -> {
                            val text = content.text.text
                            val parsed = parseTextResults(text, chatId)
                            for (pr in parsed) {
                                if (results.none { it.fileId == pr.fileId }) {
                                    results.add(pr)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Inline bot search - sends query to bot and polls results
    suspend fun searchMusicInline(query: String): List<MusicSearchResult> = withContext(Dispatchers.IO) {
        searchMusic(query)
    }

    private fun parseTextResults(text: String, chatId: Long): List<MusicSearchResult> {
        val results = mutableListOf<MusicSearchResult>()
        val lines = text.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) { i++; continue }

            val numberMatch = Regex("""^\d+[\.\)]\s*(.+?)\s*[-–—]\s*(.+?)(?:\s*\(?(\d+:\d+)\)?)?\s*$""").find(line)
            if (numberMatch != null) {
                val title = numberMatch.groupValues[1].trim()
                val artist = numberMatch.groupValues[2].trim()
                val durationStr = numberMatch.groupValues[3]
                var duration = 0L
                if (durationStr.isNotEmpty()) {
                    val parts = durationStr.split(":")
                    if (parts.size == 2) {
                        duration = (parts[0].toLongOrNull() ?: 0) * 60000L +
                                (parts[1].toLongOrNull() ?: 0) * 1000L
                    }
                }
                val previewLine = if (i + 1 < lines.size) lines[i + 1] else ""
                val fileIdMatch = Regex("""(?:id|file|code)[:\s]*["']?([a-zA-Z0-9_]+)["']?""", RegexOption.IGNORE_CASE).find(previewLine)
                results.add(
                    MusicSearchResult(
                        title = title,
                        artist = artist,
                        duration = duration,
                        fileId = fileIdMatch?.groupValues?.getOrNull(1) ?: "text_${results.size}",
                        messageId = 0L,
                        chatId = chatId,
                        albumArt = null
                    )
                )
                i += 2
            } else {
                val titleMatch = Regex("""(.+?)\s*[-–—]\s*(.+)""").find(line)
                if (titleMatch != null) {
                    val title = titleMatch.groupValues[1].trim()
                    val artist = titleMatch.groupValues[2].trim()
                    results.add(
                        MusicSearchResult(
                            title = title,
                            artist = artist,
                            duration = 0L,
                            fileId = "text_${results.size}",
                            messageId = 0L,
                            chatId = chatId,
                            albumArt = null
                        )
                    )
                }
                i++
            }
        }
        return results
    }

    suspend fun downloadSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            if (song.telegramFileId.isNullOrEmpty()) return@withContext false
            val fileId = song.telegramFileId.toIntOrNull() ?: return@withContext false
            val c = ensureClient()

            val downloadResult = c.downloadFile(fileId = fileId, priority = 32, offset = 0, limit = 0, synchronous = true)

            val maxWait = 30_000L
            val pollInterval = 500L
            var waited = 0L
            var completed = false

            while (waited < maxWait && !completed) {
                delay(pollInterval)
                waited += pollInterval
                val fileResult = c.getFile(fileId = fileId)
                if (fileResult is TdlResult.Success) {
                    val file = fileResult.result
                    if (file.local.isDownloadingCompleted) {
                        completed = true
                        val updatedSong = if (file.local.path.isNotEmpty()) {
                            song.copy(isDownloaded = true, path = file.local.path)
                        } else {
                            song.copy(isDownloaded = true)
                        }
                        MusicRepository.getInstance(context).updateSong(updatedSong)
                    }
                }
            }

            completed
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadAndGetPath(fileId: Int): String? = withContext(Dispatchers.IO) {
        try {
            val c = ensureClient()
            c.downloadFile(fileId = fileId, priority = 32, offset = 0, limit = 0, synchronous = true)

            val maxWait = 30_000L
            val pollInterval = 500L
            var waited = 0L

            while (waited < maxWait) {
                delay(pollInterval)
                waited += pollInterval
                val fileResult = c.getFile(fileId = fileId)
                if (fileResult is TdlResult.Success) {
                    val file = fileResult.result
                    if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) {
                        return@withContext file.local.path
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sendBotCommand(chatId: Long, command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = ensureClient()
            c.sendMessage(
                chatId = chatId,
                inputMessageContent = InputMessageText(
                    text = FormattedText(text = command, entities = emptyArray()),
                    linkPreviewOptions = null,
                    clearDraft = false
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getDownloadUrl(fileId: String): String? = withContext(Dispatchers.IO) {
        downloadAndGetPath(fileId.toIntOrNull() ?: return@withContext null)
    }

    fun logout() {
        isAuthenticated = false
        prefs.edit().remove("is_authenticated").apply()
        scope.launch {
            try {
                client?.logOut()
            } catch (_: Exception) {}
        }
        client = null
        botChatId = 0
        botUserId = 0
    }

    fun getApiId(): String = apiId
    fun getApiHash(): String = apiHash
    fun isLoggedIn(): Boolean = isAuthenticated
}

sealed class LoginResult {
    object CodeSent : LoginResult()
    object Success : LoginResult()
    data class Error(val message: String) : LoginResult()
}

data class InlineMusicResult(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val audioUrl: String,
    val fileId: String,
    val thumbnailUrl: String?,
    val albumArtLocalPath: String? = null
)

data class MusicSearchResult(
    val title: String,
    val artist: String,
    val duration: Long,
    val fileId: String,
    val messageId: Long,
    val chatId: Long,
    val albumArt: String? = null
)
