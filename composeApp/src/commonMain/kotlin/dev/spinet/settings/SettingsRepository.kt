package dev.spinet.settings

import com.russhwolf.settings.Settings
import dev.spinet.util.isSealed
import dev.spinet.util.openSecret
import dev.spinet.util.sealSecret
import dev.spinet.util.secretScope

class SettingsRepository(private val settings: Settings = Settings()) {

    init {
        for (key in listOf(KEY_EXTENSION_SETTINGS, KEY_SERVER_PASSWORD, KEY_SERVER_SIGN_INS)) {
            val stored = settings.getString(key, "")
            if (!isSealed(stored)) settings.putString(key, sealSecret(stored))
        }
    }

    private fun readSecret(key: String): String {
        val scope = secretScope()?.takeIf { rememberServerPasswords }
        if (scope != null) settings.getStringOrNull("$key@$scope")?.let { return openSecret(it) }
        return openSecret(settings.getString(key, ""))
    }

    private fun writeSecret(key: String, value: String) {
        val scope = secretScope()?.takeIf { rememberServerPasswords }
        settings.putString(if (scope != null) "$key@$scope" else key, sealSecret(value))
    }

    val canChooseToRememberPasswords: Boolean get() = secretScope() != null

    var rememberServerPasswords: Boolean
        get() = settings.getBoolean(KEY_REMEMBER_SERVER_PASSWORDS, false)
        set(value) = settings.putBoolean(KEY_REMEMBER_SERVER_PASSWORDS, value)

    val passwordsOnlyInMemory: Boolean get() = canChooseToRememberPasswords && !rememberServerPasswords

    fun removeSavedPasswordCopies() {
        for (key in settings.keys) {
            if (key.startsWith("$KEY_SERVER_PASSWORD@") || key.startsWith("$KEY_SERVER_SIGN_INS@")) settings.remove(key)
        }
    }
    var experimentalPluginEngineEnabled: Boolean
        get() = settings.getBoolean(KEY_EXPERIMENTAL_PLUGIN_ENGINE, false)
        set(value) = settings.putBoolean(KEY_EXPERIMENTAL_PLUGIN_ENGINE, value)

    var highQualityArtEnabled: Boolean
        get() = settings.getBoolean(KEY_HIGH_QUALITY_ART, true)
        set(value) = settings.putBoolean(KEY_HIGH_QUALITY_ART, value)

    var searchHistoryRaw: String
        get() = settings.getString(KEY_SEARCH_HISTORY, "")
        set(value) = settings.putString(KEY_SEARCH_HISTORY, value)

    var playStatsJson: String
        get() = settings.getString(KEY_PLAY_STATS, "")
        set(value) = settings.putString(KEY_PLAY_STATS, value)

    var sourcePluginsJson: String
        get() = settings.getString(KEY_SOURCE_PLUGINS, "")
        set(value) = settings.putString(KEY_SOURCE_PLUGINS, value)

    var enabledExtensionIds: String
        get() = settings.getString(KEY_ENABLED_EXTENSIONS, "")
        set(value) = settings.putString(KEY_ENABLED_EXTENSIONS, value)

    var extensionSettingsJson: String
        get() = openSecret(settings.getString(KEY_EXTENSION_SETTINGS, ""))
        set(value) = settings.putString(KEY_EXTENSION_SETTINGS, sealSecret(value))

    var extensionRepositories: String
        get() = settings.getString(KEY_EXTENSION_REPOSITORIES, "")
        set(value) = settings.putString(KEY_EXTENSION_REPOSITORIES, value)

    var selectedThemeId: String
        get() = settings.getString(KEY_SELECTED_THEME, "")
        set(value) = settings.putString(KEY_SELECTED_THEME, value)

    var customThemesJson: String
        get() = settings.getString(KEY_CUSTOM_THEMES, "")
        set(value) = settings.putString(KEY_CUSTOM_THEMES, value)

    var playlistsJson: String
        get() = settings.getString(KEY_PLAYLISTS, "")
        set(value) = settings.putString(KEY_PLAYLISTS, value)

    var favoritesJson: String
        get() = settings.getString(KEY_FAVORITES, "")
        set(value) = settings.putString(KEY_FAVORITES, value)

    var localFolderUri: String
        get() = settings.getString(KEY_LOCAL_FOLDER_URI, "")
        set(value) = settings.putString(KEY_LOCAL_FOLDER_URI, value)

    var localFolderLabel: String
        get() = settings.getString(KEY_LOCAL_FOLDER_LABEL, "")
        set(value) = settings.putString(KEY_LOCAL_FOLDER_LABEL, value)

    var serverHost: String
        get() = settings.getString(KEY_SERVER_HOST, "")
        set(value) = settings.putString(KEY_SERVER_HOST, value)

    var serverLibraryPort: String
        get() = settings.getString(KEY_SERVER_LIBRARY_PORT, "8091")
        set(value) = settings.putString(KEY_SERVER_LIBRARY_PORT, value)

