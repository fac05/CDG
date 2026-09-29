package ar.cdg.gastos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationParserTest {

    @Test
    fun `montos en formato argentino y anglo`() {
        assertEquals(123456, AmountParser.toCents("1.234,56"))
        assertEquals(123456, AmountParser.toCents("1,234.56"))
        assertEquals(150000, AmountParser.toCents("1.500"))
        assertEquals(1550, AmountParser.toCents("15,50"))
        assertEquals(1550, AmountParser.toCents("15.5"))
        assertEquals(100000000, AmountParser.toCents("1.000.000"))
        assertEquals(500000, AmountParser.toCents("5000"))
    }

    @Test
    fun `detecta moneda`() {
        assertEquals(Currency.USD, AmountParser.find("Pagaste US$ 20,99 en NETFLIX")!!.currency)
        assertEquals(Currency.USD, AmountParser.find("Compra por U\$S 15 en STEAM")!!.currency)
        assertEquals(Currency.USD, AmountParser.find("Se debitaron 9,99 USD")!!.currency)
        assertEquals(Currency.ARS, AmountParser.find("Pagaste $ 3.500 en KIOSCO")!!.currency)
    }

    @Test
    fun `compra con tarjeta Brubank`() {
        val p = NotificationParser.parse("Compra aprobada", "Compraste $ 4.250,00 en COTO SUC 123 con tu tarjeta de débito")!!
        assertEquals(Kind.GASTO, p.kind)
        assertEquals(425000, p.amountCents)
        assertEquals(Currency.ARS, p.currency)
        assertEquals("COTO SUC 123", p.merchant)
    }

    @Test
    fun `comercio antes del monto y prefijo de procesador`() {
        val p = NotificationParser.parse("Brubank", "Compra en MERCADOPAGO*RAPPI por $ 12.300")!!
        assertEquals(Kind.GASTO, p.kind)
        assertEquals(1230000, p.amountCents)
        assertEquals("RAPPI", p.merchant)
    }

    @Test
    fun `pago con QR en Cocos`() {
        val p = NotificationParser.parse("Pago realizado", "Pagaste $1.800 a Cafe Martinez.")!!
        assertEquals(Kind.GASTO, p.kind)
        assertEquals(180000, p.amountCents)
        assertEquals("Cafe Martinez", p.merchant)
    }

    @Test
    fun `transferencia enviada a una persona`() {
        val p = NotificationParser.parse("Transferencia enviada", "Transferiste $ 20.000 a Juan Pérez")!!
        assertEquals(Kind.GASTO, p.kind)
        assertTrue(p.isTransferToPerson)
        assertEquals("Juan Pérez", p.merchant)
    }

    @Test
    fun `transferencia recibida es ingreso`() {
        val p = NotificationParser.parse("¡Te llegó plata!", "Recibiste $ 850.000 de EMPRESA SA")!!
        assertEquals(Kind.INGRESO, p.kind)
        assertEquals(85000000, p.amountCents)
        assertFalse(p.isTransferToPerson)
    }

    @Test
    fun `inversion en FCI y rescate`() {
        val inv = NotificationParser.parse("Cocos", "Invertiste $ 100.000 en Cocos Ahorro FCI")!!
        assertEquals(Kind.INVERSION, inv.kind)
        assertEquals(10000000, inv.amountCents)

        val cedear = NotificationParser.parse("Orden ejecutada", "Tu orden de compra de 10 CEDEAR AAPL por $ 250.000 fue ejecutada")!!
        assertEquals(Kind.INVERSION, cedear.kind)

        val rescate = NotificationParser.parse("Cocos", "Rescataste $ 30.000 de Cocos Ahorro")!!
        assertEquals(Kind.RESCATE, rescate.kind)

        val pf = NotificationParser.parse("Brubank", "Constituiste un plazo fijo por $ 500.000")!!
        assertEquals(Kind.INVERSION, pf.kind)
    }

    @Test
    fun `ignora promociones, rechazos y vencimientos`() {
        assertNull(NotificationParser.parse("Promo", "Hasta $ 5.000 de reintegro en supermercados"))
        assertNull(NotificationParser.parse("Compra rechazada", "Tu compra de $ 3.000 en KIOSCO fue rechazada"))
        assertNull(NotificationParser.parse("Resumen", "Tu resumen de $ 120.000 vence el 10/10"))
        assertNull(NotificationParser.parse("Código", "Tu código de seguridad es 123456"))
        assertNull(NotificationParser.parse("Brubank", "Tenés una nueva notificación"))
    }

    @Test
    fun `transaccion no se confunde con accion`() {
        val p = NotificationParser.parse("Brubank", "Transacción aprobada: pagaste $ 900 en GRIDO")!!
        assertEquals(Kind.GASTO, p.kind)
        assertEquals("GRIDO", p.merchant)
    }
}
