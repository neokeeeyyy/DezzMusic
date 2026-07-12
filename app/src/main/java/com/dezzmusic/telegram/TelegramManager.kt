package com.dezzmusic.telegram

import android.content.Context
import com.dezzmusic.db.Song
import com.dezzmusic.MusicRepository
import kotlinx.coroutines.*
import org.drinkless.td.Client
import org.drinkless.td.TdApi
import org.json.JSONObject

class TelegramManager private constructor(private val context: Context) {

    private var apiId: String = ""
    private var apiHash: String = ""
    private var isAuthenticated = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)

    private var tdClient: Client? = null
    private var phoneCodeHash: String = ""

    @Volatile
    private var lastResult: Any? = null
    private val resultLock = Object()

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

    private fun ensureClient(): Client {
        if (tdClient == null) {
            val handler = object : Client.ResultHandler {
                override fun onResult(result: TdApi.TLObject) {
                    synchronized(resultLock) {
                        lastResult = result
                        resultLock.notifyAll()
                    }
                }
            }
            tdClient = Client.create({ }, null, handler)
        }
        return tdClient!!
    }

    private suspend fun <T : TdApi.TLObject> execute(request: TdApi.Function): T = withContext(Dispatchers.IO) {
        val client = ensureClient()
        synchronized(resultLock) {
            lastResult = null
            client.send(request) { }
        }
        withTimeout(30_000) {
            synchronized(resultLock) {
                while (lastResult == null) {
                    resultLock.wait(1000)
                }
                @Suppress("UNCHECKED_CAST")
                lastResult as T
            }
        }
    }

    private suspend fun initTdlib() {
        val dbDir = context.filesDir.absolutePath + "/tdlib"
        java.io.File(dbDir).mkdirs()

        execute(TdApi.SetTdlibParameters().apply {
            databaseDirectory = dbDir
            useMessageDatabase = true
            useSecretChats = false
            apiId = this@TelegramManager.apiId.toInt()
            apiHash = this@TelegramManager.apiHash
            systemLanguageCode = "es"
            deviceModel = "DezzMusic"
            applicationVersion = "1.0"
        })
    }

    suspend fun login(phoneNumber: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val client = ensureClient()

            val getState = execute<TdApi.AuthorizationState>(TdApi.GetAuthorizationState())
            when (getState) {
                is TdApi.AuthorizationStateReady -> {
                    isAuthenticated = true
                    prefs.edit().putBoolean("is_authenticated", true).apply()
                    return@withContext LoginResult.Success
                }
                is TdApi.AuthorizationStateWaitEncryptionKey -> {
                    client.send(TdApi.CheckDatabaseEncryptionKey()) { }
                    synchronized(resultLock) {
                        lastResult = null
                        resultLock.notifyAll()
                    }
                    delay(500)
                }
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    initTdlib()
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                }
                else -> {
                }
            }

            val sendCodeResult = execute<TdApi.AuthenticationCodeInfo>(
                TdApi.SendAuthenticationCode(
                    phoneNumber,
                    TdApi.PhoneNumberAuthenticationSettings(
                        false, true, false, true, false, false, ""
                    )
                )
            )

            phoneCodeHash = sendCodeResult.phoneCodeInfo.phoneCodeHash
            LoginResult.CodeSent
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Error desconocido")
        }
    }

    suspend fun verifyCode(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = execute<TdApi.AuthorizationState>(
                TdApi.CheckAuthenticationCode(code, "", "")
            )

            when (result) {
                is TdApi.AuthorizationStateWaitRegistration -> {
                    execute<TdApi.AuthorizationState>(
                        TdApi.RegisterUser("DezzMusic", "")
                    )
                    isAuthenticated = true
                    prefs.edit().putBoolean("is_authenticated", true).apply()
                    true
                }
                is TdApi.AuthorizationStateReady -> {
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
        } catch (e: Exception) {
            false
        }
    }

    suspend fun searchMusic(query: String): List<MusicSearchResult> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<MusicSearchResult>()

            val botUsername = "deezload2bot"
            val chatResult = execute<TdApi.Chats>(TdApi.SearchChats(botUsername, 1))
            var chatId = chatResult.chatIds.firstOrNull()

            if (chatId == null) {
                val user = execute<TdApi.User>(
                    TdApi.SearchPublicChat(botUsername)
                )
                val created = execute<TdApi.Chat>(
                    TdApi.CreatePrivateChat(user.id, false)
                )
                chatId = created.id
            }

            clientSend(TdApi.SendMessage(
                chatId, 0, 0, null, null,
                TdApi.InputMessageText(
                    TdApi.FormattedText(query, null), null, false, false
                )
            ))

            delay(3000)

            val history = execute<TdApi.Messages>(
                TdApi.GetChatHistory(chatId, 0, 0, 10, false)
            )

            for (msg in history.messages) {
                val content = msg.content
                if (content is TdApi.MessageText) {
                    val text = content.text.text
                    if (text.contains("Title") || text.contains("artist")) {
                        results.add(parseBotResponse(text, chatId, msg.id))
                    }
                }
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun clientSend(request: TdApi.Function) {
        val client = ensureClient()
        client.send(request) { }
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
            if (song.telegramFileId.isEmpty()) return@withContext false
            val fileId = song.telegramFileId.toIntOrNull() ?: return@withContext false
            execute<TdApi.File>(TdApi.DownloadFile(fileId, 32, 0, 0, true))
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
            val file = execute<TdApi.File>(
                TdApi.GetFile(fileId.toIntOrNull() ?: return@withContext null)
            )
            if (file.local.isDownloadingCompleted) {
                file.local.path
            } else {
                execute<TdApi.File>(
                    TdApi.DownloadFile(file.id, 32, 0, 0, true)
                )
                delay(3000)
                val updated = execute<TdApi.File>(TdApi.GetFile(file.id))
                if (updated.local.isDownloadingCompleted) updated.local.path else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sendBotCommand(chatId: Long, command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            clientSend(TdApi.SendMessage(
                chatId, 0, 0, null, null,
                TdApi.InputMessageText(
                    TdApi.FormattedText(command, null), null, false, false
                )
            ))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun logout() {
        isAuthenticated = false
        prefs.edit().remove("is_authenticated").apply()
        tdClient?.send(TdApi.LogOut()) { }
        tdClient = null
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
