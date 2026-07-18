package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.server.serverConfig

enum class WebUIInterface {
    BROWSER,
    ELECTRON,
}

enum class WebUIChannel {
    BUNDLED, // the default webUI version bundled with the server release
    STABLE,
    PREVIEW,
    ;

    companion object {
        fun from(channel: String): WebUIChannel = entries.find { it.name.lowercase() == channel.lowercase() } ?: STABLE
    }
}

enum class WebUIFlavor(
    val uiName: String,
    val repoUrl: String,
    val versionMappingUrl: String,
    val latestReleaseInfoUrl: String,
    val baseFileName: String,
) {
    WEBUI(
        "WebUI",
        "https://github.com/Suwayomi/Suwayomi-WebUI-preview",
        "https://raw.githubusercontent.com/Suwayomi/Suwayomi-WebUI/master/versionToServerVersionMapping.json",
        "https://api.github.com/repos/Suwayomi/Suwayomi-WebUI-preview/releases/latest",
        "Suwayomi-WebUI",
    ),

    VUI(
        "VUI",
        "https://github.com/Suwayomi/Suwayomi-VUI",
        "https://raw.githubusercontent.com/Suwayomi/Suwayomi-VUI/main/versionToServerVersionMapping.json",
        "https://api.github.com/repos/Suwayomi/Suwayomi-VUI/releases/latest",
        "Suwayomi-VUI-Web",
    ),

    /**
     * Official UI for the suwayomi-exp fork. Served from the local `webUI` data directory
     * (same as [CUSTOM]); not downloaded/updated from GitHub.
     */
    EXPUUI(
        "ExpUI",
        "",
        "",
        "",
        "Suwayomi-ExpUI",
    ),

    CUSTOM(
        "Custom",
        "repoURL",
        "versionMappingUrl",
        "latestReleaseInfoURL",
        "baseFileName",
    ),
    ;

    /** Flavors that only serve files already present under the server `webUI` folder. */
    fun isLocalManaged(): Boolean = this == CUSTOM || this == EXPUUI

    companion object {
        /** Bundled/default upstream UI (used by channel BUNDLED fallbacks). */
        val default: WebUIFlavor = WEBUI

        fun from(value: String): WebUIFlavor =
            entries.find {
                it.uiName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: default

        val current: WebUIFlavor
            get() = serverConfig.webUIFlavor.value
    }
}
