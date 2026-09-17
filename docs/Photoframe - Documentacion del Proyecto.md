# Photoframe by Zambiotica — Documentación del Proyecto

| Campo | Valor |
|---|---|
| **Producto** | Photoframe by Zambiotica — portarretrato digital para Android |
| **`applicationId`** | `com.zambiotica.photoframe` (permanente) |
| **Licencia** | GPL-3.0 · repo público `mlopardo/Photoframe` |
| **SDK** | `minSdk 25` · `compileSdk` / `targetSdk 36` |
| **Estado** | M1 — probado en la TabZambiótica el 2026-09-17; v0.1.1 corrige lo observado |
| **Versión del documento** | 0.1.1 |
| **Última actualización** | 2026-09-17 |
| **Líder técnico** | Apu (asesor Android) + Mariano |

> Documento maestro y **vivo**. Todo cambio de alcance o decisión técnica se refleja acá y, si
> corresponde, en el [Registro de decisiones (ADR)](#5-registro-de-decisiones-de-arquitectura-adr).

---

## 1. Objetivo

Reemplazar a Fotoo (`com.bo.fotoo`) en la TabZambiótica —una Galaxy Tab 2 con LineageOS 14.1
(Android 7.1.2, API 25)— con una app propia, y después publicarla en Google Play como utilidad
gratuita para darle una segunda vida a tablets viejas.

## 2. Alcance del MVP

- Pase de fotos en pantalla completa, con fundido (1,5 s) e intervalo configurable (30 s por defecto).
- Orden aleatorio sin repetir, aleatorio puro o por nombre de archivo.
- Ajuste: encajar con fondo difuminado (por defecto), encajar con fondo negro o recortar para llenar.
- Efecto Ken Burns opcional, apagado por defecto en equipos con poca memoria.
- Reloj y fecha superpuestos, con posición configurable (abajo a la derecha por defecto).
- Pantalla encendida solo mientras la app está al frente; el pase se detiene con la pantalla apagada.
- Arranque tras reiniciar el equipo (hasta Android 9).
- Recarga de la lista de fotos por ADB y al encenderse la pantalla.
- Decodificación con poca memoria.
- Interfaz: toque = barra de controles por 3 s; toque largo = ajustes.

**Fuera del MVP:** Android TV, HEIC, modo protector de pantalla, horarios propios, sincronización
con la nube.

## 3. Arquitectura

Deliberadamente chata: una sola pantalla y una de ajustes. No hay red, ni base de datos, ni
inyección de dependencias.

```
MainActivity      Ciclo de vida, pase de fotos, fundido, Ken Burns, reloj, gestos
SettingsActivity  PreferenceFragmentCompat + selector de carpeta (SAF)
BootReceiver      Arranque tras reiniciar (solo API <= 28)
Prefs             Único punto de lectura de la configuración
PhotoScanner      Busca las fotos (SAF o carpeta fija) y calcula una huella para detectar cambios
PhotoDecoder      Submuestreo, rotación EXIF y miniatura para el fondo difuminado
ShuffleBag        Orden aleatorio sin repetir (Kotlin puro, con test de regresión)
```

Las clases con lógica pura —`ShuffleBag`, `PhotoScanner.isSupported`, `PhotoDecoder.sampleSize`—
son las que cubre el CI con tests de JVM. El resto se verifica en el dispositivo.

## 4. Interfaz con Home Assistant (proyecto P2)

- **Carpeta:** `/sdcard/Portarretrato/`, con el permiso otorgado por `pm grant`.
- **Recarga:** `am start -n com.zambiotica.photoframe/.MainActivity --ez reload true -f 0x04000000`.
  La bandera `CLEAR_TOP` es obligatoria: sin ella, con la app ya abierta, Android solo trae la
  tarea al frente y el extra nunca llega a `onNewIntent` (comprobado en la Tab el 2026-09-17).
  Red de seguridad: la app recalcula la huella de la carpeta al volver al frente y recarga si cambió.
- **Pantalla:** la maneja HA con `input keyevent 224` y `223`. La app no los pisa: mantiene la
  pantalla encendida solo mientras está al frente.
- **P2 debe** subir a una carpeta temporal, reemplazar la carpeta y recién después recargar.

## 5. Registro de decisiones de arquitectura (ADR)

### ADR-001 — UI con Views, no Compose
**Estado:** Aceptado · **2026-09-17**
Se usa el sistema de Views clásico con AndroidX Preference, y no Jetpack Compose (que sí usa
RowCounter, ADR-001 de ese proyecto).
**Motivo:** el equipo objetivo es una Galaxy Tab 2 de 32 bits con ~1 GB de RAM. Compose suma unos
5 MB al APK y un primer arranque más lento, sin aportar nada a una pantalla que son dos ImageView
con fundido.
**Consecuencia:** no se comparte código de UI con RowCounter. Tampoco se usa Hilt: las
dependencias son tan pocas que se instancian a mano.

### ADR-002 — `applicationId` y SDK
**Estado:** Aceptado · **2026-09-17**
`com.zambiotica.photoframe`, `minSdk 25`, `compileSdk` y `targetSdk 36`.
**Motivo:** 25 es la Tab 2; 36 lo exige Play para apps nuevas desde el 31-ago-2026. El
`applicationId` no se puede cambiar después de publicar.
**Consecuencia:** hay que probar en los dos extremos: la Tab real y un emulador con API 36.

### ADR-003 — Origen de las fotos sin permisos restringidos
**Estado:** Aceptado · **2026-09-17**
El público elige la carpeta con `ACTION_OPEN_DOCUMENT_TREE` (permiso persistente). Como respaldo,
si no hay carpeta elegida, se usa `/sdcard/Portarretrato/` con `READ_EXTERNAL_STORAGE`
(`maxSdkVersion 32`).
**Motivo:** `READ_MEDIA_IMAGES` es un permiso restringido por la política de fotos de Play, exige
declaración y justificación. Con SAF no hace falta.
**Consecuencia:** en la Tab (API 25) el permiso se otorga por ADB y la carpeta fija funciona sin
tocar la pantalla.

### ADR-004 — Recarga por `am start`, no por broadcast
**Estado:** Aceptado · **2026-09-17**
La recarga llega como un extra booleano en la actividad principal, que ya está exportada.
**Motivo:** un receptor protegido con permiso de firma no se puede invocar desde el shell de ADB,
y uno exportado sin protección es superficie de ataque gratis. Además `am start` trae la app al
frente, que es lo que se quiere cuando HA recarga las fotos.
**Consecuencia:** `launchMode="singleTask"` y manejo en `onNewIntent`.

### ADR-005 — Cero recolección de datos y sin permiso de red
**Estado:** Aceptado · **2026-09-17**
Sin `INTERNET`, sin analítica y sin reporte de fallas.
**Motivo:** Data safety queda en "no recopila ni comparte datos", que es lo más simple de
declarar y de sostener; y es coherente con un marco de fotos familiares.
**Consecuencia:** los errores se diagnostican con `adb logcat`. Si algún día hace falta reporte de
fallas, ACRA por correo es la opción que no rompe esta decisión.

### ADR-006 — Donaciones fuera de la app
**Estado:** Aceptado · **2026-09-17**
El enlace de PayPal va en el README, en la página de GitHub Pages y en la ficha de F-Droid. El
build de Play no lleva botón de donar.
**Motivo:** la política de pagos de Play prohíbe llevar al usuario a un medio de pago externo; la
excepción son las donaciones exentas de impuestos, que no es este caso.
**Consecuencia:** si más adelante se quiere un botón dentro de la app, va en una variante de build
para F-Droid y GitHub.

### ADR-007 — Ken Burns por propiedades de la vista
**Estado:** Aceptado · **2026-09-17**
El zoom y el paneo se hacen animando `scaleX`, `scaleY` y `translationX` del ImageView, con capa
de hardware, y no redecodificando ni redibujando el bitmap.
**Motivo:** en la Tab 2 cualquier cosa que toque el bitmap por cuadro va a saltar.
**Consecuencia:** el recorte del efecto es fijo (8% de zoom). Queda medir el rendimiento real en la
Tab en M1.

### ADR-008 — Fondo difuminado con desenfoque real
**Estado:** Aceptado · **2026-09-17** (reemplaza la primera implementación)
El fondo se arma con una miniatura de 160 px, un desenfoque de caja de radio 6 en dos pasadas
y un oscurecido al 55%.
**Motivo:** la primera versión estiraba una miniatura de 48 px sin desenfocar. En la Tab se veía
como un recorte ampliado y pixelado, sobre todo con fotos verticales, donde el fondo ocupa media
pantalla (reportado por Mariano en la primera prueba real).
**Costo:** el desenfoque corre sobre ~16.000 píxeles; es despreciable incluso en la Tab 2. El
algoritmo está verificado contra una implementación de referencia (diferencia 0 en 6 formas
distintas, incluidos bordes de 2×2 y 200×3).

### ADR-009 — El marco se protege de los toques accidentales
**Estado:** Aceptado · **2026-09-17**
Se abandona `GestureDetector`: los toques se manejan a mano. Se ignoran los toques durante 2 s
después de que la app vuelve al frente, el toque largo pasa de 500 ms a **2 s de dedo quieto**
(tolerancia de 24 px) y los Ajustes **se cierran solos a los 2 minutos** sin uso.
**Motivo:** en la primera prueba, un ciclo de dormir y despertar dejó los Ajustes al frente en
lugar del marco. Un portarretrato que se va a Ajustes y no vuelve es un portarretrato roto.
**Consecuencia:** las reglas viven en `InteractionRules`, en Kotlin puro, con test de regresión
en el CI.

## 6. Plan por milestones

Ver `PLAN - P1 Photoframe by Zambiotica.md` en la carpeta del proyecto Home_Assistant_Helpdesk.
Resumen: **M0** fundaciones · **M1** MVP en la tablet · **M2** reemplazo de Fotoo · **M3**
integración con HA · **M4** preparación para Play · **M5** test cerrado y producción.

## 7. Pendientes

- Cuenta de Play Console: crearla y verificarla **antes de M4**.
- Enlace PayPal.me para `.github/FUNDING.yml` y el README.
- Ícono definitivo y paleta (M4).
- Medir Ken Burns en la Tab real (en la primera prueba estaba apagado, como corresponde por RAM).
- Memoria medida en la Tab: ~14 MB con fotos de 1280×800 y ~30 MB con fotos de cámara, sin OOM.
- Confirmar por ADB la resolución de la Tab (`wm size`) y si tiene bloqueo de pantalla.

---

**Nunca se guardan contraseñas, claves ni el keystore en este documento ni en el repositorio.**
