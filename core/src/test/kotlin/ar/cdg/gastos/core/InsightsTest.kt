package ar.cdg.gastos.core

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class InsightsTest {
    private val zone = ZoneOffset.UTC

    private fun tx(
        day: Int,
        pesos: Long,
        kind: Kind = Kind.GASTO,
        merchant: String? = null,
        category: Category = Categorizer().categorize(kind, merchant),
        currency: Currency = Currency.ARS,
        month: Int = 9,
    ) = Transaction(
        timestamp = LocalDateTime.of(2026, month, day, 12, 0).toInstant(zone).toEpochMilli(),
        amountCents = pesos * 100,
        currency = currency,
        kind = kind,
        category = category,
        merchant = merchant,
        bank = Bank.BRUBANK,
    )

    @Test
    fun `categorias integradas`() {
        val c = Categorizer()
        assertEquals(Category.SUPERMERCADO, c.categorize(Kind.GASTO, "COTO SUC 123"))
        assertEquals(Category.DELIVERY, c.categorize(Kind.GASTO, "RAPPI"))
        assertEquals(Category.CAFE_KIOSCO, c.categorize(Kind.GASTO, "Cafe Martinez"))
        assertEquals(Category.SUSCRIPCIONES, c.categorize(Kind.GASTO, "NETFLIX.COM"))
        assertEquals(Category.TRANSPORTE, c.categorize(Kind.GASTO, "UBER *TRIP"))
        assertEquals(Category.TRANSFERENCIA, c.categorize(Kind.GASTO, "Juan Pérez", isTransferToPerson = true))
        assertEquals(Category.OTROS, c.categorize(Kind.GASTO, "ZZZ COMERCIO RARO"))
        assertEquals(Category.INVERSION, c.categorize(Kind.INVERSION, "FCI"))
    }

    @Test
    fun `las reglas del usuario ganan y agrupan sucursales`() {
        assertEquals("coto", Categorizer.merchantKey("COTO SUC 123"))
        assertEquals("coto", Categorizer.merchantKey("Coto Suc. 45"))
        val c = Categorizer(mapOf(Categorizer.merchantKey("ZZZ COMERCIO RARO") to Category.OCIO))
        assertEquals(Category.OCIO, c.categorize(Kind.GASTO, "zzz comercio raro"))
    }

    @Test
    fun `resumen del mes con hormigas e inversion`() {
        val txs = listOf(
            tx(1, 1_000_000, Kind.INGRESO, "EMPRESA"),
            tx(2, 200_000, Kind.INVERSION, "FCI"),
            tx(20, 50_000, Kind.RESCATE, "FCI"),
            tx(3, 80_000, merchant = "COTO"),
            tx(4, 2_500, merchant = "Cafe Martinez"),
            tx(5, 2_500, merchant = "Cafe Martinez"),
            tx(6, 3_000, merchant = "CAFE MARTINEZ SUC 2"),
            tx(7, 1_500, merchant = "KIOSCO EL PEPE"),
            tx(8, 9_000, merchant = "EDENOR"), // chico pero servicio: no es hormiga
            tx(9, 20, currency = Currency.USD, merchant = "NETFLIX"),
            tx(1, 99_999, merchant = "COTO", month = 8), // otro mes
        )
        val s = Insights.summarize(txs, YearMonth.of(2026, 9), zone)

        assertEquals(98_500_00, s.spentCents)
        assertEquals(1_000_000_00, s.incomeCents)
        assertEquals(150_000_00, s.netInvestedCents)
        assertEquals(15.0, s.investmentRate)
        assertEquals(20_00, s.spentUsdCents)

        val h = s.hormiga
        assertEquals(4, h.count)
        assertEquals(9_500_00, h.totalCents)
        assertEquals(9_500_00 * 12, h.yearlyProjectionCents)
        assertEquals(1, h.frequentMerchants.size)
        assertEquals(3, h.frequentMerchants[0].count)
        assertEquals(8_000_00, h.frequentMerchants[0].totalCents)
    }

    @Test
    fun `formato de dinero`() {
        assertEquals("$ 1.234,56", formatMoney(123456))
        assertEquals("$ 1.000.000", formatMoney(100000000))
        assertEquals("US$ 20", formatMoney(2000, Currency.USD))
        assertEquals("-$ 500", formatMoney(-50000))
    }
}
