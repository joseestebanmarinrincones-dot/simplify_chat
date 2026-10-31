import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { WebSocketServer } from "ws";
import { TikTokLiveConnection } from "tiktok-live-connector";

const here = path.dirname(fileURLToPath(import.meta.url));
const configPath = path.join(here, "config.json");

let fileConfig = {};
try {
  fileConfig = JSON.parse(readFileSync(configPath, "utf8"));
} catch (error) {
  console.warn("No se pudo leer config.json, usando valores por defecto.");
}

const port = Number(process.env.STREAMCHAT_PORT || fileConfig.port || 8787);
// La libreria usa el servicio de firma de Euler Stream. La clave es opcional:
// sin clave funciona con el limite gratuito de la comunidad.
const signApiKey = process.env.TIKTOK_SIGN_API_KEY || fileConfig.signApiKey || undefined;

const server = new WebSocketServer({ host: "127.0.0.1", port });
const clients = new Set();

let connection = null;
let currentChannel = null;
let retryTimer = null;
let retryCount = 0;

function broadcast(payload) {
  const data = JSON.stringify(payload);
  for (const client of clients) {
    if (client.readyState === 1) {
      client.send(data);
    }
  }
}

function sendStatus(status, message) {
  console.log(`[${status}] ${message}`);
  broadcast({ type: "status", status, message });
}

function sendError(message) {
  console.error(`[error] ${message}`);
  broadcast({ type: "error", message });
}

function formatError(error) {
  if (!error) return "error desconocido";
  if (typeof error === "string") return error;
  if (error.message) return error.message;
  try {
    const json = JSON.stringify(error);
    return json === "{}" ? String(error) : json;
  } catch {
    return String(error);
  }
}

function normalizeChannel(raw) {
  let value = String(raw ?? "").trim();
  if (!value) return "";
  value = value.replace(/^https?:\/\//i, "");
  if (/^(www\.)?tiktok\.com\//i.test(value)) {
    value = value.slice(value.indexOf("/") + 1);
  }
  value = value.replace(/[?#].*$/, "");
  if (value.startsWith("@")) value = value.slice(1);
  return value.split("/")[0].trim();
}

function clearRetry() {
  if (retryTimer) {
    clearTimeout(retryTimer);
    retryTimer = null;
  }
}

async function leave(silent = false) {
  clearRetry();
  const old = connection;
  connection = null;
  currentChannel = null;
  if (old) {
    try {
      await old.disconnect();
    } catch (error) {
      if (!silent) console.warn("Error cerrando la conexion anterior:", error?.message ?? error);
    }
  }
}

function isOfflineError(error) {
  const text = `${error?.name ?? ""} ${error?.message ?? ""} ${formatError(error)}`.toLowerCase();
  return text.includes("offline") || text.includes("not live") || text.includes("not online")
      || text.includes("isn't online") || text.includes("isn’t online")
      || text.includes("user is not") || text.includes("isn't online");
}

function scheduleRetry(channel) {
  if (retryCount >= 3 || !channel || clients.size === 0) return;
  retryCount += 1;
  const delay = 15_000 * retryCount;
  sendStatus("connecting", `Reintentando TikTok @${channel} en ${Math.round(delay / 1000)}s...`);
  clearRetry();
  retryTimer = setTimeout(() => {
    retryTimer = null;
    if (currentChannel === channel) {
      join(channel, true);
    }
  }, delay);
}

async function join(rawChannel, isRetry = false) {
  const channel = normalizeChannel(rawChannel);
  if (!channel) {
    sendError("El canal de TikTok esta vacio.");
    return;
  }

  if (currentChannel === channel && connection?.isConnected) {
    sendStatus("connected", `TikTok @${channel} ya esta conectado.`);
    return;
  }

  await leave();
  if (!isRetry) retryCount = 0;
  currentChannel = channel;
  sendStatus("connecting", `Conectando a TikTok @${channel}...`);

  const live = new TikTokLiveConnection(channel, {
    signApiKey,
    processInitialData: false,
    fetchRoomInfoOnConnect: true,
  });
  connection = live;

  live.on("chat", (data) => {
    const user = data?.user?.uniqueId ?? data?.user?.userId ?? "tiktok";
    const displayName = data?.user?.nickname || user;
    const message = data?.comment ?? "";
    if (!message) return;
    broadcast({
      type: "chat",
      platform: "tiktok",
      user,
      displayName,
      message,
    });
  });

  live.on("disconnected", () => {
    if (currentChannel !== channel) return;
    sendStatus("disconnected", `TikTok @${channel} se desconecto.`);
    scheduleRetry(channel);
  });

  live.on("streamEnd", () => {
    if (currentChannel !== channel) return;
    sendStatus("offline", `TikTok @${channel} termino el directo.`);
  });

  live.on("error", (error) => {
    if (currentChannel !== channel) return;
    // El fallo real se informa desde connect() o desde el evento disconnected.
    console.warn("TikTok error:", formatError(error));
  });

  try {
    const state = await live.connect();
    retryCount = 0;
    sendStatus("connected", `TikTok @${channel} conectado (roomId ${state?.roomId ?? "?"}).`);
  } catch (error) {
    const message = formatError(error);
    if (isOfflineError(error)) {
      sendStatus("offline", `TikTok @${channel} no esta en directo.`);
      return;
    }
    sendError(`No se pudo conectar a TikTok @${channel}: ${message}`);
    scheduleRetry(channel);
  }
}

server.on("listening", () => {
  console.log("==============================================");
  console.log(" Simplify Chat - puente de TikTok");
  console.log(` Escuchando en ws://127.0.0.1:${port}`);
  console.log(" Deja esta ventana abierta mientras juegas.");
  console.log("==============================================");
});

server.on("error", (error) => {
  if (error?.code === "EADDRINUSE") {
    console.error(`El puerto ${port} ya esta en uso. Cambia "port" en config.json.`);
  } else {
    console.error("Error del servidor WebSocket:", error);
  }
});

server.on("connection", (socket, request) => {
  clients.add(socket);
  console.log(`Minecraft conectado desde ${request.socket.remoteAddress}`);
  socket.send(JSON.stringify({ type: "hello", message: "Puente Simplify Chat TikTok listo." }));

  if (currentChannel) {
    socket.send(JSON.stringify({
      type: "status",
      status: connection?.isConnected ? "connected" : "connecting",
      message: `TikTok @${currentChannel}`,
    }));
  }

  socket.on("message", (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      return;
    }
    if (message.type === "join") {
      join(message.channel).catch((error) => sendError(String(error?.message ?? error)));
    } else if (message.type === "ping") {
      socket.send(JSON.stringify({ type: "pong" }));
    }
  });

  socket.on("close", () => {
    clients.delete(socket);
    console.log("Minecraft desconectado del puente.");
    if (clients.size === 0) {
      clearRetry();
    }
  });

  socket.on("error", () => {
    clients.delete(socket);
  });
});

async function shutdown() {
  console.log("\nCerrando puente...");
  await leave(true);
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1500);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
