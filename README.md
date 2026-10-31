# Simplify Chat para Minecraft (Forge)

Mod de cliente que muestra los mensajes de **Twitch**, **Kick** y **TikTok** en un **overlay
transparente dentro del juego**, inspirado en
[Transparent Twitch Chat Overlay](https://github.com/baffler/Transparent-Twitch-Chat-Overlay):
mensajes apilados, colores de usuario y desvanecimiento suave, sin ensuciar el chat normal de
Minecraft.

Es de **solo lectura**: nunca envia mensajes ni pide contraseñas. Twitch y Kick se conectan
directamente; TikTok usa un pequeno puente local incluido en el proyecto.

Puedes tener **Twitch, Kick y TikTok conectados a la vez**. Los emotes de **BTTV, FFZ y 7TV**
se muestran como imagenes reales dentro del overlay, incluidos los **GIF animados**.

## Versiones

| Minecraft | Forge | Java | Jar |
|---|---|---|---|
| 1.16.5 | 36.2.42 | 11+ | `simplify_chat-1.16.5-1.1.0.jar` |
| 1.20.1 | 47.4.28 | 17 | `simplify_chat-1.20.1-1.1.0.jar` |
| 1.21.1 | 52.1.20 | 21 | `simplify_chat-1.21.1-1.1.0.jar` |
| 1.21.8 | 58.1.26 | 21 | `simplify_chat-1.21.8-1.1.0.jar` |
| 26.3 | 66.1.2 | 25 | `simplify_chat-1.1.0.jar` |

La version 1.16.5 usa Java 11 o superior por el cliente WebSocket/HTTP. Las demas usan la
version de Java que exige su Minecraft.

Cada version es un proyecto independiente dentro de `versions/`. Para compilar una:

```bat
cd versions\1.20.1
gradlew.bat build
```

El jar queda en `versions/1.20.1/build/libs/`.
## Requisitos

- Minecraft **26.3**
- Forge **26.3-66.1.2** (o superior dentro de 26.3)
- Java **25** (obligatorio para Minecraft 26.1+)
- Para TikTok: Node.js 20 o superior

> El mod es `clientSideOnly`: funciona en un mundo local y tambien en servidores, sin instalar
> nada en el servidor. No se conecta a la nada hasta que tu le dices que canal usar.

## Compilar

El proyecto ya incluye el Gradle Wrapper. Solo necesitas un JDK 25.

```bat
cd simplify_chat
set JAVA_HOME=C:\ruta\a\jdk-25
gradlew.bat build
```

El resultado queda en:

- `build/libs/simplify_chat-1.1.0.jar` - el mod
- `build/libs/simplify_chat-tiktok-bridge.zip` - el puente de TikTok (opcional)

## Instalar

1. Instala Forge 26.3-66.1.2 con el instalador oficial y crea el perfil en el launcher.
2. Copia `simplify_chat-1.1.0.jar` a `%APPDATA%\.minecraft\mods`.
3. Inicia Minecraft con el perfil de Forge.
4. Si usas TikTok, descomprime `simplify_chat-tiktok-bridge.zip` donde quieras y ejecuta
   `iniciar-puente.bat` (la primera vez instala las dependencias de Node).

> Lunar Client y otros launchers propios pueden no soportar Forge 26.3. Para esta version usa el
> launcher oficial de Minecraft o un launcher que cargue perfiles de Forge.

## Uso rapido

Dentro del juego:

```text
/streamchat add twitch TU_CANAL
/streamchat add kick TU_CANAL
/streamchat add tiktok TU_USUARIO
```

`add` agrega una fuente sin desconectar las demas. Tambien puedes usar
`/streamchat connect twitch TU_CANAL` para agregar y conectar.

Otros comandos:

```text
/streamchat sources
/streamchat status
/streamchat remove <plataforma> [canal]
/streamchat disconnect
/streamchat emotes on|off
/streamchat emotes animated on|off
/streamchat emotes size <8-28>
/streamchat emotes reload
/streamchat test
/streamchat panel
/streamchat overlay on|off|clear
/streamchat reload
/streamchat bridge
```

Al entrar a un mundo, el mod se conecta automaticamente si `enabled` es `true` y hay fuentes
guardadas. La configuracion esta en `config/streamchat-client.toml` (se crea en el primer arranque).
Las fuentes se guardan como lista, por ejemplo:

```toml
[general]
sources = ["twitch:ibai", "kick:ibai", "tiktok:usuario"]
```

## Panel de ajustes (F7)

Dentro del juego, pulsa **F7** para abrir un panel aparte desde el que puedes cambiar todo sin
tocar archivos:

- Posicion: cuatro botones de esquina.
- Margen X/Y, tamano del texto (escala), ancho.
- Opacidad del texto y del fondo.
- Color de Twitch, Kick, TikTok, mensajes, avisos, errores, fondo y separadores.
- Tamano de emote, activar/desactivar emotes y animaciones.
- Etiqueta de plataforma, formato del mensaje y prefijo de avisos.
- Vista previa en vivo.

Los cambios se aplican al instante y se guardan en `config/streamchat-client.toml`. La tecla se
puede cambiar en **Opciones -> Controles -> Simplify Chat**. Si F7 choca con otra tecla, reasignala ahi.

## Emotes (BTTV, FFZ y 7TV)

Al conectar, el mod descarga la lista global y la lista propia de cada canal de Twitch/Kick.
Cada codigo que coincida se dibuja como imagen dentro del mensaje, con su tamano original y
animacion si el proveedor entrega un GIF.

- `emotes.enabled`: activa o desactiva las imagenes.
- `emotes.animated`: reproduce los GIF animados.
- `emotes.channelSpecific`: carga tambien los emotes del canal.
- `emotes.size`: altura en pixeles de GUI (8-28).
- `emotes.providers`: lista de proveedores, por defecto `["bttv", "ffz", "7tv"]`.
- Las imagenes se guardan en `config/streamchat-emotes` para no descargarlas cada vez.
- Los emotes de Twitch nativos no se descargan; se muestran como texto. BTTV, FFZ y 7TV si
  se muestran como imagen.

## Overlay

Por defecto los mensajes **no** van al chat de Minecraft: se dibujan en un overlay transparente
en la esquina superior izquierda, con el estilo del proyecto de referencia.

- Los mensajes nuevos entran deslizandose y se desvanecen al cabo de `lifetimeSeconds`.
- Se mantienen como maximo `maxMessages` mensajes en pantalla.
- El fondo es transparente (`backgroundOpacity = 0.0`). Puedes darle color y opacidad.
- `textOpacity` controla la opacidad de todo el texto (util para un overlay mas discreto).
- Colores personalizables por plataforma, mensaje, avisos, errores, separadores y fondo.
- `messageFormat` permite cambiar el orden: `{tag}`, `{platform}`, `{name}`, `{message}`.
- `platformTag` permite cambiar `[Twitch]` por lo que quieras, o dejarlo vacio.
- `anchor` + `marginX` / `marginY` mueven el overlay; `scale` cambia el tamano del texto y
  `width` el ancho antes de partir lineas.
- `mirrorToVanillaChat = true` hace que los mensajes se vean tambien en el chat normal.
- `/streamchat overlay off` lo desactiva y manda los mensajes al chat de Minecraft.
- `/streamchat overlay clear` limpia lo que este en pantalla.
- El overlay se oculta con F1 y cuando hay una pantalla abierta (inventario, chat, menu).

Configuracion del overlay:

| Opcion | Por defecto | Descripcion |
|---|---|---|
| `enabled` | `true` | Mostrar el overlay |
| `mirrorToVanillaChat` | `false` | Copiar tambien al chat de Minecraft |
| `width` | `300` | Ancho maximo del texto antes de partir la linea |
| `maxMessages` | `12` | Mensajes visibles a la vez |
| `lifetimeSeconds` | `18` | Tiempo en pantalla |
| `scale` | `1.0` | Tamano del texto (0.5 - 2.0) |
| `anchor` | `top_left` | `top_left`, `top_right`, `bottom_left`, `bottom_right` |
| `marginX` / `marginY` | `8` | Margen respecto a la esquina |
| `textOpacity` | `1.0` | Opacidad del texto (0.05 - 1.0) |
| `backgroundOpacity` | `0.0` | Opacidad del fondo (0 = transparente) |
| `backgroundColor` | `#000000` | Color del fondo |
| `messageColor` | `#FFFFFF` | Color del mensaje |
| `systemColor` | `#AAAAAA` | Color de los avisos |
| `errorColor` | `#FF5555` | Color de los errores |
| `separatorColor` | `#888888` | Color de separadores y texto fijo |
| `twitchColor` | `#9146FF` | Color de Twitch |
| `kickColor` | `#53FC18` | Color de Kick |
| `tiktokColor` | `#25F4EE` | Color de TikTok |
| `platformTag` | `[{platform}] ` | Etiqueta de plataforma (`{platform}` se sustituye) |
| `messageFormat` | `{tag}{name}: {message}` | Formato del mensaje |
| `systemPrefix` | `[Simplify] ` | Prefijo de los avisos |
| `textShadow` | `true` | Sombra del texto |

Opciones de `[emotes]`:

| Opcion | Por defecto | Descripcion |
|---|---|---|
| `enabled` | `true` | Mostrar imagenes de emotes |
| `animated` | `true` | Reproducir GIF animados |
| `channelSpecific` | `true` | Cargar emotes del canal |
| `size` | `12` | Altura del emote en pixeles |
| `providers` | `["bttv","ffz","7tv"]` | Proveedores activos |

Ejemplos de comandos:

```text
/streamchat overlay position bottom_left
/streamchat overlay margin 20 10
/streamchat overlay scale 1.2
/streamchat overlay width 350
/streamchat overlay opacity 0.85
/streamchat overlay bgopacity 0.35
/streamchat overlay color message #FFFFFF
/streamchat overlay color background #000000
/streamchat overlay color twitch #9146FF
/streamchat overlay format {name} > {message}
/streamchat overlay tag [{platform}] 
```

Tambien puedes editar `config/streamchat-client.toml` y usar `/streamchat reload`.

## Configuracion

Opciones principales de `config/streamchat-client.toml`:

| Opcion | Por defecto | Descripcion |
|---|---|---|
| `enabled` | `true` | Conectar automaticamente al entrar a un mundo |
| `platform` | `twitch` | `twitch`, `kick` o `tiktok` |
| `channel` | vacio | Canal guardado |
| `announceStatus` | `true` | Mostrar avisos de conexion y errores |
| `showPlatformTag` | `true` | Mostrar `[Twitch]`, `[Kick]` o `[TikTok]` |
| `showNameColors` | `true` | Usar el color del usuario cuando la plataforma lo da |
| `ignoreCommands` | `true` | Ignorar mensajes que empiezan por `!` |
| `maxMessageLength` | `220` | Recorte de mensajes largos |
| `maxMessagesPerSecond` | `8` | Limite anti-flood; `0` lo desactiva |
| `blockedUsers` | `[]` | Usuarios ignorados |

Despues de editar el archivo usa `/streamchat reload` o reinicia el juego.

## TikTok

TikTok no tiene una API publica de lectura del chat, asi que el mod usa un puente local:

1. Ejecuta `iniciar-puente.bat` y deja la ventana abierta.
2. Comprueba que dice `Escuchando en ws://127.0.0.1:8787`.
3. En Minecraft: `/streamchat connect tiktok TU_USUARIO`.
4. El usuario debe estar **en directo** para que TikTok devuelva el chat.

El puente usa la libreria `tiktok-live-connector` y el servicio de firma de Euler Stream. Sin
clave funciona con el limite gratuito; si quieres mas margen, crea una clave en
<https://www.eulerstream.com> y ponla en `tiktok-bridge/config.json` (`signApiKey`), o define la
variable de entorno `TIKTOK_SIGN_API_KEY`.

Si cambias el puerto en `config.json`, cambia tambien `bridgeUrl` en
`config/streamchat-client.toml`.

## Kick

Kick cambio su sistema de chat en 2026: ahora usa Centrifugo a traves de su API interna. El mod
resuelve el canal automaticamente con `https://kick.com/api/v2/channels/TU_CANAL`.

Si Kick bloquea esa consulta (por ejemplo, error HTTP 403), abre esa URL en el navegador, busca:

- `"id"` del canal (el primer `id`, no el del usuario)
- `"chatroom": { "id": ... }`

y ponlos en la seccion `[kick]` del TOML:

```toml
[kick]
    channelId = 12345
    chatroomId = 67890
```

## Notas y limitaciones

- Solo lectura. Para escribir en el chat harian falta tokens OAuth y no esta incluido.
- BTTV, FFZ y 7TV se muestran como imagenes (incluidos GIF animados). Los emotes nativos de
  Twitch que no esten en esos proveedores se muestran como texto.
- TikTok es un protocolo no oficial: si TikTok cambia algo, actualiza el paquete del puente con
  `npm update` dentro de `tiktok-bridge`.
- El mod no envia tu IP, cookies ni credenciales a ningun sitio. Twitch y Kick se leen de forma
  anonima; TikTok pasa por el servicio de firma de Euler Stream.
- La primera vez que se abre el juego, Forge crea el archivo de configuracion; hasta entonces los
  comandos siguen funcionando con los valores por defecto.

## Estructura

```text
simplify_chat/
  build.gradle
  src/main/java/dev/streamchat/       codigo del mod
  src/main/resources/META-INF/mods.toml
  tiktok-bridge/                      puente Node.js para TikTok
  tiktok-bridge/iniciar-puente.bat
```

## Solucion de problemas

- **Twitch no muestra nada**: comprueba el nombre del canal y que el chat no este en modo
  solo-emotes o solo-subs; el IRC sigue conectado aunque no haya mensajes.
- **Kick HTTP 403**: usa los IDs manuales de la seccion `[kick]`.
- **TikTok no conecta**: el puente debe estar abierto y el usuario en directo. Revisa la ventana
  del puente para ver el error exacto.
- **Windows bloquea `iniciar-puente.bat`**: es un falso positivo de SmartScreen. En este PC usa
  el acceso directo `Iniciar TikTok.lnk`, o abre PowerShell en la carpeta `tiktok-bridge` y
  ejecuta `npm install --no-audit --no-fund` (solo la primera vez) y luego `node bridge.mjs`.
  Tambien puedes desbloquear el archivo con clic derecho -> Propiedades -> Desbloquear, o con
  `Unblock-File -LiteralPath "...\iniciar-puente.bat"`. Hay una guia corta en
  `tiktok-bridge\LEEME-BLOQUEO.txt`.
- **El puerto 8787 esta ocupado**: cambia `port` en `tiktok-bridge/config.json` y `bridgeUrl` en el
  TOML.
- **No aparece el mod**: asegurate de estar en el perfil de Forge 26.3 y de tener Java 25.
- **No se ve el overlay**: revisa que no este activado F1, que no tengas una pantalla abierta y
  ejecuta `/streamchat overlay on`.
- **El overlay se sale de la pantalla**: baja `scale`, `width` o cambia `anchor`. El mod recorta
  los mensajes mas antiguos para que quepan en la altura de la pantalla.
- **Crash con `Invalid bare key: '?[general'`**: el TOML se guardo con BOM. Abre
  `config/streamchat-client.toml` con un editor y guardalo como **UTF-8 sin BOM**, o borralo para
  que Forge lo genere de nuevo.
