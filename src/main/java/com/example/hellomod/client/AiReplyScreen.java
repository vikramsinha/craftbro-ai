package com.example.hellomod.client;

import com.example.hellomod.AiReplyPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A manually dismissed, paginated reply; text never expires like chat. */
final class AiReplyScreen extends Screen {
    private final AiReplyPayload reply;
    private List<FormattedCharSequence> lines;
    private int page;
    private int perPage;
    private int left;
    private int panelWidth;
    private int top;
    private int panelHeight;
    private Button previous;
    private Button next;

    AiReplyScreen(AiReplyPayload reply) {
        super(Component.literal("CraftBro AI"));
        this.reply = reply;
    }

    @Override protected void init() {
        panelWidth = Math.min(300, Math.min(width - 24, Math.max(180, width * 3 / 5)));
        left = (width - panelWidth) / 2;
        lines = font.split(Component.literal("You asked: " + reply.question() + "\n\nAI: " + reply.answer()), panelWidth - 24);
        int maxHeight = Math.min(height - 16, Math.max(110, height * 3 / 5));
        perPage = Math.max(1, (maxHeight - 76) / 11);
        panelHeight = Math.min(lines.size(), perPage) * 11 + 76;
        top = (height - panelHeight) / 2;
        page = Math.min(page, pages() - 1);
        int buttonWidth = Math.min(72, (panelWidth - 32) / 3);
        previous = addRenderableWidget(Button.builder(Component.literal("Previous"), button -> { page--; updateButtons(); })
            .bounds(left + 10, top + panelHeight - 28, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
            .bounds((width - buttonWidth) / 2, top + panelHeight - 28, buttonWidth, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal("Next"), button -> { page++; updateButtons(); })
            .bounds(left + panelWidth - buttonWidth - 10, top + panelHeight - 28, buttonWidth, 18).build());
        updateButtons();
    }

    private int pages() { return Math.max(1, (lines.size() + perPage - 1) / perPage); }
    private void updateButtons() {
        previous.active = page > 0;
        next.active = page + 1 < pages();
        previous.visible = next.visible = pages() > 1;
    }

    // Screen.render also calls this after our text. The default blur would blur
    // the reply itself, so this screen deliberately has no background effect.
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xFF526779);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF17202B);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF);
        int start = page * perPage;
        for (int i = start; i < Math.min(lines.size(), start + perPage); i++)
            graphics.drawString(font, lines.get(i), left + 12, top + 28 + (i - start) * 11, 0xFFFFFF);
        if (pages() > 1) graphics.drawCenteredString(font, Component.literal((page + 1) + " / " + pages()), width / 2, top + panelHeight - 42, 0xB8C8D8);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return true; }
}
