# Firma de la app: cómo se genera, se guarda y se usa la clave

> **La clave de firma no está en este repositorio y nunca debe estarlo.** Tampoco sus
> contraseñas. Este documento explica el procedimiento; los valores viven en el gestor de
> contraseñas de Mariano y en los secretos de GitHub.

## Por qué hace falta

Android identifica a una app por su `applicationId` **y su firma**. Hasta la v0.1.4 cada
corrida del CI firmaba con una clave de depuración distinta, así que actualizar en la tablet
fallaba con `INSTALL_FAILED_UPDATE_INCOMPATIBLE` y había que desinstalar, perdiendo los
ajustes. Con una clave estable, la actualización es una actualización.

Y para Google Play es obligatorio: **si se pierde la clave, no se puede volver a actualizar la
app publicada nunca más.**

## 1. Generar la clave (una sola vez, en la notebook)

```powershell
cd C:\Users\lopar\Claude\Projects\App_Photoframe
keytool -genkeypair -v -keystore photoframe-upload.jks -storetype PKCS12 ^
  -keyalg RSA -keysize 4096 -validity 10000 -alias photoframe
```

- **PKCS12** es el formato estándar actual (el viejo JKS está en desuso).
- **RSA 4096** y **10000 días** (~27 años): Play exige que la validez supere 2033.
- Pide dos contraseñas: la del archivo y la de la clave. **Que sean distintas y largas.**
- Al "nombre y apellido" respondé `Photoframe by Zambiotica`; el resto se puede dejar vacío.

El archivo `photoframe-upload.jks` **no se versiona**: `.gitignore` ya excluye `*.jks`,
`*.keystore` y `keystore.properties`.

## 2. Guardar la clave a salvo

| Qué | Dónde | Por qué |
|---|---|---|
| Las dos contraseñas | Gestor de contraseñas | Si se pierden, el archivo no sirve |
| Copia 1 del `.jks` | Comprimido con contraseña (7-Zip, AES-256) en NextCloud del Nodo 2 | Respaldo con historial |
| Copia 2 del `.jks` | Pendrive o disco externo, guardado físicamente aparte | Sobrevive a un problema del homelab |

**Una sola copia no es un respaldo.** Un disco que falla y una clave perdida son, para una app
publicada, el final de esa app: hay que republicarla con otro `applicationId` y los usuarios
instalados no reciben más actualizaciones.

## 3. Cargar los secretos en GitHub

En `Settings → Secrets and variables → Actions → New repository secret`:

| Secreto | Contenido |
|---|---|
| `PHOTOFRAME_KEYSTORE_BASE64` | El `.jks` codificado en base64 (comando abajo) |
| `PHOTOFRAME_KEYSTORE_PASSWORD` | Contraseña del archivo |
| `PHOTOFRAME_KEY_ALIAS` | `photoframe` |
| `PHOTOFRAME_KEY_PASSWORD` | Contraseña de la clave |

Para el base64, en PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("photoframe-upload.jks")) | Set-Clipboard
```

Queda en el portapapeles: se pega directamente en GitHub y **no queda un archivo suelto con la
clave en texto**. Después conviene copiar cualquier otra cosa para no dejarla en el portapapeles.

### Qué tan seguro es esto

- Los secretos de GitHub se guardan cifrados y **no se exponen en los registros**: si algo los
  imprimiera, GitHub los enmascara.
- **Los workflows disparados desde un fork no reciben secretos.** Que el repositorio sea público
  no expone la clave.
- Quien tenga permiso de escritura en el repositorio puede ejecutar acciones que usen el secreto.
  Hoy eso es solo Mariano; si algún día se suma alguien, tenerlo presente.
- El runner escribe la clave en un archivo temporal suyo (`RUNNER_TEMP`, con `umask 077`) que se
  destruye con la máquina virtual al terminar el build.

## 4. Compilar localmente con la clave (opcional)

Crear `keystore.properties` en la raíz (ya ignorado por git):

```properties
storeFile=C:\\Users\\lopar\\Claude\\Projects\\App_Photoframe\\photoframe-upload.jks
storePassword=...
keyAlias=photoframe
keyPassword=...
```

Sin ese archivo y sin los secretos, el build sigue funcionando con la firma de depuración: así
cualquiera puede clonar el repositorio y compilar.

## 5. Verificación

El CI imprime quién firmó el APK (`apksigner verify --print-certs`). Tiene que decir
`Photoframe by Zambiotica`, no `Android Debug`. Además, la prueba real es la de la tablet:
**la primera actualización que no pida desinstalar** confirma que la firma es estable.

## 6. Para Google Play (M4)

Al publicar hay que activar **Play App Signing**: Google guarda la clave con la que se firma lo
que reciben los usuarios, y la nuestra pasa a ser la *clave de subida*. Ventaja concreta: si la
clave de subida se pierde o se filtra, se puede pedir un reemplazo sin perder la app. Sin Play
App Signing, no hay red de seguridad.
