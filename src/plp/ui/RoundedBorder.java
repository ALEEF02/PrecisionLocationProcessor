package plp.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.border.Border;

public class RoundedBorder implements Border {
    private int top;
    private int left;
    private int bottom;
    private int right;
    private Color backgroundColor;
    private Color borderColor;

    public RoundedBorder(Color backgroundColor, Color borderColor, int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(this.top, this.left, this.bottom, this.right);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Draw the rounded rectangle background
        g2d.setColor(this.backgroundColor);
        g2d.fillRoundRect(x, y, width-1, height-1, 8, 8);
        
        // Draw the border
        g2d.setColor(this.borderColor);
        g2d.drawRoundRect(x, y, width-1, height-1, 8, 8);

        g2d.dispose();
    }
}