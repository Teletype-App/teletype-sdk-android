package app.teletype.sdk.config

/**
 * Backend endpoints for the two Teletype clusters. These mirror the web
 * widget's `.env.dev` / `.env.prod` files so the same accounts work.
 */
data class TeletypeEnvironment(
    val mode: String,
    val version: String,
    val apiUrl: String,
    val wsUrl: String,
    val assetsUrl: String,
    val apiVersion: String,
    val landingUrl: String,
)

enum class TeletypeMode { Dev, Prod }

val DEV_ENVIRONMENT = TeletypeEnvironment(
    mode = "dev",
    version = "4.0.0",
    apiUrl = "https://api.teletypeapp.com/",
    wsUrl = "https://websocket-widget.teletypeapp.com",
    assetsUrl = "https://widget.teletypeapp.com/",
    apiVersion = "1",
    landingUrl = "https://teletype.app",
)

val PROD_ENVIRONMENT = TeletypeEnvironment(
    mode = "stable",
    version = "5.0.0",
    apiUrl = "https://api.teletype.app/",
    wsUrl = "https://websocket-widget.teletype.app/",
    assetsUrl = "https://widget.teletype.app/",
    apiVersion = "1",
    landingUrl = "https://teletype.app",
)

val DEFAULT_MODE = TeletypeMode.Prod
val DEFAULT_ENVIRONMENT = PROD_ENVIRONMENT

fun getEnvironment(mode: TeletypeMode = DEFAULT_MODE): TeletypeEnvironment =
    if (mode == TeletypeMode.Dev) DEV_ENVIRONMENT else PROD_ENVIRONMENT

fun TeletypeEnvironment.merged(override: TeletypeEnvironment?): TeletypeEnvironment {
    if (override == null) return this
    return TeletypeEnvironment(
        mode = override.mode.ifBlank { mode },
        version = override.version.ifBlank { version },
        apiUrl = override.apiUrl.ifBlank { apiUrl },
        wsUrl = override.wsUrl.ifBlank { wsUrl },
        assetsUrl = override.assetsUrl.ifBlank { assetsUrl },
        apiVersion = override.apiVersion.ifBlank { apiVersion },
        landingUrl = override.landingUrl.ifBlank { landingUrl },
    )
}
