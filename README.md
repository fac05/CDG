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

## Cómo instalarla

1. Descargá el APK desde la pestaña **Actions** del repo (último build → artefacto `mis-gastos-apk`),
   o compilalo con Android Studio (`./gradlew :app:assembleRelease`).
2. Instalalo en el celular (Android 8 o superior; hay que permitir "instalar apps desconocidas").
3. Abrí la app y tocá **Habilitar** → activá *Mis Gastos* en "Acceso a notificaciones".
4. Recomendado: en Ajustes de Android, quitá la optimización de batería para *Mis Gastos*, así el sistema no la cierra.

> Las notificaciones de Brubank y Cocos tienen que estar activadas en sus apps.

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
