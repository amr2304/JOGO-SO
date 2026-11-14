package br.com.policialadrao.client;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

final class UiStyle {
    static final Color BACKGROUND = new Color(15, 19, 34);
    static final Color PANEL = new Color(25, 31, 51);
    static final Color PANEL_LIGHT = new Color(39, 49, 75);
    static final Color GOLD = new Color(255, 202, 112);
    static final Color TEXT = new Color(226, 234, 246);
    static final Color MUTED = new Color(151, 168, 193);
    static final Color BLUE = new Color(74, 104, 183);

    private UiStyle() { }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        button.setForeground(primary ? new Color(24, 26, 37) : TEXT);
        button.setBackground(primary ? GOLD : PANEL_LIGHT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? new Color(255, 226, 171) : new Color(90, 111, 146), 2),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        button.setMargin(new Insets(8, 12, 8, 12));
        button.setOpaque(true);
        return button;
    }

    static JTextField field(String initialValue) {
        JTextField field = new JTextField(initialValue);
        field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        field.setForeground(TEXT);
        field.setCaretColor(GOLD);
        field.setBackground(new Color(13, 17, 30));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(72, 87, 119), 1),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        return field;
    }

    static Font font(int size, int style) { return new Font(Font.MONOSPACED, style, size); }
}
