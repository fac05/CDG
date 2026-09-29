package ar.cdg.gastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.cdg.gastos.core.AmountParser
import ar.cdg.gastos.core.Bank
import ar.cdg.gastos.core.Category
import ar.cdg.gastos.core.Currency
import ar.cdg.gastos.core.Kind
import ar.cdg.gastos.core.Transaction
import ar.cdg.gastos.data.Repository

/** Alta manual (original == null) o edición de un movimiento. */
@Composable
fun TransactionDialog(repo: Repository, original: Transaction?, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(original?.let { centsToInput(it.amountCents) } ?: "") }
    var merchant by remember { mutableStateOf(original?.merchant ?: "") }
    var kind by remember { mutableStateOf(original?.kind ?: Kind.GASTO) }
    var currency by remember { mutableStateOf(original?.currency ?: Currency.ARS) }
    // Categoría elegida a mano en este diálogo (null = sugerir automáticamente).
    var pickedCategory by remember { mutableStateOf<Category?>(null) }
    var rememberForMerchant by remember { mutableStateOf(true) }

    val categorizer = remember { repo.categorizer() }
    // Si el usuario no eligió categoría: la que ya tenía el movimiento, o una sugerida según el comercio.
    val category = pickedCategory
        ?: original?.category?.takeIf { kind == original.kind }
        ?: categorizer.categorize(kind, merchant.ifBlank { null })

    val amountCents = AmountParser.toCents(amount)
    val canSave = amountCents > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Nuevo movimiento" else "Editar movimiento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Monto") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Picker(currency.symbol, Currency.entries, { it.name }) { currency = it }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Comercio o detalle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tipo")
                    Picker(kind.label, Kind.entries, { it.label }) {
                        kind = it
                        pickedCategory = null
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Categoría")
                    Picker("${category.emoji} ${category.label}", Category.entries, { "${it.emoji} ${it.label}" }) {
                        pickedCategory = it
                    }
                }
                if (kind == Kind.GASTO && merchant.isNotBlank() && pickedCategory != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberForMerchant, onCheckedChange = { rememberForMerchant = it })
                        Text("Usar siempre esta categoría para \"${merchant.trim()}\"", style = MaterialTheme.typography.bodySmall)
                    }
                }
                original?.rawText?.let {
                    Text("Notificación original: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                val cleanMerchant = merchant.trim().ifEmpty { null }
                val tx = Transaction(
                    id = original?.id ?: 0,
                    timestamp = original?.timestamp ?: System.currentTimeMillis(),
                    amountCents = amountCents,
                    currency = currency,
                    kind = kind,
                    category = category,
                    merchant = cleanMerchant,
                    bank = original?.bank ?: Bank.MANUAL,
                    rawText = original?.rawText,
                )
                if (original == null) repo.insert(tx) else repo.update(tx)
                if (rememberForMerchant && kind == Kind.GASTO && cleanMerchant != null && pickedCategory != null) {
                    repo.rememberCategory(cleanMerchant, category)
                }
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (original != null) {
                    TextButton(onClick = {
                        repo.delete(original.id)
                        onDismiss()
                    }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun <T> Picker(current: String, options: List<T>, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(current) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    onPick(option)
                    open = false
                })
            }
        }
    }
}

private fun centsToInput(cents: Long): String =
    if (cents % 100 == 0L) (cents / 100).toString() else "%d,%02d".format(cents / 100, cents % 100)
