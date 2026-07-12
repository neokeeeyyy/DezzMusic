package com.dezzmusic.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.dezzmusic.R
import com.dezzmusic.databinding.ActivitySettingsDetailBinding

class SettingsDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsDetailBinding
    private lateinit var prefs: SharedPreferences
    private var settingsType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsType = intent.getStringExtra("settings_type") ?: ""
        prefs = getSharedPreferences("dezzmusic_prefs", Context.MODE_PRIVATE)

        setupToolbar()
        setupSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        when (settingsType) {
            "appearance" -> binding.toolbar.title = "Apariencia"
            "playback" -> binding.toolbar.title = "Reproducción"
            "storage" -> binding.toolbar.title = "Almacenamiento"
            "notifications" -> binding.toolbar.title = "Notificaciones"
            "account" -> binding.toolbar.title = "Cuenta"
            "about" -> binding.toolbar.title = "Acerca de"
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupSettings() {
        when (settingsType) {
            "appearance" -> setupAppearanceSettings()
            "playback" -> setupPlaybackSettings()
            "storage" -> setupStorageSettings()
            "notifications" -> setupNotificationSettings()
            "account" -> setupAccountSettings()
            "about" -> setupAboutSettings()
        }
    }

    private fun setupAppearanceSettings() {
        binding.settingsContainer.removeAllViews()

        // Dark Mode
        val darkModeView = createSwitchItem(
            "Modo oscuro",
            "Forzar tema oscuro en toda la app",
            prefs.getBoolean("dark_mode", true)
        ) { isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
        }
        binding.settingsContainer.addView(darkModeView)

        // AMOLED Mode
        val amoledView = createSwitchItem(
            "Modo AMOLED",
            "Fondo completamente negro para pantallas AMOLED",
            prefs.getBoolean("amoled_mode", true)
        ) { isChecked ->
            prefs.edit().putBoolean("amoled_mode", isChecked).apply()
        }
        binding.settingsContainer.addView(amoledView)

        // Accent Color
        val accentView = createInfoItem(
            "Color de acento",
            "Azul (Material You)"
        )
        binding.settingsContainer.addView(accentView)
    }

    private fun setupPlaybackSettings() {
        binding.settingsContainer.removeAllViews()

        // Audio Quality
        val qualityView = createInfoItem(
            "Calidad de audio",
            "Alta calidad"
        )
        binding.settingsContainer.addView(qualityView)

        // Gapless
        val gaplessView = createSwitchItem(
            "Reproducción sin pausas",
            "Transición continua entre canciones",
            prefs.getBoolean("gapless", true)
        ) { isChecked ->
            prefs.edit().putBoolean("gapless", isChecked).apply()
        }
        binding.settingsContainer.addView(gaplessView)

        // Crossfade
        val crossfadeView = createSwitchItem(
            "Fundido cruzado",
            "Superponer canciones al cambiar",
            prefs.getBoolean("crossfade", false)
        ) { isChecked ->
            prefs.edit().putBoolean("crossfade", isChecked).apply()
        }
        binding.settingsContainer.addView(crossfadeView)
    }

    private fun setupStorageSettings() {
        binding.settingsContainer.removeAllViews()

        val storageView = createInfoItem(
            "Guardar descargas en",
            "Almacenamiento interno"
        )
        binding.settingsContainer.addView(storageView)

        val cacheView = createInfoItem(
            "Tamaño de caché",
            "128 MB"
        )
        binding.settingsContainer.addView(cacheView)

        val clearCacheView = createButtonItem(
            "Limpiar caché",
            "Eliminar archivos temporales"
        ) {
            // Clear cache
        }
        binding.settingsContainer.addView(clearCacheView)
    }

    private fun setupNotificationSettings() {
        binding.settingsContainer.removeAllViews()

        val notificationView = createSwitchItem(
            "Notificar nuevas canciones",
            "Mostrar notación cuando se agreguen canciones",
            prefs.getBoolean("notify_new_songs", true)
        ) { isChecked ->
            prefs.edit().putBoolean("notify_new_songs", isChecked).apply()
        }
        binding.settingsContainer.addView(notificationView)
    }

    private fun setupAccountSettings() {
        binding.settingsContainer.removeAllViews()

        val botView = createButtonItem(
            "Bot Deezload",
            "Configurar @deezload2bot"
        ) {
            val intent = android.content.Intent(this, DeezloadBotActivity::class.java)
            startActivity(intent)
        }
        binding.settingsContainer.addView(botView)

        val logoutView = createButtonItem(
            "Cerrar sesión",
            "Desconectar cuenta de Telegram"
        ) {
            // Logout
        }
        binding.settingsContainer.addView(logoutView)
    }

    private fun setupAboutSettings() {
        binding.settingsContainer.removeAllViews()

        val versionView = createInfoItem(
            "Versión",
            "1.0.0"
        )
        binding.settingsContainer.addView(versionView)

        val devView = createInfoItem(
            "Desarrollado por",
            "DezzMusic"
        )
        binding.settingsContainer.addView(devView)
    }

    private fun createSwitchItem(
        title: String,
        subtitle: String,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ): android.view.View {
        val view = layoutInflater.inflate(R.layout.item_settings_switch, binding.settingsContainer, false)
        val switchBinding = com.dezzmusic.databinding.ItemSettingsSwitchBinding.bind(view)
        switchBinding.tvTitle.text = title
        switchBinding.tvSubtitle.text = subtitle
        switchBinding.switchWidget.isChecked = isChecked
        switchBinding.switchWidget.setOnCheckedChangeListener { _, isChecked ->
            onCheckedChange(isChecked)
        }
        return view
    }

    private fun createInfoItem(title: String, subtitle: String): android.view.View {
        val view = layoutInflater.inflate(R.layout.item_settings_info, binding.settingsContainer, false)
        val infoBinding = com.dezzmusic.databinding.ItemSettingsInfoBinding.bind(view)
        infoBinding.tvTitle.text = title
        infoBinding.tvSubtitle.text = subtitle
        return view
    }

    private fun createButtonItem(title: String, subtitle: String, onClick: () -> Unit): android.view.View {
        val view = layoutInflater.inflate(R.layout.item_settings_button, binding.settingsContainer, false)
        val buttonBinding = com.dezzmusic.databinding.ItemSettingsButtonBinding.bind(view)
        buttonBinding.tvTitle.text = title
        buttonBinding.tvSubtitle.text = subtitle
        buttonBinding.root.setOnClickListener { onClick() }
        return view
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
