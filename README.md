# CDG — Mis Gastos

App Android para controlar tus gastos **sin cargar nada a mano**: lee las notificaciones que te mandan
**Brubank** y **Cocos** cada vez que pagás, cobrás o invertís, saca el monto y el comercio, y lo categoriza solo.

## Qué hace

- **Registro automático**: cuando llega "Compraste $ 4.250 en COTO", queda guardado un gasto de $ 4.250 en *Supermercado*.
- **Tipos de movimiento**: gasto, ingreso, inversión (FCI, CEDEARs, plazo fijo, dólar MEP…) y rescate de inversión.
- **Categorías automáticas** para comercios argentinos comunes (Coto, Rappi, Uber, Netflix, Farmacity, Mercado Libre…).
  Si corregís una categoría, la app la **recuerda para ese comercio** y la aplica también a los gastos anteriores.
- **Gastos hormiga** 🐜: compras chicas (por defecto hasta $ 10.000, configurable) que no son servicios, salud ni transferencias.
  Muestra cuánto suman en el mes, qué porcentaje de tus gastos son, **cuánto serían en un año** y en qué comercios se repiten.
- **Cuánto invertís**: inversión neta del mes (invertido − rescatado) y qué porcentaje de tus ingresos representa.
- **Pesos y dólares** por separado (no se mezclan ni se convierten).
- **Carga manual** para gastos en efectivo, y **exportación a CSV**.
- **Privacidad**: todo se guarda en el teléfono. La app no tiene permiso de Internet.

## Cómo instalarla (y recibir actualizaciones)

Cada merge a `main` compila la app y publica el APK en **[Releases](https://github.com/fac05/CDG/releases)**.
La forma recomendada de instalarla es con **[Obtainium](https://github.com/ImranR98/Obtainium)**, que avisa
cuando hay una versión nueva y la instala con un toque:

1. Instalá Obtainium (desde su página de Releases o F-Droid).
2. Como el repo es privado, Obtainium necesita un token de GitHub de solo lectura:
   - En GitHub: [Settings → Developer settings → Fine-grained tokens → Generate new token](https://github.com/settings/personal-access-tokens/new).
   - *Repository access*: **Only select repositories** → `fac05/CDG`.
   - *Permissions → Repository permissions → Contents*: **Read-only**.
   - En Obtainium: **Ajustes** → sección **GitHub** → pegá el token en *GitHub Personal Access Token*.
3. En Obtainium: **Agregar app** → pegá `https://github.com/fac05/CDG` → **Agregar** → **Instalar**.
4. Si Play Protect bloquea la instalación ("accede a información sensible"): Play Store → tu foto →
   **Play Protect** → ⚙️ → apagá *Analizar apps con Play Protect*, instalá, y volvé a prenderlo.
5. Abrí *Mis Gastos* → **Habilitar** → activala en "Acceso a notificaciones".
   Si dice *"por seguridad, esta configuración no está disponible"*: Ajustes → Apps → Mis Gastos →
   **⋮** → **Permitir configuración restringida**, y volvé a activarla.
6. Recomendado: Ajustes → Apps → Mis Gastos → Batería → *Sin restricciones*, así Android no la cierra.

Las actualizaciones se instalan encima y conservan tus datos y permisos.

> Las notificaciones de Brubank y Cocos tienen que estar activadas en sus apps.

### Firma del APK

Todas las versiones se firman con la misma clave, guardada en los *secrets* del repo
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). **Guardá una copia de la clave**:
si se pierde, las versiones nuevas no se pueden instalar encima y hay que desinstalar (perdiendo los datos).

## Si una notificación no se reconoce

Los bancos cambian el texto de sus notificaciones seguido. Cuando llega una notificación de Brubank o Cocos con
números que la app no entiende, se guarda en **Ajustes → Notificaciones no reconocidas**. Con esos textos se
pueden agregar palabras clave en `core/src/main/kotlin/ar/cdg/gastos/core/NotificationParser.kt` (y un test en
`NotificationParserTest.kt`).

## Estructura

```
core/   Lógica pura en Kotlin (sin Android): parseo de notificaciones, categorías, resumen mensual y gastos hormiga.
        Tiene tests: ./gradlew :core:test
app/    App Android (Jetpack Compose): servicio que escucha notificaciones, base SQLite local y pantallas.
```

El módulo `app` solo se incluye si hay un SDK de Android instalado (`ANDROID_HOME` o `local.properties`),
así los tests de `core` corren en cualquier máquina con Java.

## Ideas para seguir

- Presupuesto mensual por categoría con alertas.
- Aviso cuando un comercio hormiga supera N compras en la semana.
- Soportar más bancos (Mercado Pago, Ualá, Naranja X): alcanza con agregar su paquete en `Bank` en `Model.kt`.
