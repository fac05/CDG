package ar.cdg.gastos.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ar.cdg.gastos.core.Bank
import ar.cdg.gastos.core.Categorizer
import ar.cdg.gastos.core.Category
import ar.cdg.gastos.core.Currency
import ar.cdg.gastos.core.HormigaSettings
import ar.cdg.gastos.core.Kind
import ar.cdg.gastos.core.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notificación de un banco que tenía un monto pero no se pudo interpretar. */
data class UnparsedNotification(val id: Long, val timestamp: Long, val packageName: String, val text: String)

private class Db(context: Context) : SQLiteOpenHelper(context, "gastos.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                amount_cents INTEGER NOT NULL,
                currency TEXT NOT NULL,
                kind TEXT NOT NULL,
                category TEXT NOT NULL,
                merchant TEXT,
                bank TEXT NOT NULL,
                raw_text TEXT
            )""",
        )
        db.execSQL("CREATE INDEX idx_tx_ts ON transactions(ts)")
        db.execSQL("CREATE TABLE merchant_rules (merchant_key TEXT PRIMARY KEY, category TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE unparsed (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                package TEXT NOT NULL,
                text TEXT NOT NULL
            )""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * Acceso a los datos guardados en el teléfono. Nada sale del dispositivo.
 * Expone flujos para que la UI se actualice sola cuando llega una notificación nueva.
 */
class Repository(context: Context) {
    private val db = Db(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _unparsed = MutableStateFlow<List<UnparsedNotification>>(emptyList())
    val unparsed: StateFlow<List<UnparsedNotification>> = _unparsed.asStateFlow()

    private val _hormiga = MutableStateFlow(loadHormigaSettings())
    val hormigaSettings: StateFlow<HormigaSettings> = _hormiga.asStateFlow()

    init {
        refresh()
    }

    @Synchronized
    fun refresh() {
        _transactions.value = queryTransactions()
        _unparsed.value = queryUnparsed()
    }

    // ---------- Movimientos ----------

    @Synchronized
    fun insert(tx: Transaction): Long {
        val id = db.writableDatabase.insert("transactions", null, tx.toValues())
        refresh()
        return id
    }

    @Synchronized
    fun update(tx: Transaction) {
        db.writableDatabase.update("transactions", tx.toValues(), "id = ?", arrayOf(tx.id.toString()))
        refresh()
    }

    @Synchronized
    fun delete(id: Long) {
        db.writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
        refresh()
    }

    /** Evita registrar dos veces la misma notificación (Android a veces la vuelve a publicar). */
    fun isRecentDuplicate(rawText: String, now: Long, windowMillis: Long = 10 * 60 * 1000): Boolean =
        exists("transactions", "raw_text = ? AND ts > ?", rawText, now - windowMillis) ||
            exists("unparsed", "text = ? AND ts > ?", rawText, now - windowMillis)

    private fun exists(table: String, where: String, text: String, since: Long): Boolean =
        db.readableDatabase.rawQuery("SELECT 1 FROM $table WHERE $where LIMIT 1", arrayOf(text, since.toString()))
            .use { it.moveToFirst() }

    // ---------- Reglas de categoría ----------

    fun categorizer(): Categorizer = Categorizer(merchantRules())

    private fun merchantRules(): Map<String, Category> =
        db.readableDatabase.rawQuery("SELECT merchant_key, category FROM merchant_rules", null).use { c ->
            buildMap {
                while (c.moveToNext()) {
                    enumOrNull<Category>(c.getString(1))?.let { put(c.getString(0), it) }
                }
            }
        }

    /** Recuerda la categoría para este comercio y la aplica a todos sus gastos anteriores. */
    @Synchronized
    fun rememberCategory(merchant: String, category: Category) {
        val key = Categorizer.merchantKey(merchant)
        val w = db.writableDatabase
        w.insertWithOnConflict(
            "merchant_rules", null,
            ContentValues().apply {
                put("merchant_key", key)
                put("category", category.name)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        _transactions.value
            .filter { it.kind == Kind.GASTO && it.merchant != null && Categorizer.merchantKey(it.merchant!!) == key }
            .forEach { tx ->
                w.update(
                    "transactions",
                    ContentValues().apply { put("category", category.name) },
                    "id = ?", arrayOf(tx.id.toString()),
                )
            }
        refresh()
    }

    // ---------- Notificaciones no reconocidas ----------

    @Synchronized
    fun saveUnparsed(packageName: String, text: String, now: Long) {
        db.writableDatabase.insert(
            "unparsed", null,
            ContentValues().apply {
                put("ts", now)
                put("package", packageName)
                put("text", text)
            },
        )
        // Guardamos solo las últimas 200.
        db.writableDatabase.execSQL(
            "DELETE FROM unparsed WHERE id NOT IN (SELECT id FROM unparsed ORDER BY ts DESC LIMIT 200)",
        )
        refresh()
    }

    @Synchronized
    fun deleteUnparsed(id: Long) {
        db.writableDatabase.delete("unparsed", "id = ?", arrayOf(id.toString()))
        refresh()
    }

    // ---------- Ajustes ----------

    fun setHormigaMax(cents: Long) {
        prefs.edit().putLong("hormiga_max", cents).apply()
        _hormiga.value = loadHormigaSettings()
    }

    private fun loadHormigaSettings(): HormigaSettings {
        val default = HormigaSettings()
        return default.copy(maxAmountCents = prefs.getLong("hormiga_max", default.maxAmountCents))
    }

    // ---------- Exportar ----------

    fun toCsv(): String = buildString {
        appendLine("fecha,tipo,categoria,comercio,monto,moneda,banco")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        for (tx in _transactions.value) {
            val merchant = (tx.merchant ?: "").replace("\"", "\"\"")
            val amount = "%d.%02d".format(tx.amountCents / 100, tx.amountCents % 100)
            appendLine(
                "${fmt.format(java.util.Date(tx.timestamp))},${tx.kind.label},${tx.category.label}," +
                    "\"$merchant\",$amount,${tx.currency.name},${tx.bank.label}",
            )
        }
    }

    // ---------- Mapeo ----------

    private fun queryTransactions(): List<Transaction> =
        db.readableDatabase.rawQuery("SELECT * FROM transactions ORDER BY ts DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toTransaction()) }
        }

    private fun queryUnparsed(): List<UnparsedNotification> =
        db.readableDatabase.rawQuery("SELECT id, ts, package, text FROM unparsed ORDER BY ts DESC", null).use { c ->
            buildList {
                while (c.moveToNext()) add(UnparsedNotification(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)))
            }
        }

    private fun Cursor.toTransaction() = Transaction(
        id = getLong(getColumnIndexOrThrow("id")),
        timestamp = getLong(getColumnIndexOrThrow("ts")),
        amountCents = getLong(getColumnIndexOrThrow("amount_cents")),
        currency = enumOrNull<Currency>(str("currency")) ?: Currency.ARS,
        kind = enumOrNull<Kind>(str("kind")) ?: Kind.GASTO,
        category = enumOrNull<Category>(str("category")) ?: Category.OTROS,
        merchant = str("merchant"),
        bank = enumOrNull<Bank>(str("bank")) ?: Bank.MANUAL,
        rawText = str("raw_text"),
    )

    private fun Cursor.str(column: String): String? {
        val i = getColumnIndexOrThrow(column)
        return if (isNull(i)) null else getString(i)
    }

    private fun Transaction.toValues() = ContentValues().apply {
        put("ts", timestamp)
        put("amount_cents", amountCents)
        put("currency", currency.name)
        put("kind", kind.name)
        put("category", category.name)
        put("merchant", merchant)
        put("bank", bank.name)
        put("raw_text", rawText)
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }
