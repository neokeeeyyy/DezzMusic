package com.dezzmusic.telegram

import android.content.Context
import com.dezzmusic.db.Song
import com.dezzmusic.MusicRepository
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.PhoneNumberAuthenticationSettings
import kotlinx.coroutines.*
import java.io.File

class TelegramManager private constructor(private val context: Context) {

    private var apiId: String = ""
    private var apiHash: String = ""
    private var isAuthenticated = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)

    private var client: TdlClient? = null

    companion object {
        @Volatile
        private var instance: TelegramManager? = null

        const val DEFAULT_API_ID = "36775534"
        const val DEFAULT_API_HASH = "9326795e8a9162e7bd455c3a35d151ef"

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
                        is AuthorizationStateWaitPhoneNumber -> {
                            // Already initialized, proceed to send code
                        }
                        is AuthorizationStateWaitCode -> {
                            // Code already sent, return CodeSent
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
                is TdlResult.Success -> {
                    LoginResult.CodeSent
                }
                is TdlResult.Failure -> {
                    LoginResult.Error("Error al enviar código: ${sendResult.message}")
                }
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
                is TdlResult.Failure -> {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun searchMusic(query: String): List<MusicSearchResult> = withContext(Dispatchers.IO) {
        try {
            val c = ensureClient()
            val results = mutableListOf<MusicSearchResult>()

            val botUsername = "deezload2bot"
            val searchResult = c.searchChats(query = botUsername, limit = 1)
            var chatId: Long = 0

            if (searchResult is TdlResult.Success) {
                val chatIds = searchResult.result
                if (chatIds.chatIds.isNotEmpty()) {
                    chatId = chatIds.chatIds.first()
                }
            }

            if (chatId == 0L) {
                val userResult = c.searchPublicChat(username = botUsername)
                if (userResult is TdlResult.Success) {
                    val user = userResult.result
                    val chatResult = c.createPrivateChat(userId = user.id, force = false)
                    if (chatResult is TdlResult.Success) {
                        chatId = chatResult.result.id
                    }
                }
            }

            if (chatId == 0L) return@withContext emptyList()

            c.sendMessage(
                chatId = chatId,
                inputMessageContent = InputMessageText(
                    text = FormattedText(text = query, entities = emptyArray()),
                    linkPreviewOptions = null,
                    clearDraft = false
                )
            )

            delay(3000)

            val historyResult = c.getChatHistory(
                chatId = chatId,
                fromMessageId = 0,
                offset = 0,
                limit = 10,
                onlyLocal = false
            )

            if (historyResult is TdlResult.Success) {
                val messages = historyResult.result.messages
                for (msg in messages) {
                    if (msg == null) continue
                    val content = msg.content
                    if (content is MessageText) {
                        val text = content.text.text
                        if (text.contains("Title") || text.contains("artist")) {
                            results.add(parseBotResponse(text, chatId, msg.id))
                        }
                    }
                }
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseBotResponse(text: String, chatId: Long, messageId: Long): MusicSearchResult {
        val lines = text.split("\n")
        var title = "Unknown"
        var artist = "Unknown"
        var duration = 0L
        var fileId = ""

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.startsWith("title") -> title = line.substringAfter(":").trim()
                lower.startsWith("artist") || lower.startsWith("by") -> artist = line.substringAfter(":").trim()
                lower.startsWith("duration") -> {
                    val dur = line.substringAfter(":").trim()
                    val parts = dur.split(":")
                    if (parts.size == 2) {
                        duration = (parts[0].trim().toLongOrNull() ?: 0) * 60 +
                                (parts[1].trim().toLongOrNull() ?: 0)
                    }
                }
                lower.startsWith("file") || lower.startsWith("id") -> {
                    if (fileId.isEmpty()) fileId = line.substringAfter(":").trim()
                }
            }
        }

        return MusicSearchResult(title, artist, duration, fileId, messageId, chatId)
    }

    suspend fun downloadSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            if (song.telegramFileId.isNullOrEmpty()) return@withContext false
            val fileId = song.telegramFileId?.toIntOrNull() ?: return@withContext false
            val c = ensureClient()
            c.downloadFile(fileId = fileId, priority = 32, offset = 0, limit = 0, synchronous = true)
            delay(2000)
            val updatedSong = song.copy(isDownloaded = true)
            MusicRepository.getInstance(context).updateSong(updatedSong)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getDownloadUrl(fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = ensureClient()
            val fileResult = c.getFile(fileId = fileId.toIntOrNull() ?: return@withContext null)
            if (fileResult is TdlResult.Success) {
                val file = fileResult.result
                if (file.local.isDownloadingCompleted) {
                    file.local.path
                } else {
                    c.downloadFile(fileId = file.id, priority = 32, offset = 0, limit = 0, synchronous = false)
                    delay(3000)
                    val updatedResult = c.getFile(fileId = file.id)
                    if (updatedResult is TdlResult.Success && updatedResult.result.local.isDownloadingCompleted) {
                        updatedResult.result.local.path
                    } else null
                }
            } else null
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

    fun logout() {
        isAuthenticated = false
        prefs.edit().remove("is_authenticated").apply()
        scope.launch {
            try {
                client?.logOut()
            } catch (_: Exception) {}
        }
        client = null
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

data class MusicSearchResult(
    val title: String,
    val artist: String,
    val duration: Long,
    val fileId: String,
    val messageId: Long,
    val chatId: Long
)
