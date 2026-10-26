package net.kravos.vesti.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import net.kravos.vesti.App
import net.kravos.vesti.data.Article
import net.kravos.vesti.data.Section

enum class Tab(val label: String, val section: Section?) {
    DANAS("Danas", null),
    SRBIJA("Srbija", Section.SRBIJA),
    SVET("Svet", Section.SVET),
    BEZBEDNOST("Bezbednost", Section.BEZBEDNOST),
    SACUVANO("Sačuvano", null),
}

sealed class Route {
    data class Story(val id: String) : Route()
    data class Reader(val article: Article) : Route()
    data class SourceFeed(val sourceId: String) : Route()
    data object Settings : Route()
}

class Navigator {
    val stack = mutableStateListOf<Route>()
    val tab = mutableStateOf(Tab.DANAS)
    fun go(r: Route) { stack.add(r) }
    fun back(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
    fun tab(t: Tab) { stack.clear(); tab.value = t }
}

val LocalNav = staticCompositionLocalOf { Navigator() }
val LocalApp = staticCompositionLocalOf<App> { error("no app") }
