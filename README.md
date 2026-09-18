# Photoframe by Zambiotica

Portarretrato digital para Android: pasa las fotos de una carpeta en pantalla completa, con
fundido, reloj y control remoto por ADB. Pensado para darle una segunda vida a una tablet vieja.

**Gratis, sin anuncios, sin recolección de datos y sin acceso a la red.**

- `applicationId`: `com.zambiotica.photoframe`
- `minSdk` 25 (Android 7.1) · `targetSdk` 36 (Android 16)
- Kotlin + Views (sin Compose), licencia **GPL-3.0**

## Funciones

- Pase de fotos en pantalla completa con fundido e intervalo configurable (30 s por defecto).
- Orden aleatorio sin repetir: pasa todas las fotos antes de volver a empezar.
- Ajuste **encajar con fondo difuminado**, encajar con fondo negro o recortar para llenar.
- Efecto **Ken Burns** opcional (viene apagado en equipos con poca memoria).
- Reloj y fecha superpuestos, con posición configurable.
- **Fecha en que se tomó cada foto**, leída del EXIF de la propia imagen. Si la foto no la trae,
  no se muestra nada. Sin red y sin permisos extra.
- La pantalla se mantiene encendida mientras la app está al frente; con la pantalla apagada el
  pase se detiene, para no gastar batería.
- Arranque automático después de reiniciar el equipo (hasta Android 9; ver más abajo).
- Decodificación con poca memoria: submuestreo al tamaño de la pantalla y RGB_565 en equipos chicos.

## Carpeta de fotos

- **Recomendado:** elegir la carpeta desde los ajustes, con el selector del sistema. La app no pide
  permisos de fotos restringidos.
- **Carpeta fija:** si no se eligió ninguna, usa `/sdcard/Portarretrato/`, siempre que tenga el
  permiso de lectura de almacenamiento (Android 12 o anterior).
- Formatos: JPG, PNG y WebP. Recorre subcarpetas hasta 3 niveles.

## Control remoto por ADB (Home Assistant y similares)

Recargar la lista de fotos y traer la app al frente:

```bash
adb shell am start -n com.zambiotica.photoframe/.MainActivity --ez reload true -f 0x04000000
```

La bandera `0x04000000` (`FLAG_ACTIVITY_CLEAR_TOP`) es necesaria: sin ella, si la app ya está
abierta, Android solo trae la tarea al frente y no entrega el extra `reload`.

Encender y apagar la pantalla (funciones del sistema, no de la app):

```bash
adb shell input keyevent 224   # despertar
adb shell input keyevent 223   # dormir
```

Desde Home Assistant, con la integración *Android Debug Bridge*:

```yaml
action: androidtv.adb_command
target:
  entity_id: media_player.tablet
data:
  command: "am start -n com.zambiotica.photoframe/.MainActivity --ez reload true -f 0x04000000"
```

La app también vuelve a leer la carpeta sola cada vez que se enciende la pantalla, si detecta
cambios.

## Arranque tras reiniciar

Funciona hasta Android 9. Desde Android 10 el sistema no permite abrir una app desde segundo plano
al arrancar; para esos equipos está previsto el modo protector de pantalla o usar la app como
pantalla de inicio.

## Compilar

Requiere JDK 17 (o 21) y el SDK de Android.

```bash
gradle wrapper --gradle-version 8.13   # solo la primera vez
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Instalar en una tablet por ADB en red:

```bash
adb connect 192.168.0.78:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.zambiotica.photoframe android.permission.READ_EXTERNAL_STORAGE
```

## Privacidad

La app no recolecta ni transmite ningún dato: no tiene permiso de red. Las fotos se leen del
almacenamiento del equipo y no salen de ahí. Política completa: `docs/privacy-policy.md`.

## Documentación

- `docs/Photoframe - Documentacion del Proyecto.md` — documento maestro y registro de decisiones (ADR).

## Donaciones

La app es gratis y sin anuncios. Si te resulta útil, podés invitarme un café:
**https://paypal.me/zambiotica**

(El enlace vive acá y en la página del proyecto, nunca dentro de la app publicada en Google Play:
la política de pagos de Play no permite links a medios de pago externos.)

## Licencia

GPL-3.0. Ver `LICENSE`.
