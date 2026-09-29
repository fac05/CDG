package ar.cdg.gastos.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.cdg.gastos.core.AmountParser
import ar.cdg.gastos.core.Category
import ar.cdg.gastos.core.Currency
import ar.cdg.gastos.core.HormigaSettings
import ar.cdg.gastos.core.Insights
import ar.cdg.gastos.core.Kind
import ar.cdg.gastos.core.MonthSummary
import ar.cdg.gastos.core.Transaction
import ar.cdg.gastos.core.formatMoney
import ar.cdg.gastos.data.Repository
import ar.cdg.gastos.data.UnparsedNotification
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm", SPANISH)
private fun formatDate(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dateFormat)

private val incomeGreen = Color(0xFF2E7D32)

// ---------------------------------------------------------------- Resumen

@Composable
fun SummaryScreen(
    summary: MonthSummary,
    listenerEnabled: Boolean,
    onEnableListener: () -> Unit,
    onOpenHormiga: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!listenerEnabled) {
            item { EnableListenerCard(onEnableListener) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Gastaste", formatMoney(summary.spentCents), Modifier.weight(1f))
                StatCard("Ingresó", formatMoney(summary.incomeCents), Modifier.weight(1f), valueColor = incomeGreen)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "Invertiste (neto)",
                    formatMoney(summary.netInvestedCents),
                    Modifier.weight(1f),
                    footnote = summary.investmentRate?.let { "%.0f%% de lo que entró".format(it) },
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                StatCard(
                    "Te quedó",
                    formatMoney(summary.balanceCents),
                    Modifier.weight(1f),
                    footnote = "ingresos − gastos − inversión",
                    valueColor = if (summary.balanceCents < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        }
        if (summary.spentUsdCents != 0L || summary.investedUsdCents != 0L) {
            item {
                Text(
                    "En dólares: gastaste ${formatMoney(summary.spentUsdCents, Currency.USD)} · " +
                        "invertiste ${formatMoney(summary.investedUsdCents, Currency.USD)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            val h = summary.hormiga
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onOpenHormiga),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🐜 Gastos hormiga", style = MaterialTheme.typography.titleMedium)
                    Text(formatMoney(h.totalCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${h.count} compras chicas · %.0f%% de tus gastos · en un año serían %s"
                            .format(h.shareOfSpending, formatMoney(h.yearlyProjectionCents)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        categoryBreakdown("Gastos por categoría", summary.byCategory)
    }
}

@Composable
private fun EnableListenerCard(onEnable: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Falta un paso", style = MaterialTheme.typography.titleMedium)
            Text(
                "Para registrar tus gastos automáticamente, habilitá \"Mis Gastos\" en el acceso a notificaciones. " +
                    "Solo se procesan las notificaciones de Brubank y Cocos, y todo queda en tu teléfono.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onEnable) { Text("Habilitar") }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    valueColor: Color = Color.Unspecified,
) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
            if (footnote != null) Text(footnote, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun LazyListScope.categoryBreakdown(title: String, totals: List<Pair<Category, Long>>) {
    if (totals.isEmpty()) return
    val max = totals.maxOf { it.second }.coerceAtLeast(1)
    item { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
    items(totals, key = { "cat-$title-${it.first}" }) { (cat, cents) ->
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${cat.emoji} ${cat.label}")
                Text(formatMoney(cents), fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(
                progress = { cents.toFloat() / max },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Movimientos

@Composable
fun MovementsScreen(
    transactions: List<Transaction>,
    hormigaSettings: HormigaSettings,
    onClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (transactions.isEmpty()) {
        EmptyState(
            "Todavía no hay movimientos este mes.\nCuando pagues con Brubank o Cocos van a aparecer acá, " +
                "o podés cargarlos a mano con el botón +.",
            modifier,
        )
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(transactions, key = { it.id }) { tx ->
            TransactionRow(tx, isHormiga = Insights.isHormiga(tx, hormigaSettings), onClick = { onClick(tx) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, isHormiga: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tx.category.emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (tx.merchant ?: tx.category.label) + if (isHormiga) " 🐜" else "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${tx.category.label} · ${tx.bank.label} · ${formatDate(tx.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val (sign, color) = when (tx.kind) {
            Kind.GASTO -> "−" to Color.Unspecified
            Kind.INGRESO -> "+" to incomeGreen
            Kind.INVERSION -> "→ " to MaterialTheme.colorScheme.primary
            Kind.RESCATE -> "← " to MaterialTheme.colorScheme.primary
        }
        Text(sign + formatMoney(tx.amountCents, tx.currency), color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Hormiga

@Composable
fun HormigaScreen(
    summary: MonthSummary,
    settings: HormigaSettings,
    onClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val h = summary.hormiga
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🐜 Gastos hormiga de ${summary.month.label()}", style = MaterialTheme.typography.titleMedium)
                    Text(formatMoney(h.totalCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${h.count} compras de hasta ${formatMoney(settings.maxAmountCents)}")
                    Text("Son el %.0f%% de todo lo que gastaste".format(h.shareOfSpending))
                    Text("Si seguís así, en un año se van ${formatMoney(h.yearlyProjectionCents)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (h.frequentMerchants.isNotEmpty()) {
            item { SectionTitle("Dónde se te van (${settings.frequentCount}+ veces en el mes)") }
            items(h.frequentMerchants) { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${m.merchant} · ${m.count} veces", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatMoney(m.totalCents), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (h.byCategory.isNotEmpty()) {
            item { SectionTitle("Por categoría") }
            items(h.byCategory, key = { "hcat-${it.first}" }) { (cat, cents) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${cat.emoji} ${cat.label}")
                    Text(formatMoney(cents), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (h.transactions.isNotEmpty()) {
            item { SectionTitle("Todas las compras hormiga") }
            items(h.transactions, key = { "htx-${it.id}" }) { tx ->
                TransactionRow(tx, isHormiga = false, onClick = { onClick(tx) })
                HorizontalDivider()
            }
        } else {
            item {
                Text(
                    "No hay gastos hormiga este mes. 🎉",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
}

// ---------------------------------------------------------------- Ajustes

@Composable
fun SettingsScreen(
    repo: Repository,
    listenerEnabled: Boolean,
    onEnableListener: () -> Unit,
    hormigaSettings: HormigaSettings,
    unparsed: List<UnparsedNotification>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var threshold by remember(hormigaSettings.maxAmountCents) {
        mutableStateOf((hormigaSettings.maxAmountCents / 100).toString())
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lectura de notificaciones", style = MaterialTheme.typography.titleMedium)
                    Text(if (listenerEnabled) "✅ Activada" else "❌ Desactivada")
                    Text(
                        "Se leen solo las notificaciones de Brubank y Cocos. Si Android cierra la app para ahorrar batería, " +
                            "desactivá la optimización de batería para \"Mis Gastos\".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onEnableListener) { Text("Abrir ajustes de Android") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gasto hormiga", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Compras chicas que no son servicios, salud ni transferencias. Elegí el monto máximo:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = threshold,
                            onValueChange = { threshold = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            prefix = { Text("$ ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { AmountParser.toCents(threshold).takeIf { it > 0 }?.let(repo::setHormigaMax) },
                        ) { Text("Guardar") }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exportar", style = MaterialTheme.typography.titleMedium)
                    Text("Compartí todos tus movimientos como CSV (para Excel o Google Sheets).", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Mis gastos (CSV)")
                            putExtra(Intent.EXTRA_TEXT, repo.toCsv())
                        }
                        context.startActivity(Intent.createChooser(send, "Exportar movimientos"))
                    }) { Text("Compartir CSV") }
                }
            }
        }
        item {
            Column {
                Text("Notificaciones no reconocidas (${unparsed.size})", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Notificaciones de tus bancos con números que no parecían un gasto, ingreso ni inversión. " +
                        "Si alguna era un movimiento real, cargalo a mano y guardá el texto para mejorar el reconocimiento.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        items(unparsed, key = { "unp-${it.id}" }) { u ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${formatDate(u.timestamp)} · ${u.packageName}", style = MaterialTheme.typography.labelSmall)
                        Text(u.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { repo.deleteUnparsed(u.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar")
                    }
                }
            }
        }
    }
}
