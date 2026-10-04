package com.turbo.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Game(val name: String, val pkg: String, val path: String)
data class AppInfo(val label: String, val pkg: String)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("games", 0)
    fun load(c: Context): List<Game> {
        val a = JSONArray(p(c).getString("list", "[]"))
        return (0 until a.length()).map { val o = a.getJSONObject(it)
            Game(o.getString("n"), o.getString("p"), o.getString("s")) }
    }
    fun save(c: Context, l: List<Game>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("n", it.name).put("p", it.pkg).put("s", it.path)) }
        p(c).edit().putString("list", a.toString()).apply()
    }
}

fun launch(c: Context, g: Game) {
    // Для Winlator-форков: пробуем открыть игру напрямую через .desktop-ярлык.
    // Если Activity не экспортирована или это эмулятор, просто запускаем приложение.
    try {
        if (g.path.isBlank()) throw IllegalStateException()
        c.startActivity(Intent().setClassName(g.pkg, "${g.pkg}.XServerDisplayActivity")
            .putExtra("shortcut_path", g.path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        c.packageManager.getLaunchIntentForPackage(g.pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(it) }
    }
}

fun installedApps(c: Context): List<AppInfo> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, PackageManager.MATCH_ALL)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var games by remember { mutableStateOf(Store.load(ctx)) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Turbo Launcher") }) },
        floatingActionButton = { FloatingActionButton({ adding = true }) { Text("+") } }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games) { g ->
                Card(Modifier.fillMaxWidth().clickable { launch(ctx, g) }) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(g.name, style = MaterialTheme.typography.titleMedium); Text(g.pkg, style = MaterialTheme.typography.bodySmall) }
                        TextButton({ games = games - g; Store.save(ctx, games) }) { Text("Удалить") }
                    }
                }
            }
        }
    }
    if (adding) AddDialog(ctx, { adding = false }) { g -> games = games + g; Store.save(ctx, games); adding = false }
}

@Composable
fun AddDialog(ctx: Context, onDismiss: () -> Unit, onAdd: (Game) -> Unit) {
    val apps = remember { installedApps(ctx) }
    var q by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<AppInfo?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton({ sel?.let { onAdd(Game(name.ifBlank { it.label }, it.pkg, path)) } }) { Text("Добавить") } },
        title = { Text("Новая игра") },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
            OutlinedTextField(path, { path = it }, label = { Text("Путь к .desktop (для Winlator)") }, singleLine = true)
            OutlinedTextField(q, { q = it }, label = { Text("Поиск приложения") }, singleLine = true)
            Text("Выбрано: ${sel?.label ?: "-"}")
            LazyColumn(Modifier.height(160.dp)) {
                items(apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }) { a ->
                    Text(a.label, Modifier.fillMaxWidth().clickable { sel = a }.padding(8.dp))
                }
            }
        } })
}
