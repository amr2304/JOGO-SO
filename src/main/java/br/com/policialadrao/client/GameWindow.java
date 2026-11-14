package br.com.policialadrao.client;

import br.com.policialadrao.common.Direction;
import br.com.policialadrao.common.GameSnapshot;
import br.com.policialadrao.common.GameSnapshot.MatchStatus;
import br.com.policialadrao.common.PixelAssets;
import br.com.policialadrao.common.Role;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public final class GameWindow implements GameClient.Listener {
    private static final String START = "start";
    private static final String GAME = "game";

    private final JFrame frame = new JFrame("Polícia e Ladrão | Assalto ao Banco");
    private final JPanel cards = new JPanel(new CardLayout());
    private final StartPanel startPanel = new StartPanel();
    private GameClient client;
    private GamePanel gamePanel;
    private GameSnapshot latestSnapshot;
    private String playerName = "Jogador";

    public GameWindow() {
        cards.add(startPanel, START);
        frame.setContentPane(cards);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1120, 790));
        frame.setSize(1450, 930);
        frame.setLocationRelativeTo(null);
        BufferedImage icon = PixelAssets.image("ladrao.png");
        frame.setIconImage(icon);
        startPanel.setOnConnect(this::connect);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) {
                if (client != null) client.close();
            }
        });
    }

    public void show() { frame.setVisible(true); }

    private void connect(StartPanel.ConnectionInfo info) {
        playerName = info.playerName();
        client = new GameClient(this);
        GameClient selectedClient = client;
        Thread connector = new Thread(() -> {
            try {
                selectedClient.connect(info.host(), info.port());
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> startPanel.setStatus(
                        "Servidor indisponível. Confira o endereço e tente novamente.", true));
                selectedClient.close();
            }
        }, "conectar-ao-servidor");
        connector.setDaemon(true);
        connector.start();
    }

    @Override
    public void onRole(Role role) {
        SwingUtilities.invokeLater(() -> {
            gamePanel = new GamePanel(role, playerName, client);
            cards.add(gamePanel, GAME);
            ((CardLayout) cards.getLayout()).show(cards, GAME);
            gamePanel.requestFocusInWindow();
            if (latestSnapshot != null) gamePanel.setSnapshot(latestSnapshot);
            frame.setTitle("Polícia e Ladrão | " + role.label());
            frame.revalidate();
            frame.repaint();
        });
    }

    @Override
    public void onSnapshot(GameSnapshot snapshot) {
        latestSnapshot = snapshot;
        SwingUtilities.invokeLater(() -> {
            if (gamePanel != null) gamePanel.setSnapshot(snapshot);
        });
    }

    @Override
    public void onEvent(String message) {
        SwingUtilities.invokeLater(() -> {
            if (gamePanel != null) gamePanel.addEvent(message, false);
            else startPanel.setStatus(message, false);
        });
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> {
            if (gamePanel != null) gamePanel.addEvent(message, true);
            else startPanel.setStatus(message, true);
        });
    }

    @Override
    public void onEnd(Role winner) {
        SwingUtilities.invokeLater(() -> {
            if (gamePanel != null) gamePanel.addEvent(
                    winner == Role.THIEF ? "Fim da partida: o ladrão escapou." : "Fim da partida: o guarda venceu.", false);
        });
    }

    @Override
    public void onClosed() {
        if (frame.isDisplayable()) {
            SwingUtilities.invokeLater(() -> {
                if (gamePanel != null && (latestSnapshot == null
                        || (latestSnapshot.status() != MatchStatus.THIEF_WON && latestSnapshot.status() != MatchStatus.GUARD_WON))) {
                    gamePanel.addEvent("Conexão encerrada.", true);
                }
            });
        }
    }

    private static final class GamePanel extends JPanel {
        private final Role role;
        private final String playerName;
        private final GameClient client;
        private final BoardPanel board;
        private final JLabel roleBadge = new JLabel("", SwingConstants.CENTER);
        private final JLabel connectionStatus = new JLabel("Aguardando o segundo jogador...", SwingConstants.LEFT);
        private final JLabel clock = new JLabel("10:00", SwingConstants.CENTER);
        private final JLabel objective = new JLabel("", SwingConstants.LEFT);
        private final JLabel vault = new JLabel("", SwingConstants.LEFT);
        private final JLabel itemStatus = new JLabel("", SwingConstants.LEFT);
        private final JLabel feedback = new JLabel("", SwingConstants.LEFT);
        private final JTextArea eventLog = new JTextArea();
        private GameSnapshot current;

        private GamePanel(Role role, String playerName, GameClient client) {
            this.role = role;
            this.playerName = playerName;
            this.client = client;
            this.board = new BoardPanel(this::canMove, client::move);
            setLayout(new BorderLayout(18, 0));
            setBackground(UiStyle.BACKGROUND);
            setFocusable(true);
            setBorder(BorderFactory.createEmptyBorder(22, 26, 24, 26));
            add(createHeader(), BorderLayout.NORTH);
            add(createMainArea(), BorderLayout.CENTER);
            installKeys();
        }

        private JPanel createHeader() {
            JPanel header = new JPanel(new BorderLayout(15, 0));
            header.setOpaque(false);
            header.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
            JLabel title = new JLabel("POLÍCIA E LADRÃO");
            title.setFont(UiStyle.font(18, Font.BOLD));
            title.setForeground(UiStyle.GOLD);
            JLabel identity = new JLabel("AGENTE: " + playerName.toUpperCase());
            identity.setFont(UiStyle.font(11, Font.PLAIN));
            identity.setForeground(UiStyle.MUTED);
            JPanel titleBlock = new JPanel();
            titleBlock.setOpaque(false);
            titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
            titleBlock.add(title);
            titleBlock.add(Box.createVerticalStrut(4));
            titleBlock.add(identity);

            roleBadge.setText(role == Role.THIEF ? "LADRÃO" : "GUARDA");
            roleBadge.setFont(UiStyle.font(11, Font.BOLD));
            roleBadge.setForeground(role == Role.THIEF ? new Color(255, 214, 154) : new Color(188, 208, 255));
            roleBadge.setOpaque(true);
            roleBadge.setBackground(role == Role.THIEF ? new Color(102, 67, 44) : new Color(47, 69, 130));
            roleBadge.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

            clock.setFont(UiStyle.font(22, Font.BOLD));
            clock.setForeground(Color.WHITE);
            JPanel clockBlock = new JPanel(new BorderLayout(8, 0));
            clockBlock.setOpaque(false);
            clockBlock.add(new JLabel("TEMPO", SwingConstants.RIGHT), BorderLayout.WEST);
            clockBlock.add(clock, BorderLayout.CENTER);
            clockBlock.getComponent(0).setFont(UiStyle.font(10, Font.BOLD));
            clockBlock.getComponent(0).setForeground(UiStyle.MUTED);
            header.add(titleBlock, BorderLayout.WEST);
            header.add(roleBadge, BorderLayout.CENTER);
            header.add(clockBlock, BorderLayout.EAST);
            return header;
        }

        private JPanel createMainArea() {
            JPanel main = new JPanel(new BorderLayout(20, 0));
            main.setOpaque(false);
            JPanel boardFrame = new JPanel(new BorderLayout());
            boardFrame.setOpaque(true);
            boardFrame.setBackground(new Color(11, 14, 26));
            boardFrame.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(63, 78, 109), 2),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            boardFrame.add(board, BorderLayout.CENTER);
            main.add(boardFrame, BorderLayout.CENTER);
            main.add(createSidebar(), BorderLayout.EAST);
            return main;
        }

        private JPanel createSidebar() {
            JPanel sidebar = new JPanel();
            sidebar.setPreferredSize(new Dimension(320, 0));
            sidebar.setOpaque(true);
            sidebar.setBackground(UiStyle.PANEL);
            sidebar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(60, 75, 105), 1),
                    BorderFactory.createEmptyBorder(18, 17, 15, 17)));
            sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

            connectionStatus.setFont(UiStyle.font(11, Font.BOLD));
            connectionStatus.setForeground(UiStyle.TEXT);
            objective.setFont(UiStyle.font(11, Font.PLAIN));
            objective.setForeground(UiStyle.TEXT);
            objective.setVerticalAlignment(SwingConstants.TOP);
            objective.setPreferredSize(new Dimension(280, 56));
            objective.setMaximumSize(new Dimension(280, 56));
            vault.setFont(UiStyle.font(10, Font.BOLD));
            vault.setForeground(UiStyle.GOLD);
            itemStatus.setFont(UiStyle.font(10, Font.PLAIN));
            itemStatus.setForeground(UiStyle.MUTED);
            feedback.setFont(UiStyle.font(10, Font.PLAIN));
            feedback.setForeground(UiStyle.MUTED);

            sidebar.add(connectionStatus);
            sidebar.add(Box.createVerticalStrut(14));
            sidebar.add(section("OBJETIVO"));
            sidebar.add(objective);
            sidebar.add(Box.createVerticalStrut(8));
            sidebar.add(vault);
            sidebar.add(Box.createVerticalStrut(14));
            sidebar.add(section("MOVIMENTO  ·  WASD / SETAS"));
            sidebar.add(createDirectionPad());
            sidebar.add(Box.createVerticalStrut(12));
            sidebar.add(section(role == Role.THIEF ? "HABILIDADE" : "EQUIPAMENTO"));
            javax.swing.JButton itemButton = UiStyle.button(
                    role == Role.THIEF ? "USAR MANTO  [ESPAÇO]" : "ARMAR ALARME  [F]", false);
            itemButton.setFocusable(false);
            itemButton.setAlignmentX(LEFT_ALIGNMENT);
            itemButton.addActionListener(event -> client.useItem());
            sidebar.add(itemButton);
            sidebar.add(Box.createVerticalStrut(5));
            sidebar.add(itemStatus);
            sidebar.add(Box.createVerticalStrut(13));
            sidebar.add(section("RÁDIO"));

            eventLog.setEditable(false);
            eventLog.setFocusable(false);
            eventLog.setLineWrap(true);
            eventLog.setWrapStyleWord(true);
            eventLog.setFont(UiStyle.font(10, Font.PLAIN));
            eventLog.setForeground(UiStyle.TEXT);
            eventLog.setBackground(new Color(13, 17, 29));
            eventLog.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JScrollPane logScroll = new JScrollPane(eventLog);
            logScroll.setBorder(BorderFactory.createLineBorder(new Color(53, 67, 95)));
            logScroll.setAlignmentX(LEFT_ALIGNMENT);
            logScroll.setPreferredSize(new Dimension(284, 160));
            logScroll.setMaximumSize(new Dimension(284, 190));
            logScroll.setMinimumSize(new Dimension(284, 125));
            sidebar.add(logScroll);
            sidebar.add(Box.createVerticalStrut(8));
            sidebar.add(feedback);
            sidebar.add(Box.createVerticalGlue());
            JLabel hint = new JLabel("Clique numa casa ao lado para andar.");
            hint.setFont(UiStyle.font(9, Font.PLAIN));
            hint.setForeground(UiStyle.MUTED);
            sidebar.add(hint);
            return sidebar;
        }

        private JPanel createDirectionPad() {
            JPanel pad = new JPanel(new GridLayout(3, 3, 4, 4));
            pad.setOpaque(false);
            pad.setMaximumSize(new Dimension(184, 118));
            pad.setAlignmentX(LEFT_ALIGNMENT);
            pad.add(emptyCell());
            pad.add(directionButton("↑", Direction.NORTH));
            pad.add(emptyCell());
            pad.add(directionButton("←", Direction.WEST));
            JLabel center = new JLabel(role == Role.THIEF ? "L" : "G", SwingConstants.CENTER);
            center.setFont(UiStyle.font(12, Font.BOLD));
            center.setForeground(UiStyle.GOLD);
            center.setOpaque(true);
            center.setBackground(new Color(13, 17, 29));
            pad.add(center);
            pad.add(directionButton("→", Direction.EAST));
            pad.add(emptyCell());
            pad.add(directionButton("↓", Direction.SOUTH));
            pad.add(emptyCell());
            return pad;
        }

        private static JLabel emptyCell() { return new JLabel(); }

        private javax.swing.JButton directionButton(String label, Direction direction) {
            javax.swing.JButton button = UiStyle.button(label, false);
            button.setFocusable(false);
            button.setMargin(new java.awt.Insets(3, 8, 3, 8));
            button.addActionListener(event -> { if (canMove()) client.move(direction); });
            return button;
        }

        private static JLabel section(String text) {
            JLabel label = new JLabel(text);
            label.setFont(UiStyle.font(9, Font.BOLD));
            label.setForeground(UiStyle.GOLD);
            label.setAlignmentX(LEFT_ALIGNMENT);
            return label;
        }

        private void installKeys() {
            var input = getInputMap(WHEN_IN_FOCUSED_WINDOW);
            var actions = getActionMap();
            bind(input, actions, "north", Direction.NORTH, KeyEvent.VK_W, KeyEvent.VK_UP);
            bind(input, actions, "south", Direction.SOUTH, KeyEvent.VK_S, KeyEvent.VK_DOWN);
            bind(input, actions, "west", Direction.WEST, KeyEvent.VK_A, KeyEvent.VK_LEFT);
            bind(input, actions, "east", Direction.EAST, KeyEvent.VK_D, KeyEvent.VK_RIGHT);
            if (role == Role.THIEF) {
                input.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "item");
            } else {
                input.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "item");
            }
            actions.put("item", new javax.swing.AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { client.useItem(); }
            });
        }

        private void bind(javax.swing.InputMap input, javax.swing.ActionMap actions, String name,
                          Direction direction, int firstKey, int secondKey) {
            input.put(KeyStroke.getKeyStroke(firstKey, 0), name);
            input.put(KeyStroke.getKeyStroke(secondKey, 0), name);
            actions.put(name, new javax.swing.AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    if (canMove()) client.move(direction);
                }
            });
        }

        private boolean canMove() {
            return current != null && current.status() == MatchStatus.RUNNING && current.isMyTurn();
        }

        private void setSnapshot(GameSnapshot snapshot) {
            current = snapshot;
            board.setSnapshot(snapshot);
            clock.setText(String.format("%02d:%02d", snapshot.secondsRemaining() / 60, snapshot.secondsRemaining() % 60));
            if (snapshot.status() == MatchStatus.WAITING) connectionStatus.setText("AGUARDANDO OUTRO JOGADOR...");
            else if (snapshot.status() == MatchStatus.RUNNING) {
                connectionStatus.setText((snapshot.isMyTurn() ? "SUA VEZ" : "VEZ DO OPONENTE")
                        + "  //  RODADA " + snapshot.tick() + "  //  EM ANDAMENTO");
            } else connectionStatus.setText("PARTIDA ENCERRADA");

            if (role == Role.THIEF) {
                objective.setText("<html>Encontre o cofre verdadeiro e alcance a saída dourada.</html>");
                if (snapshot.hasLoot()) objective.setText("<html>Você pegou o cofre. Fuja até a saída dourada!</html>");
                String phase = switch (snapshot.vaultPhase()) {
                    case OPEN -> "ABERTO";
                    case ALARM -> "ALARME ATIVO";
                    case LOCKED -> "TRANCADO";
                    case CARRIED -> "COFRE PEGADO";
                };
                vault.setText(snapshot.vaultPhase() == GameSnapshot.VaultPhase.CARRIED ? phase
                        : "COFRE: " + phase + "  ·  PRÓXIMO CICLO " + snapshot.vaultSecondsRemaining() + "s");
                itemStatus.setText("Manto restante: " + snapshot.smokeUsesLeft()
                        + (snapshot.stunTicksLeft() > 0 ? "  ·  PRESO: " + snapshot.stunTicksLeft() + " ticks" : ""));
            } else {
                objective.setText("<html>Persiga o ladrão, siga as pegadas e proteja o banco.</html>");
                if (snapshot.hasLoot()) objective.setText("<html>O ladrão pegou o cofre. Intercepte-o antes que alcance a saída.</html>");
                vault.setText(snapshot.alarmRegion().isBlank() ? "ALARME: SEM REGIÃO" : "REGIÃO DO ALARME: " + snapshot.alarmRegion());
                itemStatus.setText("Alarmes restantes: " + snapshot.alarmUsesLeft());
            }
            if (snapshot.status() == MatchStatus.THIEF_WON) feedback.setText("Vitória do ladrão.");
            else if (snapshot.status() == MatchStatus.GUARD_WON) feedback.setText("Vitória do guarda.");
        }

        private void addEvent(String message, boolean isError) {
            if (eventLog.getLineCount() > 15) eventLog.setText("");
            eventLog.append((isError ? "! " : "> ") + message + "\n");
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
            feedback.setText(message);
            feedback.setForeground(isError ? new Color(255, 142, 132) : UiStyle.MUTED);
        }
    }
}
