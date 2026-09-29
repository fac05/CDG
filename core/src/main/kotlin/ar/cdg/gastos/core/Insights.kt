package ar.cdg.gastos.core

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Qué cuenta como "gasto hormiga": compras chicas y repetidas que individualmente no
 * duelen, pero sumadas a fin de mes sí.
 */
data class HormigaSettings(
    /** Monto máximo (en centavos, ARS) para considerar un gasto como hormiga. */
    val maxAmountCents: Long = 10_000_00,
    /** Cantidad de veces en el mes a partir de la cual un comercio se marca como "frecuente". */
    val frequentCount: Int = 3,
    /** Categorías que nunca son hormiga aunque el monto sea chico. */
    val excluded: Set<Category> = setOf(
        Category.SERVICIOS, Category.SALUD, Category.TRANSFERENCIA,
        Category.INVERSION, Category.INGRESO,
    ),
)

data class MerchantTotal(val merchant: String, val count: Int, val totalCents: Long)

data class HormigaReport(
    val totalCents: Long,
    val count: Int,
    /** Porcentaje del gasto total del mes (0..100). */
    val shareOfSpending: Double,
    /** Si seguís así todo el año. */
    val yearlyProjectionCents: Long,
    val byCategory: List<Pair<Category, Long>>,
    /** Comercios donde gastaste chico muchas veces. */
    val frequentMerchants: List<MerchantTotal>,
    val transactions: List<Transaction>,
)

data class MonthSummary(
    val month: YearMonth,
    /** Totales en pesos. */
    val spentCents: Long,
    val incomeCents: Long,
    val investedCents: Long,
    val rescuedCents: Long,
    /** Totales en dólares (se muestran aparte, no se convierten). */
    val spentUsdCents: Long,
    val investedUsdCents: Long,
    val byCategory: List<Pair<Category, Long>>,
    val hormiga: HormigaReport,
) {
    val netInvestedCents: Long get() = investedCents - rescuedCents

    /** Qué parte de lo que entró se fue a inversiones (0..100), o null si no hubo ingresos. */
    val investmentRate: Double?
        get() = if (incomeCents > 0) netInvestedCents * 100.0 / incomeCents else null

    /** Lo que entró menos lo que se gastó y se invirtió. */
    val balanceCents: Long get() = incomeCents - spentCents - netInvestedCents
}

object Insights {

    fun monthOf(tx: Transaction, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(tx.timestamp).atZone(zone))

    fun summarize(
        all: List<Transaction>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
        hormiga: HormigaSettings = HormigaSettings(),
    ): MonthSummary {
        val txs = all.filter { monthOf(it, zone) == month }
        val ars = txs.filter { it.currency == Currency.ARS }
        val usd = txs.filter { it.currency == Currency.USD }

        fun List<Transaction>.sum(kind: Kind) = filter { it.kind == kind }.sumOf { it.amountCents }

        val expenses = ars.filter { it.kind == Kind.GASTO }
        return MonthSummary(
            month = month,
            spentCents = ars.sum(Kind.GASTO),
            incomeCents = ars.sum(Kind.INGRESO),
            investedCents = ars.sum(Kind.INVERSION),
            rescuedCents = ars.sum(Kind.RESCATE),
            spentUsdCents = usd.sum(Kind.GASTO),
            investedUsdCents = usd.sum(Kind.INVERSION) - usd.sum(Kind.RESCATE),
            byCategory = totalsByCategory(expenses),
            hormiga = hormigaReport(expenses, hormiga),
        )
    }

    fun isHormiga(tx: Transaction, settings: HormigaSettings): Boolean =
        tx.kind == Kind.GASTO &&
            tx.currency == Currency.ARS &&
            tx.amountCents <= settings.maxAmountCents &&
            tx.category !in settings.excluded

    fun hormigaReport(monthExpenses: List<Transaction>, settings: HormigaSettings): HormigaReport {
        val hormigas = monthExpenses.filter { isHormiga(it, settings) }
        val total = hormigas.sumOf { it.amountCents }
        val spent = monthExpenses.filter { it.kind == Kind.GASTO && it.currency == Currency.ARS }.sumOf { it.amountCents }

        val frequent = hormigas
            .groupBy { Categorizer.merchantKey(it.merchant ?: it.category.label) }
            .map { (_, list) ->
                MerchantTotal(
                    merchant = list.first().merchant ?: list.first().category.label,
                    count = list.size,
                    totalCents = list.sumOf { it.amountCents },
                )
            }
            .filter { it.count >= settings.frequentCount }
            .sortedByDescending { it.totalCents }

        return HormigaReport(
            totalCents = total,
            count = hormigas.size,
            shareOfSpending = if (spent > 0) total * 100.0 / spent else 0.0,
            yearlyProjectionCents = total * 12,
            byCategory = totalsByCategory(hormigas),
            frequentMerchants = frequent,
            transactions = hormigas.sortedByDescending { it.timestamp },
        )
    }

    private fun totalsByCategory(txs: List<Transaction>): List<Pair<Category, Long>> =
        txs.groupBy { it.category }
            .map { (cat, list) -> cat to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
}
