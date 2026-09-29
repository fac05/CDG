package ar.cdg.gastos.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.cdg.gastos.GastosApp
import ar.cdg.gastos.core.Insights
import ar.cdg.gastos.core.Transaction
import ar.cdg.gastos.data.Repository
import ar.cdg.gastos.service.BankNotificationListener
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as GastosApp).repository
        setContent {
            GastosTheme {
                GastosRoot(
                    repo = repo,
                    listenerEnabled = listenerEnabled,
                    openListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El usuario puede haber vuelto de Ajustes de Android después de habilitar el permiso.
        listenerEnabled = BankNotificationListener.isEnabled(this)
        (application as GastosApp).repository.refresh()
    }
}

val SPANISH: Locale = Locale.forLanguageTag("es-AR")

fun YearMonth.label(): String =
    format(DateTimeFormatter.ofPattern("MMMM yyyy", SPANISH)).replaceFirstChar { it.uppercase() }

private enum class Tab(val label: String, val icon: ImageVector) {
    RESUMEN("Resumen", Icons.Filled.Home),
    MOVIMIENTOS("Movimientos", Icons.AutoMirrored.Filled.List),
    HORMIGA("Hormiga", Icons.Filled.Warning),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GastosRoot(repo: Repository, listenerEnabled: Boolean, openListenerSettings: () -> Unit) {
    val transactions by repo.transactions.collectAsStateWithLifecycle()
    val unparsed by repo.unparsed.collectAsStateWithLifecycle()
    val hormigaSettings by repo.hormigaSettings.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(Tab.RESUMEN) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var adding by remember { mutableStateOf(false) }

    val summary = remember(transactions, month, hormigaSettings) {
        Insights.summarize(transactions, month, hormiga = hormigaSettings)
    }
    val monthTransactions = remember(transactions, month) {
        transactions.filter { Insights.monthOf(it, java.time.ZoneId.systemDefault()) == month }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.AJUSTES) "Ajustes" else "Mis Gastos") },
                actions = {
                    if (tab != Tab.AJUSTES) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { month = month.minusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior")
                            }
                            Text(month.label(), style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente")
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.RESUMEN || tab == Tab.MOVIMIENTOS) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar movimiento")
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.RESUMEN -> SummaryScreen(
                summary = summary,
                listenerEnabled = listenerEnabled,
                onEnableListener = openListenerSettings,
                onOpenHormiga = { tab = Tab.HORMIGA },
                modifier = modifier,
            )
            Tab.MOVIMIENTOS -> MovementsScreen(
                transactions = monthTransactions,
                hormigaSettings = hormigaSettings,
                onClick = { editing = it },
                modifier = modifier,
            )
            Tab.HORMIGA -> HormigaScreen(
                summary = summary,
                settings = hormigaSettings,
                onClick = { editing = it },
                modifier = modifier,
            )
            Tab.AJUSTES -> SettingsScreen(
                repo = repo,
                listenerEnabled = listenerEnabled,
                onEnableListener = openListenerSettings,
                hormigaSettings = hormigaSettings,
                unparsed = unparsed,
                modifier = modifier,
            )
        }
    }

    editing?.let { tx ->
        TransactionDialog(repo = repo, original = tx, onDismiss = { editing = null })
    }
    if (adding) {
        TransactionDialog(repo = repo, original = null, onDismiss = { adding = false })
    }
}

@Composable
fun GastosTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF7FD6B0))
        else -> lightColorScheme(primary = Color(0xFF1B6E53))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
