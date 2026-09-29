package ar.cdg.gastos.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import ar.cdg.gastos.GastosApp
import ar.cdg.gastos.core.Bank
import ar.cdg.gastos.core.NotificationParser
import ar.cdg.gastos.core.Transaction

/**
 * Escucha las notificaciones del teléfono y registra las de Brubank y Cocos.
 * Todo se procesa y guarda localmente.
 */
class BankNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val bank = Bank.fromPackage(sbn.packageName) ?: return
        val notification = sbn.notification ?: return
        // Los "resúmenes" de grupo repiten el contenido de otras notificaciones.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val body = if (!bigText.isNullOrBlank() && bigText.length >= (text?.length ?: 0)) bigText else text

        val raw = listOfNotNull(title, body).filter { it.isNotBlank() }.joinToString(". ")
        if (raw.isBlank()) return

        try {
            handle(bank, title, body, raw, sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando notificación", e)
        }
    }

    private fun handle(bank: Bank, title: String?, body: String?, raw: String, now: Long) {
        val repo = (application as GastosApp).repository
        if (repo.isRecentDuplicate(raw, now)) return

        val parsed = NotificationParser.parse(title, body)
        if (parsed == null) {
            // Si tenía números puede ser un movimiento que no supimos leer: lo guardamos para revisarlo.
            if (raw.any { it.isDigit() }) repo.saveUnparsed(bank.packageName.orEmpty(), raw, now)
            return
        }

        repo.insert(
            Transaction(
                timestamp = now,
                amountCents = parsed.amountCents,
                currency = parsed.currency,
                kind = parsed.kind,
                category = repo.categorizer().categorize(parsed),
                merchant = parsed.merchant,
                bank = bank,
                rawText = raw,
            ),
        )
    }

    companion object {
        private const val TAG = "BankNotifListener"

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, BankNotificationListener::class.java)
            return flat.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == me }
        }
    }
}
