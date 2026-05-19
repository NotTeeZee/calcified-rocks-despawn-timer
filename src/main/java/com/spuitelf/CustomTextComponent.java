package com.spuitelf;

import lombok.Setter;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.RenderableEntity;

import java.awt.*;

public class CustomTextComponent implements RenderableEntity {
    private final String text;
    private final Point position;
    @Setter
    private Color color = Color.BLACK;
    @Setter
    private boolean emphasize = false;
    @Setter
    private boolean centered = true;

    private static Font emphasizeFont = FontManager.getRunescapeBoldFont().deriveFont(16f);
    private static Font standardFont = FontManager.getRunescapeSmallFont().deriveFont(16f);

    static void updateFontSizes(int normal, int emphasized) {
        standardFont = FontManager.getRunescapeSmallFont().deriveFont((float) normal);
        // Bold font is naturally much larger than the small font, so scale down the size to keep them in-line
        emphasizeFont = FontManager.getRunescapeBoldFont().deriveFont((float) Math.round(emphasized * 0.65f));
    }

    public CustomTextComponent(String text, Point position) {
        this.text = text;
        this.position = position;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Font originalFont = graphics.getFont();
        graphics.setFont(emphasize ? emphasizeFont : standardFont);

        final FontMetrics fontMetrics = graphics.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(text);
        int textHeight = fontMetrics.getAscent();

        if (centered) {
            position.setLocation(position.getX() - textWidth / 2d, position.getY() + textHeight / 2d);
        }

        graphics.setColor(Color.BLACK);
        if (emphasize) {
            graphics.drawString(text, position.x, position.y - 1);
            graphics.drawString(text, position.x - 1, position.y);
        }
        graphics.drawString(text, position.x, position.y + 1);
        graphics.drawString(text, position.x + 1, position.y);

        graphics.setColor(color);
        graphics.drawString(text, position.x, position.y);

        if (originalFont != null) {
            graphics.setFont(originalFont);
        }

        return new Dimension(textWidth, textHeight);
    }
}

