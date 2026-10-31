package dev.streamchat;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.streamchat.chat.ChatManager;
import dev.streamchat.chat.OverlayChat;
import dev.streamchat.emote.EmoteManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

public final class StreamChatCommands {
    private StreamChatCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("streamchat")
                        .executes(context -> help(context.getSource()))
                        .then(Commands.literal("help")
                                .executes(context -> help(context.getSource())))
                        .then(Commands.literal("connect")
                                .executes(context -> {
                                    ChatManager.connectConfigured(true);
                                    return 1;
                                })
                                .then(Commands.argument("platform", StringArgumentType.word())
                                        .then(Commands.argument("channel", StringArgumentType.greedyString())
                                                .executes(StreamChatCommands::connect))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("platform", StringArgumentType.word())
                                        .then(Commands.argument("channel", StringArgumentType.greedyString())
                                                .executes(StreamChatCommands::addSource))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("platform", StringArgumentType.word())
                                        .executes(context -> removeSource(context, ""))
                                        .then(Commands.argument("channel", StringArgumentType.greedyString())
                                                .executes(context -> removeSource(context,
                                                        StringArgumentType.getString(context, "channel"))))))
                        .then(Commands.literal("sources")
                                .executes(context -> {
                                    ok(context.getSource(), ChatManager.statusText());
                                    return 1;
                                }))
                        .then(Commands.literal("disconnect")
                                .executes(context -> {
                                    ChatManager.disconnect(true);
                                    return 1;
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    ok(context.getSource(), ChatManager.statusText());
                                    return 1;
                                }))
                        .then(Commands.literal("platform")
                                .then(Commands.argument("platform", StringArgumentType.word())
                                        .executes(StreamChatCommands::setPlatform)))
                        .then(Commands.literal("channel")
                                .then(Commands.argument("channel", StringArgumentType.greedyString())
                                        .executes(StreamChatCommands::setChannel)))
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    ChatConfig.load();
                                    EmoteManager.reload();
                                    ok(context.getSource(), "Configuracion recargada.");
                                    return 1;
                                }))
                        .then(Commands.literal("emotes")
                                .executes(context -> {
                                    ok(context.getSource(), "Emotes: " + (ChatConfig.emotesEnabled ? "activados" : "desactivados")
                                            + " | Animados: " + (ChatConfig.emotesAnimated ? "si" : "no")
                                            + " | Tamano: " + ChatConfig.emotesSize
                                            + " | Proveedores: " + String.join(", ", ChatConfig.emoteProviders));
                                    return 1;
                                })
                                .then(Commands.literal("on")
                                        .executes(context -> setEmotes(context.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> setEmotes(context.getSource(), false)))
                                .then(Commands.literal("animated")
                                        .then(Commands.literal("on")
                                                .executes(context -> setEmotesAnimated(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setEmotesAnimated(context.getSource(), false))))
                                .then(Commands.literal("size")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(8, 28))
                                                .executes(context -> {
                                                    int value = IntegerArgumentType.getInteger(context, "value");
                                                    ChatConfig.setEmotesSize(value);
                                                    ok(context.getSource(), "Tamano de emote guardado: " + ChatConfig.emotesSize);
                                                    return 1;
                                                })))
                                .then(Commands.literal("reload")
                                        .executes(context -> {
                                            EmoteManager.reload();
                                            ok(context.getSource(), "Recargando emotes...");
                                            return 1;
                                        })))
                        .then(Commands.literal("test")
                                .executes(context -> {
                                    ChatManager.test();
                                    ok(context.getSource(), "Mensaje de prueba enviado al overlay.");
                                    return 1;
                                }))
                        .then(Commands.literal("panel")
                                .executes(context -> {
                                    StreamChatKeys.openPanel();
                                    return 1;
                                }))
                        .then(Commands.literal("overlay")
                                .executes(context -> setOverlay(context.getSource(), !ChatConfig.overlayEnabled))
                                .then(Commands.literal("on")
                                        .executes(context -> setOverlay(context.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> setOverlay(context.getSource(), false)))
                                .then(Commands.literal("clear")
                                        .executes(context -> {
                                            OverlayChat.clear();
                                            ok(context.getSource(), "Overlay limpiado.");
                                            return 1;
                                        }))
                                .then(Commands.literal("position")
                                        .then(Commands.argument("anchor", StringArgumentType.word())
                                                .executes(StreamChatCommands::setOverlayPosition)))
                                .then(Commands.literal("margin")
                                        .then(Commands.argument("x", IntegerArgumentType.integer(0, 400))
                                                .then(Commands.argument("y", IntegerArgumentType.integer(0, 400))
                                                        .executes(StreamChatCommands::setOverlayMargin))))
                                .then(Commands.literal("scale")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5D, 2.0D))
                                                .executes(StreamChatCommands::setOverlayScale)))
                                .then(Commands.literal("width")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(120, 800))
                                                .executes(StreamChatCommands::setOverlayWidth)))
                                .then(Commands.literal("opacity")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.05D, 1.0D))
                                                .executes(StreamChatCommands::setOverlayOpacity)))
                                .then(Commands.literal("bgopacity")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 0.8D))
                                                .executes(StreamChatCommands::setOverlayBackgroundOpacity)))
                                .then(Commands.literal("color")
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .then(Commands.argument("hex", StringArgumentType.word())
                                                        .executes(StreamChatCommands::setOverlayColor))))
                                .then(Commands.literal("format")
                                        .then(Commands.argument("format", StringArgumentType.greedyString())
                                                .executes(StreamChatCommands::setOverlayFormat)))
                                .then(Commands.literal("tag")
                                        .then(Commands.argument("format", StringArgumentType.greedyString())
                                                .executes(StreamChatCommands::setOverlayTag)))
                                .then(Commands.literal("prefix")
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(StreamChatCommands::setOverlayPrefix))))
                        .then(Commands.literal("bridge")
                                .executes(context -> bridge(context.getSource())))
        );
    }

    private static int connect(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String platformId = StringArgumentType.getString(context, "platform");
        ChatPlatform platform = ChatPlatform.byId(platformId);
        if (platform == null) {
            fail(source, "Plataforma desconocida. Usa twitch, kick o tiktok.");
            return 0;
        }

        String channel = ChannelNames.normalize(platform, StringArgumentType.getString(context, "channel"));
        if (channel.isEmpty()) {
            fail(source, "El canal esta vacio.");
            return 0;
        }

        ChatManager.connect(platform, channel, true);
        ok(source, "Conectando a " + platform.displayName() + ": "
                + (platform == ChatPlatform.TIKTOK ? "@" + channel : channel));
        return 1;
    }

    private static int addSource(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ChatPlatform platform = ChatPlatform.byId(StringArgumentType.getString(context, "platform"));
        if (platform == null) {
            fail(source, "Plataforma desconocida. Usa twitch, kick o tiktok.");
            return 0;
        }
        String channel = ChannelNames.normalize(platform, StringArgumentType.getString(context, "channel"));
        if (channel.isEmpty()) {
            fail(source, "El canal esta vacio.");
            return 0;
        }
        ChatManager.connect(platform, channel, true);
        ok(source, "Chat agregado: " + platform.displayName() + " " + channel);
        return 1;
    }

    private static int removeSource(CommandContext<CommandSourceStack> context, String channel) {
        CommandSourceStack source = context.getSource();
        ChatPlatform platform = ChatPlatform.byId(StringArgumentType.getString(context, "platform"));
        if (platform == null) {
            fail(source, "Plataforma desconocida. Usa twitch, kick o tiktok.");
            return 0;
        }
        if (!ChatConfig.removeSource(platform, channel)) {
            fail(source, "No habia ningun chat configurado para " + platform.displayName() + ".");
            return 0;
        }
        ChatManager.connectConfigured(false);
        ok(source, "Chat eliminado. Quedan " + ChatConfig.effectiveSources().size() + ".");
        return 1;
    }

    private static int setEmotes(CommandSourceStack source, boolean enabled) {
        ChatConfig.setEmotesEnabled(enabled);
        EmoteManager.reload();
        ok(source, enabled ? "Emotes activados." : "Emotes desactivados.");
        return 1;
    }

    private static int setEmotesAnimated(CommandSourceStack source, boolean enabled) {
        ChatConfig.setEmotesAnimated(enabled);
        EmoteManager.reload();
        ok(source, enabled ? "Emotes animados activados." : "Emotes animados desactivados.");
        return 1;
    }

    private static int setPlatform(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String platformId = StringArgumentType.getString(context, "platform");
        ChatPlatform platform = ChatPlatform.byId(platformId);
        if (platform == null) {
            fail(source, "Plataforma desconocida. Usa twitch, kick o tiktok.");
            return 0;
        }
        ChatConfig.setPlatform(platform.id());
        ok(source, "Plataforma guardada: " + platform.displayName() + ". Usa /streamchat connect para aplicarla.");
        return 1;
    }

    private static int setChannel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ChatPlatform platform = ChatPlatform.byIdOrDefault(ChatConfig.platform);
        String channel = ChannelNames.normalize(platform, StringArgumentType.getString(context, "channel"));
        if (channel.isEmpty()) {
            fail(source, "El canal esta vacio.");
            return 0;
        }
        ChatConfig.setChannel(channel);
        ok(source, "Canal guardado: " + (platform == ChatPlatform.TIKTOK ? "@" + channel : channel));
        return 1;
    }

    private static int setOverlayPosition(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String anchor = StringArgumentType.getString(context, "anchor");
        if (!ChatConfig.setAnchor(anchor)) {
            fail(source, "Posicion invalida. Usa top_left, top_right, bottom_left o bottom_right.");
            return 0;
        }
        ok(source, "Posicion del overlay guardada: " + ChatConfig.overlayAnchor);
        return 1;
    }

    private static int setOverlayMargin(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        ChatConfig.setMargins(x, y);
        ok(source, "Margen del overlay guardado: " + x + ", " + y);
        return 1;
    }

    private static int setOverlayScale(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double value = DoubleArgumentType.getDouble(context, "value");
        ChatConfig.setOverlayScale(value);
        ok(source, "Escala del overlay guardada: " + ChatConfig.overlayScale);
        return 1;
    }

    private static int setOverlayWidth(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int value = IntegerArgumentType.getInteger(context, "value");
        ChatConfig.setOverlayWidth(value);
        ok(source, "Ancho del texto guardado: " + ChatConfig.overlayWidth);
        return 1;
    }

    private static int setOverlayOpacity(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double value = DoubleArgumentType.getDouble(context, "value");
        ChatConfig.setOverlayTextOpacity(value);
        ok(source, "Opacidad del texto guardada: " + ChatConfig.overlayTextOpacity);
        return 1;
    }

    private static int setOverlayBackgroundOpacity(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double value = DoubleArgumentType.getDouble(context, "value");
        ChatConfig.setOverlayBackgroundOpacity(value);
        ok(source, "Opacidad del fondo guardada: " + ChatConfig.overlayBackground);
        return 1;
    }

    private static int setOverlayColor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String target = StringArgumentType.getString(context, "target");
        String hex = StringArgumentType.getString(context, "hex");
        if (!ChatConfig.setColor(target, hex)) {
            fail(source, "Color o destino invalido. Destinos: twitch, kick, tiktok, message, system, error, background, separator.");
            return 0;
        }
        ok(source, "Color de " + target + " guardado: " + hex);
        return 1;
    }

    private static int setOverlayFormat(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String format = StringArgumentType.getString(context, "format");
        ChatConfig.setMessageFormat(format);
        ok(source, "Formato guardado. Placeholders: {tag}, {platform}, {name}, {message}.");
        return 1;
    }

    private static int setOverlayTag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String format = StringArgumentType.getString(context, "format");
        ChatConfig.setPlatformTagFormat(format);
        ok(source, "Etiqueta de plataforma guardada. Usa {platform} para el nombre.");
        return 1;
    }

    private static int setOverlayPrefix(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String text = StringArgumentType.getString(context, "text");
        ChatConfig.setSystemPrefix(text);
        ok(source, "Prefijo de avisos guardado: " + text);
        return 1;
    }

    private static int bridge(CommandSourceStack source) {
        ok(source, "TikTok funciona con un puente local (Node.js):");
        ok(source, "1. Ejecuta tiktok-bridge/iniciar-puente.bat (la primera vez instala dependencias).");
        ok(source, "2. Dentro del juego: /streamchat connect tiktok TU_USUARIO");
        ok(source, "3. El puente escucha en " + ChatConfig.tiktokBridgeUrl + " (se cambia en el TOML).");
        return 1;
    }

    private static int setOverlay(CommandSourceStack source, boolean enabled) {
        ChatConfig.setOverlayEnabled(enabled);
        if (enabled) {
            ok(source, "Overlay activado. Ajusta posicion y tamano en [overlay] del TOML.");
        } else {
            OverlayChat.clear();
            ok(source, "Overlay desactivado. Los mensajes iran al chat normal de Minecraft.");
        }
        return 1;
    }

    private static int help(CommandSourceStack source) {
        ok(source, "Simplify Chat 1.1.0 - comandos:");
        line(source, "/streamchat add <twitch|kick|tiktok> <canal>", "Agrega un chat sin quitar los demas.");
        line(source, "/streamchat remove <plataforma> [canal]", "Quita un chat configurado.");
        line(source, "/streamchat connect [plataforma] [canal]", "Conecta un chat o todos los configurados.");
        line(source, "/streamchat sources | status", "Lista los chats y su estado.");
        line(source, "/streamchat disconnect", "Cierra todas las conexiones.");
        line(source, "/streamchat emotes on|off|animated on|off|size <8-28>|reload", "Ajustes de emotes.");
        line(source, "/streamchat platform <twitch|kick|tiktok>", "Cambia la plataforma guardada antigua.");
        line(source, "/streamchat channel <canal>", "Cambia el canal guardado antiguo.");
        line(source, "/streamchat test", "Muestra un mensaje de prueba.");
        line(source, "/streamchat panel", "Abre el panel de apariencia (tambien F7).");
        line(source, "/streamchat overlay [on|off|clear]", "Activa, desactiva o limpia el overlay.");
        line(source, "/streamchat overlay position <top_left|top_right|bottom_left|bottom_right>", "Esquina del overlay.");
        line(source, "/streamchat overlay margin <x> <y>", "Margen respecto a la esquina.");
        line(source, "/streamchat overlay scale <0.5-2.0> | width <120-800>", "Tamano del texto y ancho.");
        line(source, "/streamchat overlay opacity <0.05-1.0>", "Opacidad del texto.");
        line(source, "/streamchat overlay bgopacity <0.0-0.8>", "Opacidad del fondo.");
        line(source, "/streamchat overlay color <destino> <#RRGGBB>", "Colores de plataformas, texto, fondo, etc.");
        line(source, "/streamchat overlay format <texto>", "Formato con {tag}, {platform}, {name}, {message}.");
        line(source, "/streamchat reload", "Recarga config/streamchat-client.toml.");
        line(source, "/streamchat bridge", "Ayuda con el puente de TikTok.");
        return 1;
    }

    private static void line(CommandSourceStack source, String command, String description) {
        source.sendSuccess(() -> Component.literal(command)
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY)), false);
    }

    private static void ok(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.WHITE), false);
    }

    private static void fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