    var serverUsername: String
        get() = settings.getString(KEY_SERVER_USERNAME, "")
        set(value) = settings.putString(KEY_SERVER_USERNAME, value)

    var serverPassword: String
        get() = readSecret(KEY_SERVER_PASSWORD)
        set(value) = writeSecret(KEY_SERVER_PASSWORD, value)

    var serverSignInsJson: String
        get() = readSecret(KEY_SERVER_SIGN_INS)
        set(value) = writeSecret(KEY_SERVER_SIGN_INS, value)

    var defaultServerDir: String
        get() = settings.getString(KEY_DEFAULT_SERVER_DIR, "")
        set(value) = settings.putString(KEY_DEFAULT_SERVER_DIR, value)

    var localFolderPrompted: Boolean
        get() = settings.getBoolean(KEY_LOCAL_FOLDER_PROMPTED, false)
        set(value) = settings.putBoolean(KEY_LOCAL_FOLDER_PROMPTED, value)

    var removedServerUrlsCsv: String
        get() = settings.getString(KEY_REMOVED_SERVERS, "")
        set(value) = settings.putString(KEY_REMOVED_SERVERS, value)

    var settingsOrderCsv: String
        get() = settings.getString(KEY_SETTINGS_ORDER, "")
        set(value) = settings.putString(KEY_SETTINGS_ORDER, value)

    var settingsLocked: Boolean
        get() = settings.getBoolean(KEY_SETTINGS_LOCKED, true)
        set(value) = settings.putBoolean(KEY_SETTINGS_LOCKED, value)

    var syncAutoEnabled: Boolean
        get() = settings.getBoolean(KEY_SYNC_AUTO, true)
        set(value) = settings.putBoolean(KEY_SYNC_AUTO, value)

    var syncPlaybackEnabled: Boolean
        get() = settings.getBoolean(KEY_SYNC_PLAYBACK, true)
        set(value) = settings.putBoolean(KEY_SYNC_PLAYBACK, value)

    var syncThemeEnabled: Boolean
        get() = settings.getBoolean(KEY_SYNC_THEME, true)
        set(value) = settings.putBoolean(KEY_SYNC_THEME, value)

    var lastSessionJson: String
        get() = settings.getString(KEY_LAST_SESSION, "")
        set(value) = settings.putString(KEY_LAST_SESSION, value)

    var widgetServerBookmarkJson: String
        get() = settings.getString(KEY_WIDGET_SERVER_BOOKMARK, "")
        set(value) = settings.putString(KEY_WIDGET_SERVER_BOOKMARK, value)

    var widgetSnapshotJson: String
        get() = settings.getString(KEY_WIDGET_SNAPSHOT, "")
        set(value) = settings.putString(KEY_WIDGET_SNAPSHOT, value)

    private companion object {
        const val KEY_WIDGET_SERVER_BOOKMARK = "widget_server_bookmark"
        const val KEY_LAST_SESSION = "last_session"
        const val KEY_WIDGET_SNAPSHOT = "widget_snapshot"
        const val KEY_EXPERIMENTAL_PLUGIN_ENGINE = "experimental_plugin_engine"
        const val KEY_HIGH_QUALITY_ART = "high_quality_art"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_PLAY_STATS = "play_stats"
        const val KEY_SOURCE_PLUGINS = "source_plugins"
        const val KEY_ENABLED_EXTENSIONS = "enabled_extensions"
        const val KEY_EXTENSION_REPOSITORIES = "extension_repositories"
        const val KEY_EXTENSION_SETTINGS = "extension_settings"
        const val KEY_SELECTED_THEME = "selected_theme"
        const val KEY_CUSTOM_THEMES = "custom_themes"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_FAVORITES = "favorites"
        const val KEY_LOCAL_FOLDER_URI = "local_folder_uri"
        const val KEY_LOCAL_FOLDER_LABEL = "local_folder_label"
        const val KEY_SERVER_HOST = "server_host"
        const val KEY_SERVER_LIBRARY_PORT = "server_library_port"
        const val KEY_SERVER_USERNAME = "server_username"
        const val KEY_SERVER_PASSWORD = "server_password"
        const val KEY_SERVER_SIGN_INS = "server_sign_ins"
        const val KEY_REMEMBER_SERVER_PASSWORDS = "remember_server_passwords"
        const val KEY_DEFAULT_SERVER_DIR = "default_server_dir"
        const val KEY_LOCAL_FOLDER_PROMPTED = "local_folder_prompted"
        const val KEY_SETTINGS_ORDER = "settings_order"
        const val KEY_REMOVED_SERVERS = "removed_servers"
        const val KEY_SETTINGS_LOCKED = "settings_locked"
        const val KEY_SYNC_AUTO = "sync_auto"
        const val KEY_SYNC_THEME = "sync_theme"
        const val KEY_SYNC_PLAYBACK = "sync_playback"
    }
}
