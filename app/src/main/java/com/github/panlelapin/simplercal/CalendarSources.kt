package com.github.panlelapin.simplercal

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class CalendarRole(val labelResource: Int) {
    MAIN(R.string.calendar_main),
    BANK_HOLIDAYS(R.string.calendar_bank_holidays),
    SCHOOL_HOLIDAYS(R.string.calendar_school_holidays),
}

internal enum class CalendarSourceKind(val labelResource: Int) {
    NONE(R.string.source_none), ANDROID(R.string.source_android),
    LOCAL(R.string.source_local), HTTPS(R.string.source_https),
}

internal data class CalendarSource(
    val kind: CalendarSourceKind = CalendarSourceKind.NONE,
    val enabled: Boolean = true,
    val calendarId: Long = NO_CALENDAR_ID,
    val location: String = "",
) {
    val isFile: Boolean get() = kind == CalendarSourceKind.LOCAL || kind == CalendarSourceKind.HTTPS
    // Enabled is deliberately not part of the cache identity.
    val identity: String get() = "${kind.name}:$calendarId:$location"
}

internal data class CalendarSourceState(
    val source: CalendarSource = CalendarSource(),
    val days: List<List<CalendarEvent>> = emptyEventDays(),
    val monday: LocalDate? = null,
    val zone: ZoneId? = null,
    val loadedIdentity: String? = null,
    val failure: CalendarFailureReason? = null,
    val refreshing: Boolean = false,
    val lastSuccessMillis: Long? = null,
)

internal data class IcalSnapshot(val identity: String, val text: String, val successMillis: Long)

/** Provider IDs and document grants are local to this installation; HTTPS settings are portable. */
internal class CalendarSourcePreferences(context: Context) : SourceStore {
    private val settings = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val device = context.getSharedPreferences("calendar_device", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "ical_sources")
    private val cacheLocks = CalendarRole.entries.associateWith { Mutex() }
    private val legacyId = AppPreferences(context).selectedCalendarId()

    override fun source(role: CalendarRole): CalendarSource {
        val key = "source_${role.name}"
        val storedKind = settings.getString("${key}_kind", null)
        val kind = if (storedKind == null && role == CalendarRole.MAIN && legacyId >= 0) CalendarSourceKind.ANDROID
            else CalendarSourceKind.entries.firstOrNull { it.name == storedKind } ?: CalendarSourceKind.NONE
        val id = device.getLong("${key}_id", if (role == CalendarRole.MAIN) legacyId else NO_CALENDAR_ID)
        return CalendarSource(kind, settings.getBoolean("${key}_enabled", true), id,
            if (kind == CalendarSourceKind.LOCAL) device.getString("${key}_uri", "").orEmpty()
            else settings.getString("${key}_url", "").orEmpty())
    }

    override fun save(role: CalendarRole, source: CalendarSource) {
        val key = "source_${role.name}"
        device.edit {
            putLong("${key}_id", source.calendarId)
            putString("${key}_uri", if (source.kind == CalendarSourceKind.LOCAL) source.location else "")
        }
        settings.edit {
            putString("${key}_kind", source.kind.name)
            putBoolean("${key}_enabled", source.enabled)
            putString("${key}_url", if (source.kind == CalendarSourceKind.HTTPS) source.location else "")
        }
    }

    private fun cache(role: CalendarRole): AtomicFile = AtomicFile(File(directory, "${role.name}.json"))

    override suspend fun snapshot(role: CalendarRole, source: CalendarSource): IcalSnapshot? = cacheLocks.getValue(role).withLock {
        val file = cache(role)
        if (!file.baseFile.exists()) return@withLock null
        val json = JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        if (json.getString("identity") != source.identity) return@withLock null
        IcalSnapshot(source.identity, json.getString("text"), json.getLong("success"))
    }

    override suspend fun saveSnapshot(role: CalendarRole, snapshot: IcalSnapshot) = cacheLocks.getValue(role).withLock {
        check(directory.isDirectory || directory.mkdirs())
        val file = cache(role)
        val bytes = JSONObject().put("identity", snapshot.identity).put("text", snapshot.text)
            .put("success", snapshot.successMillis).toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}

internal fun allowedDate(date: LocalDate, year: Int): Boolean = date.year in year..year + 1
internal fun firstAllowedMonday(year: Int): LocalDate = currentWeekMonday(LocalDate.of(year, 1, 1))
internal fun lastAllowedMonday(year: Int): LocalDate = currentWeekMonday(LocalDate.of(year + 1, 12, 31))
