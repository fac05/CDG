package ar.cdg.gastos.core

/** Tipo de movimiento. */
enum class Kind(val label: String) {
    GASTO("Gasto"),
    INGRESO("Ingreso"),
    INVERSION("Inversión"),
    RESCATE("Rescate de inversión"),
}

enum class Currency(val symbol: String) {
    ARS("$"),
    USD("US$"),
}

/** Bancos soportados, con el paquete Android de su app (de donde vienen las notificaciones). */
enum class Bank(val label: String, val packageName: String?) {
    BRUBANK("Brubank", "com.brubank"),
    COCOS("Cocos", "capital.cocos.app.twa"),
    MANUAL("Manual", null);

    companion object {
        fun fromPackage(packageName: String): Bank? = entries.firstOrNull { it.packageName == packageName }
        val watchedPackages: Set<String> = entries.mapNotNull { it.packageName }.toSet()
    }
}

enum class Category(val label: String, val emoji: String) {
    SUPERMERCADO("Supermercado", "🛒"),
    COMIDA_AFUERA("Comida afuera", "🍔"),
    DELIVERY("Delivery", "🛵"),
    CAFE_KIOSCO("Café y kiosco", "☕"),
    TRANSPORTE("Transporte", "🚕"),
    SUSCRIPCIONES("Suscripciones", "📺"),
    SERVICIOS("Servicios", "💡"),
    SALUD("Salud", "💊"),
    COMPRAS("Compras", "🛍️"),
    OCIO("Ocio", "🎉"),
    TRANSFERENCIA("Transferencias", "↔️"),
    INVERSION("Inversión", "📈"),
    INGRESO("Ingreso", "💰"),
    OTROS("Otros", "❓"),
}

/**
 * Un movimiento de plata. Los montos se guardan en centavos para no perder precisión.
 */
data class Transaction(
    val id: Long = 0,
    val timestamp: Long,
    val amountCents: Long,
    val currency: Currency,
    val kind: Kind,
    val category: Category,
    val merchant: String?,
    val bank: Bank,
    val rawText: String? = null,
)

/** Resultado de interpretar una notificación bancaria. */
data class ParsedNotification(
    val amountCents: Long,
    val currency: Currency,
    val kind: Kind,
    val merchant: String?,
    val isTransferToPerson: Boolean = false,
)

data class Money(val cents: Long, val currency: Currency) {
    override fun toString(): String = formatMoney(cents, currency)
}

/** Formatea al estilo argentino: $ 1.234,56 */
fun formatMoney(cents: Long, currency: Currency = Currency.ARS): String {
    val negative = cents < 0
    val abs = kotlin.math.abs(cents)
    val integer = abs / 100
    val decimals = abs % 100
    val grouped = integer.toString().reversed().chunked(3).joinToString(".").reversed()
    val body = if (decimals == 0L) grouped else "$grouped,${decimals.toString().padStart(2, '0')}"
    return (if (negative) "-" else "") + currency.symbol + " " + body
}
