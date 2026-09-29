package ar.cdg.gastos.core

/**
 * Asigna categoría a un movimiento.
 *
 * Orden de prioridad:
 * 1. Reglas del usuario (cuando cambiás la categoría de un comercio, se recuerda para la próxima).
 * 2. Palabras clave conocidas de comercios argentinos.
 * 3. Valores por defecto según el tipo de movimiento.
 */
class Categorizer(private val userRules: Map<String, Category> = emptyMap()) {

    fun categorize(parsed: ParsedNotification): Category =
        categorize(parsed.kind, parsed.merchant, parsed.isTransferToPerson)

    fun categorize(kind: Kind, merchant: String?, isTransferToPerson: Boolean = false): Category {
        when (kind) {
            Kind.INGRESO -> return Category.INGRESO
            Kind.INVERSION, Kind.RESCATE -> return Category.INVERSION
            Kind.GASTO -> Unit
        }
        if (merchant != null) {
            userRules[merchantKey(merchant)]?.let { return it }
            builtIn(merchant)?.let { return it }
        }
        return if (isTransferToPerson) Category.TRANSFERENCIA else Category.OTROS
    }

    private fun builtIn(merchant: String): Category? {
        val n = " " + normalize(merchant).replace(Regex("[^a-z0-9]+"), " ") + " "
        return KEYWORDS.firstOrNull { (_, words) -> words.any { " $it" in n } }?.first
    }

    companion object {
        /**
         * Clave estable para un comercio: "COTO SUC 123" y "Coto Suc. 45" -> "coto".
         * Se usa para recordar la categoría elegida por el usuario.
         */
        fun merchantKey(merchant: String): String =
            normalize(NotificationParser.cleanMerchant(merchant) ?: merchant)
                .replace(Regex("[^a-z ]+"), " ")
                .split(' ')
                .filter { it.length > 1 && it !in NOISE_WORDS }
                .take(3)
                .joinToString(" ")
                .ifEmpty { normalize(merchant).trim() }

        private val NOISE_WORDS = setOf("suc", "sucursal", "sa", "srl", "sas", "de", "del", "la", "el", "los", "las", "y", "ar", "arg")

        // El orden importa: la primera categoría que matchea gana.
        private val KEYWORDS: List<Pair<Category, List<String>>> = listOf(
            Category.DELIVERY to listOf("rappi", "pedidosya", "pedidos ya", "glovo", "didi food", "ifood"),
            Category.SUSCRIPCIONES to listOf(
                "netflix", "spotify", "disney", "hbo", "max ", "star plus", "paramount", "prime video",
                "amazon prime", "youtube", "google one", "google storage", "icloud", "apple com", "openai",
                "chatgpt", "claude", "anthropic", "crunchyroll", "twitch", "patreon", "flow", "dgo", "directv go",
                "xbox", "playstation", "psn", "steam", "nintendo", "microsoft", "adobe", "canva", "duolingo",
            ),
            Category.TRANSPORTE to listOf(
                "uber", "cabify", "didi", "sube", "subte", "tren", "colectivo", "ypf", "shell", "axion",
                "puma energy", "gulf", "estacionamiento", "parking", "peaje", "ausa", "autopista", "aerolineas",
                "flybondi", "jetsmart", "despegar", "moovit",
            ),
            Category.SUPERMERCADO to listOf(
                "coto", "carrefour", "dia ", "supermercado dia", "jumbo", "disco", "vea", "changomas", "chango mas",
                "walmart", "la anonima", "cooperativa obrera", "maxiconsumo", "vital", "makro", "diarco", "yaguar",
                "supermercado", "super ", "autoservicio", "almacen", "verduleria", "carniceria", "dietetica",
            ),
            Category.CAFE_KIOSCO to listOf(
                "starbucks", "havanna", "cafe martinez", "martinez", "bonafide", "tostado", "le pain", "cafe",
                "coffee", "kiosco", "maxikiosco", "drugstore", "open 25", "grido", "freddo", "rapanui",
                "panaderia", "confiteria", "heladeria", "chungo", "lucciano",
            ),
            Category.COMIDA_AFUERA to listOf(
                "mcdonald", "mc donald", "burger king", "mostaza", "wendy", "kfc", "subway", "fabric sushi",
                "sushi", "pizzeria", "pizza", "parrilla", "restaurant", "resto", "bar ", "cerveceria",
                "temple", "antares", "patagonia", "hamburgues", "bodegon", "rotiseria", "empanadas",
            ),
            Category.SERVICIOS to listOf(
                "edenor", "edesur", "metrogas", "naturgy", "camuzzi", "aysa", "abl", "agip", "arba", "afip", "arca",
                "personal", "movistar", "claro", "telecentro", "fibertel", "iplan", "telecom", "expensas",
                "seguro", "la caja", "sancor", "mapfre", "federacion patronal", "rio uruguay",
            ),
            Category.SALUD to listOf(
                "farmacity", "farmacia", "farmaonline", "dr ahorro", "osde", "swiss medical", "galeno", "medife",
                "omint", "hospital", "clinica", "sanatorio", "odontolog", "optica", "gimnasio", "gym", "megatlon",
                "sportclub", "smartfit",
            ),
            Category.COMPRAS to listOf(
                "mercadolibre", "mercado libre", "meli", "amazon", "aliexpress", "shein", "temu", "tiendamia",
                "fravega", "garbarino", "musimundo", "coppel", "falabella", "easy", "sodimac", "zara", "nike",
                "adidas", "dexter", "grimoldi", "compumundo", "megatone",
            ),
            Category.OCIO to listOf(
                "cinemark", "hoyts", "showcase", "cinepolis", "ticketek", "all access", "passline", "teatro",
                "museo", "boliche", "club", "entradas",
            ),
        )
    }
}
