package br.com.policialadrao.client;

import br.com.policialadrao.common.PixelAssets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public final class StartPanel extends JPanel {
    public record ConnectionInfo(String playerName, String host, int port) { }

    private final JTextField nameField = UiStyle.field("Jogador");
    private final JTextField hostField = UiStyle.field("127.0.0.1");
    private final JTextField portField = UiStyle.field("5050");
    private final JButton connectButton = UiStyle.button("CONECTAR À PARTIDA", true);
    private final JLabel status = new JLabel("Inicie o servidor e conecte dois jogadores.");
    private Consumer<ConnectionInfo> onConnect = info -> { };

    public StartPanel() {
        setLayout(new BorderLayout());
        setOpaque(true);
        add(createContent(), BorderLayout.CENTER);
        connectButton.addActionListener(event -> connect());
    }

    public void setOnConnect(Consumer<ConnectionInfo> onConnect) {
        this.onConnect = onConnect;
    }

    public void setStatus(String message, boolean isError) {
        status.setText(message);
        status.setForeground(isError ? new Color(255, 134, 127) : new Color(166, 219, 173));
        connectButton.setEnabled(true);
        connectButton.setText("CONECTAR À PARTIDA");
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new GridLayout(1, 2, 28, 0));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(52, 72, 52, 72));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("JOGO DE SISTEMAS OPERACIONAIS");
        eyebrow.setFont(UiStyle.font(12, Font.BOLD));
        eyebrow.setForeground(UiStyle.GOLD);
        JLabel title = new JLabel("POLÍCIA E");
        title.setFont(UiStyle.font(38, Font.BOLD));
        title.setForeground(Color.WHITE);
        JLabel title2 = new JLabel("LADRÃO");
        title2.setFont(UiStyle.font(38, Font.BOLD));
        title2.setForeground(UiStyle.GOLD);
        JLabel description = new JLabel("<html>Um assalto. Um cofre verdadeiro.<br>Uma cidade que não conta tudo.</html>");
        description.setFont(UiStyle.font(14, Font.PLAIN));
        description.setForeground(UiStyle.TEXT);

        left.add(Box.createVerticalGlue());
        left.add(eyebrow);
        left.add(Box.createVerticalStrut(15));
        left.add(title);
        left.add(title2);
        left.add(Box.createVerticalStrut(16));
        left.add(description);
        left.add(Box.createVerticalStrut(24));
        left.add(createForm());
        left.add(Box.createVerticalGlue());

        JPanel art = new ArtPanel();
        art.setPreferredSize(new Dimension(530, 630));
        content.add(left);
        content.add(art);
        return content;
    }

    private JPanel createForm() {
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 7));
        form.setOpaque(true);
        form.setBackground(new Color(15, 19, 34, 232));
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(78, 96, 130), 2),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        form.add(label("NOME DO JOGADOR"));
        form.add(nameField);
        form.add(label("SERVIDOR"));
        form.add(hostField);
        form.add(label("PORTA"));
        form.add(portField);
        form.add(Box.createVerticalStrut(4));
        form.add(connectButton);
        status.setFont(UiStyle.font(11, Font.PLAIN));
        status.setForeground(UiStyle.MUTED);
        form.add(status);
        return form;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiStyle.font(10, Font.BOLD));
        label.setForeground(UiStyle.MUTED);
        return label;
    }

    private void connect() {
        try {
            String host = hostField.getText().trim();
            String name = nameField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            if (host.isBlank() || name.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException();
            setStatus("Conectando ao servidor...", false);
            connectButton.setEnabled(false);
            connectButton.setText("CONECTANDO...");
            onConnect.accept(new ConnectionInfo(name, host, port));
        } catch (RuntimeException exception) {
            setStatus("Confira o nome, servidor e porta.", true);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        BufferedImage map = PixelAssets.image("mapa.png");
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double scale = Math.max(getWidth() / (double) map.getWidth(), getHeight() / (double) map.getHeight());
        int width = (int) (map.getWidth() * scale);
        int height = (int) (map.getHeight() * scale);
        g.drawImage(map, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
        g.setComposite(AlphaComposite.SrcOver.derive(0.84f));
        g.setColor(new Color(11, 14, 27));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(255, 255, 255, 12));
        for (int y = 0; y < getHeight(); y += 4) g.drawLine(0, y, getWidth(), y);
        g.dispose();
    }

    private static final class ArtPanel extends JPanel {
        private final BufferedImage map = PixelAssets.image("mapa.png");
        private final BufferedImage thief = PixelAssets.image("ladrao.png");
        private final BufferedImage guard = PixelAssets.image("guarda.png");
        private final BufferedImage alarm = PixelAssets.image("alarme_24px.png");

        private ArtPanel() { setOpaque(false); }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int inset = 20;
            g.setColor(new Color(10, 13, 26, 226));
            g.fillRect(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2);
            g.setColor(UiStyle.GOLD);
            g.drawRect(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2);
            g.drawImage(map, inset + 16, inset + 16, getWidth() - inset * 2 - 32, (getHeight() - inset * 2) / 2, null);
            g.setColor(new Color(16, 21, 37));
            g.fillRect(inset + 16, inset + 16 + (getHeight() - inset * 2) / 2, getWidth() - inset * 2 - 32, 75);
            g.setFont(UiStyle.font(13, Font.BOLD));
            g.setColor(UiStyle.TEXT);
            g.drawString("BANCO CENTRAL // ARQUIVO 04", inset + 28, inset + 16 + (getHeight() - inset * 2) / 2 + 28);
            g.setFont(UiStyle.font(10, Font.PLAIN));
            g.setColor(UiStyle.MUTED);
            g.drawString("O GUARDA NÃO VÊ TUDO. O LADRÃO TAMBÉM NÃO.", inset + 28,
                    inset + 16 + (getHeight() - inset * 2) / 2 + 50);
            int spriteY = getHeight() - inset - 155;
            g.drawImage(thief, inset + 38, spriteY, 112, 112, null);
            g.drawImage(guard, getWidth() - inset - 150, spriteY, 112, 112, null);
            g.drawImage(alarm, getWidth() / 2 - 15, spriteY + 32, 30, 30, null);
            g.setColor(new Color(12, 16, 30));
            g.fillRect(inset + 35, spriteY + 116, 118, 21);
            g.fillRect(getWidth() - inset - 153, spriteY + 116, 118, 21);
            g.setFont(UiStyle.font(10, Font.BOLD));
            g.setColor(UiStyle.GOLD);
            g.drawString("LADRÃO", inset + 65, spriteY + 131);
            g.drawString("GUARDA", getWidth() - inset - 125, spriteY + 131);
            g.dispose();
        }
    }
}
