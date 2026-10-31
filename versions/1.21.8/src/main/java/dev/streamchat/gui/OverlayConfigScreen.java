package dev.streamchat.gui;

import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.chat.MessageFormatter;
import dev.streamchat.emote.EmoteManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Panel de ajustes del overlay. Se abre con F7 (rebindable en Controles).
 * Tiene tres pestanas para que quepa en pantallas pequenas y aplica los
 * cambios en vivo sobre config/streamchat-client.toml.
 */
public final class OverlayConfigScreen extends Screen {
    private static final String[] COLOR_TARGETS = {
            "twitch", "kick", "tiktok", "message", "system", "error", "background", "separator"
    };
    private static final String[] COLOR_LABELS = {
            "Twitch", "Kick", "TikTok", "Mensajes", "Avisos", "Errores", "Fondo", "Separadores"
    };

    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int ACCENT = 0xFF25F4EE;
    private static final int OK = 0xFF55FF55;
    private static final int ERROR = 0xFFFF5555;

    private final List<Row> rows = new ArrayList<>();
    private int page = 0;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentW;

    private int colorIndex = 0;
    private Button colorTargetButton;
    private EditBox colorBox;
    private String colorStatus = "";
    private int colorStatusColor = MUTED;

    public OverlayConfigScreen() {
        super(Component.literal("Simplify Chat - Apariencia"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.rows.clear();

        this.panelW = Math.min(620, this.width - 16);
        this.panelH = Math.min(300, this.height - 16);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.contentX = this.panelX + 16;
        this.contentW = this.panelW - 32;

        int tabY = this.panelY + 31;
        int tabGap = 4;
        int tabWidth = (this.contentW - tabGap * 2) / 3;
        addButton(tabLabel("Posicion", 0), this.contentX, tabY, tabWidth, button -> setPage(0));
        addButton(tabLabel("Estilo", 1), this.contentX + tabWidth + tabGap, tabY, tabWidth, button -> setPage(1));
        addButton(tabLabel("Texto", 2), this.contentX + (tabWidth + tabGap) * 2, tabY, tabWidth, button -> setPage(2));

        int contentTop = this.panelY + 56;
        switch (this.page) {
            case 0 -> buildPositionPage(contentTop);
            case 1 -> buildStylePage(contentTop);
            default -> buildTextPage(contentTop);
        }

        int buttonY = this.panelY + this.panelH - 26;
        addButton("Guardar y cerrar", this.panelX + this.panelW / 2 - 170, buttonY, 160, button -> this.onClose());
        addButton("Cerrar", this.panelX + this.panelW / 2 + 10, buttonY, 120, button -> this.onClose());
    }

    private void buildPositionPage(int top) {
        int buttonWidth = (this.contentW - 6) / 2;
        addButton("Arriba izq.", this.contentX, top, buttonWidth, button -> setPosition("top_left"));
        addButton("Arriba der.", this.contentX + buttonWidth + 6, top, buttonWidth, button -> setPosition("top_right"));
        addButton("Abajo izq.", this.contentX, top + 24, buttonWidth, button -> setPosition("bottom_left"));
        addButton("Abajo der.", this.contentX + buttonWidth + 6, top + 24, buttonWidth, button -> setPosition("bottom_right"));

        this.rows.add(new Row("Posicion actual: " + ChatConfig.overlayAnchor, null, this.contentX, top + 52, this.contentW));

        addValueRow("Margen X", this.contentX, top + 76, this.contentW,
                () -> String.valueOf(ChatConfig.overlayMarginX),
                () -> ChatConfig.setMargins(ChatConfig.overlayMarginX - 4, ChatConfig.overlayMarginY),
                () -> ChatConfig.setMargins(ChatConfig.overlayMarginX + 4, ChatConfig.overlayMarginY));
        addValueRow("Margen Y", this.contentX, top + 100, this.contentW,
                () -> String.valueOf(ChatConfig.overlayMarginY),
                () -> ChatConfig.setMargins(ChatConfig.overlayMarginX, ChatConfig.overlayMarginY - 4),
                () -> ChatConfig.setMargins(ChatConfig.overlayMarginX, ChatConfig.overlayMarginY + 4));
    }

    private void buildStylePage(int top) {
        addValueRow("Escala texto", this.contentX, top, this.contentW,
                () -> String.format(Locale.ROOT, "%.1f", ChatConfig.overlayScale),
                () -> ChatConfig.setOverlayScale(ChatConfig.overlayScale - 0.1D),
                () -> ChatConfig.setOverlayScale(ChatConfig.overlayScale + 0.1D));
        addValueRow("Ancho", this.contentX, top + 24, this.contentW,
                () -> String.valueOf(ChatConfig.overlayWidth),
                () -> ChatConfig.setOverlayWidth(ChatConfig.overlayWidth - 20),
                () -> ChatConfig.setOverlayWidth(ChatConfig.overlayWidth + 20));
        addValueRow("Opacidad texto", this.contentX, top + 48, this.contentW,
                () -> percent(ChatConfig.overlayTextOpacity),
                () -> ChatConfig.setOverlayTextOpacity(ChatConfig.overlayTextOpacity - 0.05D),
                () -> ChatConfig.setOverlayTextOpacity(ChatConfig.overlayTextOpacity + 0.05D));
        addValueRow("Opacidad fondo", this.contentX, top + 72, this.contentW,
                () -> percent(ChatConfig.overlayBackground),
                () -> ChatConfig.setOverlayBackgroundOpacity(ChatConfig.overlayBackground - 0.05D),
                () -> ChatConfig.setOverlayBackgroundOpacity(ChatConfig.overlayBackground + 0.05D));

        int colorY = top + 100;
        this.colorTargetButton = Button.builder(colorTargetLabel(), button -> cycleColor())
                .bounds(this.contentX, colorY, this.contentW / 2 - 2, 20)
                .build();
        addRenderableWidget(this.colorTargetButton);

        int hexX = this.contentX + this.contentW / 2 + 2;
        this.colorBox = new EditBox(this.font, hexX, colorY, this.contentW - this.contentW / 2 - 58, 20,
                Component.literal("#RRGGBB"));
        this.colorBox.setMaxLength(7);
        this.colorBox.setValue(currentColorHex());
        addRenderableWidget(this.colorBox);

        addButton("Aplicar", this.contentX + this.contentW - 52, colorY, 52, button -> applyColor());

        addValueRow("Tamano de emote", this.contentX, top + 132, this.contentW,
                () -> String.valueOf(ChatConfig.emotesSize),
                () -> ChatConfig.setEmotesSize(ChatConfig.emotesSize - 2),
                () -> ChatConfig.setEmotesSize(ChatConfig.emotesSize + 2));

        int toggleWidth = this.contentW / 2 - 3;
        addButton(ChatConfig.emotesEnabled ? "Emotes: SI" : "Emotes: NO",
                this.contentX, top + 156, toggleWidth, button -> {
                    ChatConfig.setEmotesEnabled(!ChatConfig.emotesEnabled);
                    EmoteManager.reload();
                    this.rebuildWidgets();
                });
        addButton(ChatConfig.emotesAnimated ? "Animados: SI" : "Animados: NO",
                this.contentX + this.contentW - toggleWidth, top + 156, toggleWidth, button -> {
                    ChatConfig.setEmotesAnimated(!ChatConfig.emotesAnimated);
                    EmoteManager.reload();
                    this.rebuildWidgets();
                });
    }

    private void buildTextPage(int top) {
        addTextField("Etiqueta de plataforma", this.contentX, top + 12, this.contentW,
                ChatConfig.overlayPlatformTagFormat, ChatConfig::setPlatformTagFormat);
        addTextField("Formato del mensaje", this.contentX, top + 44, this.contentW,
                ChatConfig.overlayMessageFormat, ChatConfig::setMessageFormat);
        addTextField("Prefijo de avisos", this.contentX, top + 76, this.contentW,
                ChatConfig.overlaySystemPrefix, ChatConfig::setSystemPrefix);
        this.rows.add(new Row("VISTA PREVIA", null, this.contentX, top + 106, this.contentW));
    }

    private void setPage(int page) {
        if (this.page == page) {
            return;
        }
        this.page = page;
        this.rebuildWidgets();
    }

    private void setPosition(String anchor) {
        ChatConfig.setAnchor(anchor);
        this.rebuildWidgets();
    }

    private String tabLabel(String label, int index) {
        return this.page == index ? "> " + label + " <" : label;
    }

    private void addValueRow(String label, int x, int y, int width, Supplier<String> value,
                             Runnable minus, Runnable plus) {
        addButton("-", x + width - 44, y - 4, 20, button -> minus.run());
        addButton("+", x + width - 22, y - 4, 20, button -> plus.run());
        this.rows.add(new Row(label, value, x, y, width));
    }

    private void addTextField(String label, int x, int y, int width, String value, Consumer<String> responder) {
        this.rows.add(new Row(label, null, x, y - 11, width));
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.literal(label));
        box.setMaxLength(160);
        box.setValue(value);
        box.setResponder(responder);
        addRenderableWidget(box);
    }

    private Button addButton(String label, int x, int y, int width, Button.OnPress onPress) {
        Button button = Button.builder(Component.literal(label), onPress)
                .bounds(x, y, width, 20)
                .build();
        addRenderableWidget(button);
        return button;
    }

    private Component colorTargetLabel() {
        return Component.literal("Color: " + COLOR_LABELS[this.colorIndex]);
    }

    private void cycleColor() {
        this.colorIndex = (this.colorIndex + 1) % COLOR_TARGETS.length;
        this.colorTargetButton.setMessage(colorTargetLabel());
        this.colorBox.setValue(currentColorHex());
        this.colorStatus = "";
    }

    private void applyColor() {
        if (ChatConfig.setColor(COLOR_TARGETS[this.colorIndex], this.colorBox.getValue())) {
            this.colorStatus = "Color aplicado";
            this.colorStatusColor = OK;
        } else {
            this.colorStatus = "Hex invalido (usa #RRGGBB)";
            this.colorStatusColor = ERROR;
        }
    }

    private String currentColorHex() {
        int rgb = switch (COLOR_TARGETS[this.colorIndex]) {
            case "twitch" -> ChatConfig.overlayTwitchColor;
            case "kick" -> ChatConfig.overlayKickColor;
            case "tiktok" -> ChatConfig.overlayTikTokColor;
            case "message" -> ChatConfig.overlayMessageColor;
            case "system" -> ChatConfig.overlaySystemColor;
            case "error" -> ChatConfig.overlayErrorColor;
            case "background" -> ChatConfig.overlayBackgroundColor;
            default -> ChatConfig.overlaySeparatorColor;
        };
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    private static String percent(double value) {
        return Math.round(value * 100.0D) + "%";
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dejar ver el juego detras del panel.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, 0xF0101010);
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 3, ACCENT);

        graphics.drawString(this.font, this.title, this.panelX + 16, this.panelY + 8, WHITE, true);
        graphics.drawString(this.font, Component.literal("F7: abrir/cerrar | los cambios se guardan en vivo"),
                this.panelX + 16, this.panelY + 21, MUTED, false);

        for (Row row : this.rows) {
            graphics.drawString(this.font, Component.literal(row.label), row.x, row.y, MUTED, false);
            if (row.value != null) {
                String value = row.value.get();
                int valueX = row.x + row.w - 110;
                graphics.drawString(this.font, Component.literal(value), valueX, row.y, WHITE, false);
            }
        }

        if (this.page == 1 && !this.colorStatus.isEmpty()) {
            graphics.drawString(this.font, Component.literal(this.colorStatus),
                    this.contentX, this.panelY + 56 + 124, this.colorStatusColor, false);
        }

        if (this.page == 2) {
            int previewY = this.panelY + 56 + 122;
            graphics.fill(this.contentX, previewY - 3, this.contentX + this.contentW, previewY - 2, 0x40FFFFFF);
            Component preview = MessageFormatter.format(
                    new ChatMessage(ChatPlatform.TWITCH, "StreamChat", "Mensaje de prueba", "#55FF55"));
            int previewAlpha = (int) (Math.max(0.05D, Math.min(1.0D, ChatConfig.overlayTextOpacity)) * 255.0D);
            graphics.drawString(this.font, preview, this.contentX, previewY + 4,
                    (previewAlpha << 24) | 0xFFFFFF, true);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        try {
            ChatConfig.save();
        } catch (IllegalStateException ignored) {
            // Los setters ya guardan cada cambio.
        }
        super.onClose();
    }

    private record Row(String label, Supplier<String> value, int x, int y, int w) {
    }
}
