# Puente de TikTok para Simplify Chat

Este programa conecta el chat de un directo de TikTok con el mod Simplify Chat de Minecraft.
Es necesario porque TikTok no ofrece una API publica para leer el chat.

## Uso

1. Instala Node.js 20 o superior: <https://nodejs.org/>
2. Ejecuta `iniciar-puente.bat`.
   - La primera vez descarga las dependencias con `npm install`.
3. Deja la ventana abierta. Debe mostrar:
   `Escuchando en ws://127.0.0.1:8787`
4. En Minecraft: `/streamchat add tiktok TU_USUARIO`.
5. El usuario debe estar en directo y con el chat habilitado; veras sus comentarios
   en el overlay como `[TikTok] Nombre: mensaje`.

## Configuracion

`config.json`:

```json
{
  "port": 8787,
  "signApiKey": ""
}
```

- `port`: puerto local. Si lo cambias, cambia tambien `bridgeUrl` en
  `config/streamchat-client.toml` del mod.
- `signApiKey`: clave opcional de <https://www.eulerstream.com>. Sin clave se usa el limite
  gratuito del servicio de firma.

Tambien puedes usar variables de entorno:

```bat
set TIKTOK_SIGN_API_KEY=tu_clave
set STREAMCHAT_PORT=8787
iniciar-puente.bat
```

## Protocolo (por si quieres hacer otro cliente)

El puente escucha en `ws://127.0.0.1:8787` y habla JSON.

El cliente envia:

```json
{"type":"join","channel":"usuario_de_tiktok"}
```

El puente responde:

```json
{"type":"hello","message":"Puente Simplify Chat TikTok listo."}
{"type":"status","status":"connecting","message":"Conectando a TikTok @usuario..."}
{"type":"status","status":"connected","message":"TikTok @usuario conectado."}
{"type":"chat","platform":"tiktok","user":"usuario","displayName":"Nombre","message":"hola"}
{"type":"error","message":"..."}
```

Estados posibles: `connecting`, `connected`, `offline`, `disconnected`, `error`.

## Limitaciones

- Es un proyecto no oficial basado en `tiktok-live-connector`; TikTok puede cambiar el protocolo.
- Solo lee el chat; no envia mensajes.
- No necesita tu cuenta ni cookies para leer un directo publico.
