package dev.streamchat;

/**
 * Un mensaje ya normalizado, listo para pintarse en el chat de Minecraft.
 *
 * @param platform  plataforma de origen
 * @param user      nombre visible del usuario
 * @param message   texto del mensaje
 * @param colorHex  color del usuario en formato #RRGGBB, o null
 */
public record ChatMessage(ChatPlatform platform, String user, String message, String colorHex) {
}
