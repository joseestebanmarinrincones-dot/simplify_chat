package dev.streamchat.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.chat.MessageFormatter;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private final int page;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentW;

    private int colorIndex = 0;
    private Button colorTargetButton;
    private TextFieldWidget colorBox;
    private String colorStatus = "";
    private int colorStatusColor = MUTED;

    public OverlayConfigScreen() {
        this(0);
    }

    public OverlayConfigScreen(int page) {
        super(new StringTextComponent("Simplify Chat - Apariencia"));
        this.page = Math.max(0, Math.min(2, page));
    }

    @Override
    protected void init() {
        this.buttons.clear();
        this.children.clear();
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
        if (this.page == 0) {
            buildPositionPage(contentTop);
        } else if (this.page == 1) {
            buildStylePage(contentTop);
        } else {
            buildTextPage(contentTop);
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
        this.colorTargetButton = addButton(colorTargetLabel(), this.contentX, colorY, this.contentW / 2 - 2, button -> cycleColor());
        int hexX = this.contentX + this.contentW / 2 + 2;
        this.colorBox = new TextFieldWidget(this.font, hexX, colorY, this.contentW - this.contentW / 2 - 58, 20,
                new StringTextComponent("#RRGGBB"));
        this.colorBox.setMaxLength(7);
        this.colorBox.setValue(currentColorHex());
        addWidget(this.colorBox);
        addButton("Aplicar", this.contentX + this.contentW - 52, colorY, 52, button -> applyColor());
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
        if (this.page != page) {
            this.minecraft.setScreen(new OverlayConfigScreen(page));
        }
    }

    private void setPosition(String anchor) {
        ChatConfig.setAnchor(anchor);
        this.minecraft.setScreen(new OverlayConfigScreen(this.page));
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
        TextFieldWidget box = new TextFieldWidget(this.font, x, y, width, 20, new StringTextComponent(label));
        box.setMaxLength(160);
        box.setValue(value);
        box.setResponder(responder);
        addWidget(box);
    }

    private Button addButton(String label, int x, int y, int width, Button.IPressable onPress) {
        Button button = new Button(x, y, width, 20, new StringTextComponent(label), onPress);
        addButton(button);
        return button;
    }

    private String colorTargetLabel() {
        return "Color: " + COLOR_LABELS[this.colorIndex];
    }

    private void cycleColor() {
        this.colorIndex = (this.colorIndex + 1) % COLOR_TARGETS.length;
        this.colorTargetButton.setMessage(new StringTextComponent(colorTargetLabel()));
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
        String target = COLOR_TARGETS[this.colorIndex];
        int rgb;
        if ("twitch".equals(target)) {
            rgb = ChatConfig.overlayTwitchColor;
        } else if ("kick".equals(target)) {
            rgb = ChatConfig.overlayKickColor;
        } else if ("tiktok".equals(target)) {
            rgb = ChatConfig.overlayTikTokColor;
        } else if ("message".equals(target)) {
            rgb = ChatConfig.overlayMessageColor;
        } else if ("system".equals(target)) {
            rgb = ChatConfig.overlaySystemColor;
        } else if ("error".equals(target)) {
            rgb = ChatConfig.overlayErrorColor;
        } else if ("background".equals(target)) {
            rgb = ChatConfig.overlayBackgroundColor;
        } else {
            rgb = ChatConfig.overlaySeparatorColor;
        }
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    private static String percent(double value) {
        return Math.round(value * 100.0D) + "%";
    }

    @Override
    public void renderBackground(MatrixStack matrixStack) {
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTick) {
        AbstractGui.fill(matrixStack, 0, 0, this.width, this.height, 0x99000000);
        AbstractGui.fill(matrixStack, this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, 0xF0101010);
        AbstractGui.fill(matrixStack, this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 3, ACCENT);

        this.font.drawShadow(matrixStack, this.title, this.panelX + 16, this.panelY + 8, WHITE);
        this.font.drawShadow(matrixStack, new StringTextComponent("F7: abrir/cerrar | los cambios se guardan en vivo"),
                this.panelX + 16, this.panelY + 21, MUTED);

        for (Row row : this.rows) {
            this.font.drawShadow(matrixStack, new StringTextComponent(row.label), row.x, row.y, MUTED);
            if (row.value != null) {
                String value = row.value.get();
                int valueX = row.x + row.w - 110;
                this.font.drawShadow(matrixStack, new StringTextComponent(value), valueX, row.y, WHITE);
            }
        }

        if (this.page == 1 && !this.colorStatus.isEmpty()) {
            this.font.drawShadow(matrixStack, new StringTextComponent(this.colorStatus),
                    this.contentX, this.panelY + 56 + 124, this.colorStatusColor);
        }
        if (this.page == 2) {
            int previewY = this.panelY + 56 + 122;
            AbstractGui.fill(matrixStack, this.contentX, previewY - 3, this.contentX + this.contentW, previewY - 2, 0x40FFFFFF);
            ITextComponent preview = MessageFormatter.format(
                    new ChatMessage(ChatPlatform.TWITCH, "Simplify Chat", "Mensaje de prueba", "#55FF55"));
            int previewAlpha = (int) (Math.max(0.05D, Math.min(1.0D, ChatConfig.overlayTextOpacity)) * 255.0D);
            this.font.drawShadow(matrixStack, preview, this.contentX, previewY + 4, (previewAlpha << 24) | 0xFFFFFF);
        }

        super.render(matrixStack, mouseX, mouseY, partialTick);
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
        }
        super.onClose();
    }

    private static final class Row {
        private final String label;
        private final Supplier<String> value;
        private final int x;
        private final int y;
        private final int w;

        private Row(String label, Supplier<String> value, int x, int y, int w) {
            this.label = label;
            this.value = value;
            this.x = x;
            this.y = y;
            this.w = w;
        }
    }
}
