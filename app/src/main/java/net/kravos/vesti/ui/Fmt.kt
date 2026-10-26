package net.kravos.vesti.ui

import android.content.Context
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val DAYS = listOf("ponedeljak", "utorak", "sreda", "četvrtak", "petak", "subota", "nedelja")
    private val MONTHS = listOf("januar", "februar", "mart", "april", "maj", "jun", "jul", "avgust", "septembar", "oktobar", "novembar", "decembar")
    private val MON = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "avg", "sep", "okt", "nov", "dec")

    /** "ponedeljak, 26. oktobar" */
    fun masthead(): String {
        val d = LocalDate.now(zone)
        return "${DAYS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth}. ${MONTHS[d.monthValue - 1]}"
    }

    /** "upravo", "pre 12 min", "pre 3 h", "juče 18:40", "24. okt" */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val m = (now - ms) / 60_000
        if (m < 1) return "upravo"
        if (m < 60) return "pre $m min"
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        val today = LocalDate.now(zone)
        if (m < 12 * 60 || t.toLocalDate() == today) return "pre ${m / 60} h"
        if (t.toLocalDate() == today.minusDays(1)) return "juče %02d:%02d".format(t.hour, t.minute)
        return "${t.dayOfMonth}. ${MON[t.monthValue - 1]}"
    }

    fun izvora(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "$n izvor"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n izvora"
        else -> "$n izvora"
    }

    fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

    /** The emulator smoke test runs with animations off; infinite spinners would break uiautomator dumps. */
    fun animationsOff(ctx: Context): Boolean =
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
}
