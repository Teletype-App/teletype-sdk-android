package app.teletype.sdk.i18n

import java.util.Locale

fun normalizeLanguage(locale: String?): String {
    if (locale.isNullOrBlank()) return DEFAULT_LOCALE
    val lang = locale.split("-", "_").firstOrNull()?.lowercase().orEmpty()
    return lang.ifBlank { DEFAULT_LOCALE }
}

fun getSystemLanguage(): String = normalizeLanguage(Locale.getDefault().language)

fun resolveStrings(
    locale: String? = null,
    translations: TeletypeTranslations? = null,
): Pair<String, TeletypeStrings> {
    val language = normalizeLanguage(locale ?: getSystemLanguage())
    val builtin = BUILTIN_LOCALES[language] ?: EN_STRINGS
    val custom = translations?.get(language)
    val strings = builtin.overridden(custom)
    return language to strings
}

private const val SEC_MIN = 60
private const val SEC_HOUR = 60 * 60
private const val SEC_DAY = SEC_HOUR * 24

private val UNITS = mapOf(
    "en" to mapOf(
        "minute" to listOf("minute", "minutes"),
        "hour" to listOf("hour", "hours"),
    ),
    "ru" to mapOf(
        "minute" to listOf("минуту", "минуты", "минут"),
        "hour" to listOf("час", "часа", "часов"),
    ),
)

private val MONTHS = mapOf(
    "en" to listOf(
        "January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December",
    ),
    "ru" to listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня", "июля",
        "августа", "сентября", "октября", "ноября", "декабря",
    ),
)

private fun morph(n: Int, forms: List<String>, language: String): String {
    if (language == "ru") {
        val mod100 = kotlin.math.abs(n) % 100
        val mod10 = mod100 % 10
        return when {
            mod100 in 11..14 -> forms.getOrElse(2) { forms.last() }
            mod10 == 1 -> forms[0]
            mod10 in 2..4 -> forms.getOrElse(1) { forms.last() }
            else -> forms.getOrElse(2) { forms.last() }
        }
    }
    return if (n == 1) forms[0] else forms.getOrElse(1) { forms[0] }
}

private fun pad(n: Int): String = if (n < 10) "0$n" else n.toString()

private fun hhmm(d: java.util.Calendar): String =
    "${pad(d.get(java.util.Calendar.HOUR_OF_DAY))}:${pad(d.get(java.util.Calendar.MINUTE))}"

fun formatRelativeTime(iso: String?, language: String, strings: TeletypeStrings): String {
    if (iso.isNullOrBlank()) return ""
    val ms = try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(iso)?.time
                ?: return ""
        } catch (_: Exception) {
            return ""
        }
    }
    val date = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val units = UNITS[language] ?: UNITS.getValue("en")
    val diffSec = ((System.currentTimeMillis() - ms) / 1000).coerceAtLeast(0).toInt()

    if (diffSec < SEC_MIN) return strings.timeJustNow
    if (diffSec < SEC_HOUR) {
        val n = diffSec / SEC_MIN
        return "$n ${morph(n, units.getValue("minute"), language)} ${strings.timeAgo}"
    }
    if (diffSec < SEC_HOUR * 3) {
        val n = diffSec / SEC_HOUR
        return "$n ${morph(n, units.getValue("hour"), language)} ${strings.timeAgo}"
    }
    if (diffSec < SEC_DAY) return hhmm(date)
    if (diffSec < SEC_DAY * 2) return "${strings.timeYesterday} ${hhmm(date)}"

    val months = MONTHS[language] ?: MONTHS.getValue("en")
    val day = date.get(java.util.Calendar.DAY_OF_MONTH)
    val month = months[date.get(java.util.Calendar.MONTH)]
    val now = java.util.Calendar.getInstance()
    return if (date.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)) {
        "$day $month"
    } else {
        "$day $month, ${date.get(java.util.Calendar.YEAR)}"
    }
}
