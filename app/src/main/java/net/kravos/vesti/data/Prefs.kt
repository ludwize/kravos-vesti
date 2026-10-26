package net.kravos.vesti.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("Svetla"), DARK("Tamna") }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme
    fun setTheme(m: ThemeMode) { sp.edit().putString("theme", m.name).apply(); _theme.value = m }

    private val _latin = MutableStateFlow(sp.getBoolean("latin", true))
    /** Show everything in Latin script (transliterate Cyrillic sources). */
    val latin: StateFlow<Boolean> = _latin
    fun setLatin(v: Boolean) { sp.edit().putBoolean("latin", v).apply(); _latin.value = v }

    private val _hideEnt = MutableStateFlow(sp.getBoolean("hideEnt", true))
    /** Hide showbiz and lifestyle (sport has its own tab now). */
    val hideEnt: StateFlow<Boolean> = _hideEnt
    fun setHideEnt(v: Boolean) { sp.edit().putBoolean("hideEnt", v).apply(); _hideEnt.value = v }

    private val _textSize = MutableStateFlow(sp.getInt("textSize", 1))
    /** Reader text size step 0..3. */
    val textSize: StateFlow<Int> = _textSize
    fun setTextSize(v: Int) { val x = v.coerceIn(0, 3); sp.edit().putInt("textSize", x).apply(); _textSize.value = x }

    private val _sources = MutableStateFlow(loadSources())
    val sources: StateFlow<Set<String>> = _sources
    fun toggleSource(id: String) {
        val cur = _sources.value
        _sources.value = if (id in cur) cur - id else cur + id
        sp.edit().putString("sources", _sources.value.joinToString(",")).putBoolean("sourcesSet", true).apply()
    }

    private fun loadSources(): Set<String> {
        val defaults = Sources.ALL.filter { it.defaultOn }.map { it.id }.toSet()
        if (!sp.getBoolean("sourcesSet", false)) return defaults
        val known = Sources.ALL.map { it.id }.toSet()
        // sources added in later versions follow their default
        val seen = sp.getString("seenSources", null)?.split(',')?.toSet() ?: known
        val stored = sp.getString("sources", "")!!.split(',').filter { it in known }.toSet()
        return stored + defaults.filter { it !in seen }
    }

    init { sp.edit().putString("seenSources", Sources.ALL.joinToString(",") { it.id }).apply() }

    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) = sp.edit().putBoolean("autoUpdate", v).apply()

    var skippedBuild: Int
        get() = sp.getInt("skippedBuild", 0)
        set(v) = sp.edit().putInt("skippedBuild", v).apply()
}
