package ar.cdg.gastos.core

import java.text.Normalizer

/** Pasa a minúsculas y saca tildes, para comparar palabras clave sin preocuparse por el formato. */
fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/** true si alguna palabra clave aparece al comienzo de una palabra ("accion" no matchea "transaccion"). */
internal fun String.hasAnyWord(keywords: List<String>): Boolean =
    keywords.any { kw -> Regex("(?<![a-z0-9])" + Regex.escape(kw)).containsMatchIn(this) }

object AmountParser {
    // Símbolo antes del número: "$ 1.234,56", "US$ 20", "U$S 15,50", "ARS 3.000".
    private val prefixed = Regex(
        """(US\$|U\${'$'}S|U\${'$'}D|USD|ARS|\$)\s?(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    // Símbolo después del número: "1.234,56 ARS", "20 USD", "5000 pesos", "10 dólares".
    private val suffixed = Regex(
        """(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s?(ARS|USD|pesos|d[oó]lares)\b""",
        RegexOption.IGNORE_CASE,
    )

    data class Match(val cents: Long, val currency: Currency, val range: IntRange)

    /** Devuelve el primer monto que aparece en el texto, o null. */
    fun find(text: String): Match? {
        val candidates = buildList {
            prefixed.find(text)?.let { m ->
                add(Match(toCents(m.groupValues[2]), currencyOf(m.groupValues[1]), m.range))
            }
            suffixed.find(text)?.let { m ->
                add(Match(toCents(m.groupValues[1]), currencyOf(m.groupValues[2]), m.range))
            }
        }
        return candidates.minByOrNull { it.range.first }
    }

    private fun currencyOf(token: String): Currency {
        val t = normalize(token)
        return if (t == "$" || t == "ars" || t == "pesos") Currency.ARS else Currency.USD
    }

    /**
     * Convierte un número escrito en formato argentino ("1.234,56") o anglo ("1,234.56") a centavos.
     * Regla: si aparece un separador seguido de 1 o 2 dígitos al final, es el decimal.
     */
    fun toCents(number: String): Long {
        val lastSep = number.indexOfLast { it == '.' || it == ',' }
        val (intPart, decPart) = if (lastSep >= 0 && number.length - lastSep - 1 in 1..2) {
            number.substring(0, lastSep) to number.substring(lastSep + 1)
        } else {
            number to ""
        }
        val integer = intPart.filter { it.isDigit() }.ifEmpty { "0" }.toLong()
        val decimals = decPart.padEnd(2, '0').toLong()
        return integer * 100 + decimals
    }
}

/**
 * Interpreta el texto de una notificación bancaria (Brubank, Cocos, etc.) y decide
 * si es un gasto, un ingreso o una inversión, cuánto fue y dónde.
 *
 * Es deliberadamente genérico (basado en palabras clave) porque los bancos cambian el
 * texto de sus notificaciones seguido. Las notificaciones que no se entienden se guardan
 * aparte en la app para poder ajustar estas reglas.
 */
object NotificationParser {

    /** Notificaciones con monto que NO son movimientos (promos, rechazos, vencimientos...). */
    private val ignoreKeywords = listOf(
        "rechaz", "no pudimos", "no se pudo", "fallid", "cancelad", "codigo", "clave", "token",
        "promo", "beneficio", "sorteo", "gana ", "hasta $", "sin interes", "vence", "vencimiento",
        "limite", "disponible para", "te ofrecemos", "preaprobad", "pendiente de aprobacion",
    )

    private val rescueKeywords = listOf(
        "rescate", "rescataste", "vendiste", "orden de venta", "venta de", "precancel",
        "vencio tu plazo fijo", "se acredito tu plazo fijo",
    )

    private val investmentKeywords = listOf(
        "invertiste", "inversion", "suscripcion", "suscribiste", "orden de compra", "compraste cedear",
        "fci", "fondo comun", "cedear", "accion", "bono", "obligacion negociable", "plazo fijo",
        "constituiste", "dolar mep", "compraste dolares", "compraste usd", "caucion",
    )

    private val incomeKeywords = listOf(
        "recibiste", "te transfirio", "te transfirieron", "te enviaron", "te envio", "ingreso",
        "acreditamos", "se acredito", "cobraste", "devolucion", "reintegro", "depositaron",
        "te pagaron", "sueldo", "haberes",
    )

    private val transferKeywords = listOf("transferiste", "enviaste", "transferencia a")

    private val expenseKeywords = listOf(
        "compraste", "compra", "pagaste", "pago", "debito", "debitamos", "consumo", "extraccion",
        "extrajiste", "retiraste", "usaste tu tarjeta", "cobro",
    ) + transferKeywords

    fun parse(title: String?, text: String?): ParsedNotification? {
        val full = listOfNotNull(title?.trim(), text?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(". ")
        if (full.isEmpty()) return null

        val amount = AmountParser.find(full) ?: return null
        if (amount.cents <= 0) return null

        val n = normalize(full)
        if (n.hasAnyWord(ignoreKeywords)) return null

        val isTransfer = n.hasAnyWord(transferKeywords)
        val kind = when {
            n.hasAnyWord(rescueKeywords) -> Kind.RESCATE
            n.hasAnyWord(investmentKeywords) && !n.hasAnyWord(incomeKeywords) -> Kind.INVERSION
            n.hasAnyWord(incomeKeywords) -> Kind.INGRESO
            n.hasAnyWord(expenseKeywords) -> Kind.GASTO
            else -> return null
        }

        return ParsedNotification(
            amountCents = amount.cents,
            currency = amount.currency,
            kind = kind,
            merchant = extractMerchant(full, amount.range),
            isTransferToPerson = kind == Kind.GASTO && isTransfer,
        )
    }

    // Corta el nombre del comercio cuando empieza otra parte de la oración.
    private const val STOP = """(?=\s+(?:con|el|desde|hoy|ayer|por|a las|mediante|usando|de tu|en tu)\b|[.!,;\n(]|$)"""
    private val beforeAmount = Regex("""\b(?:en|a)\s+(.+?)\s+(?:por|de)\s*$""", RegexOption.IGNORE_CASE)
    private val afterAmount = Regex("""^\s*(?:en|a|para)\s+(.+?)$STOP""", RegexOption.IGNORE_CASE)

    /** Intenta sacar el comercio o destinatario: "... $ 1.500 en COTO SUC 12" o "Compra en RAPPI por $ 3.000". */
    fun extractMerchant(text: String, amountRange: IntRange): String? {
        val after = text.substring(amountRange.last + 1)
        afterAmount.find(after)?.let { return cleanMerchant(it.groupValues[1]) }

        val before = text.substring(0, amountRange.first)
        beforeAmount.find(before)?.let { return cleanMerchant(it.groupValues[1]) }
        return null
    }

    private val processorPrefix = Regex(
        """^(?:MERCADOPAGO|MERPAGO|MP|DLO|DLOCAL|PAYU|SQ|PAYPAL|GOOGLE|APPLE\.COM/BILL)\s*\*\s*""",
        RegexOption.IGNORE_CASE,
    )

    fun cleanMerchant(raw: String): String? {
        val cleaned = raw.trim().replace(processorPrefix, "").replace(Regex("\\s+"), " ").trim()
        return cleaned.takeIf { it.length >= 2 }?.take(60)
    }
}
