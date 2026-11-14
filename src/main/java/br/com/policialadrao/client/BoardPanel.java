package br.com.policialadrao.client;

import br.com.policialadrao.common.GameSnapshot;
import br.com.policialadrao.common.GameSnapshot.Cell;
import br.com.policialadrao.common.GameSnapshot.MatchStatus;
import br.com.policialadrao.common.GameSnapshot.Trail;
import br.com.policialadrao.common.MapLayout;
import br.com.policialadrao.common.PixelAssets;
import br.com.policialadrao.common.Role;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class BoardPanel extends JPanel {
    public static final int TILE_SIZE = 56;
    private static final int BOARD_SIZE = MapLayout.SIZE * TILE_SIZE;
    private static final Color INK = new Color(19, 24, 42);
    private static final Color GOLD = new Color(255, 203, 112);
    private final BufferedImage thiefSprite = PixelAssets.image("ladrao.png");
    private final BufferedImage guardSprite = PixelAssets.image("guarda.png");
    private final BufferedImage chestSprite = PixelAssets.image("cofre.png");
    private final BufferedImage smokeSprite = PixelAssets.image("fumaca_24px.png");
    private final BufferedImage alarmSprite = PixelAssets.image("alarme_24px.png");
    private final BufferedImage mapImage = PixelAssets.image("mapa.png");
    private final BufferedImage floorTexture = mapImage.getSubimage(72, 144, 72, 72);
    private final BufferedImage wallTexture = mapImage.getSubimage(72, 72, 72, 72);
    private final BooleanSupplier canMove;
    private final Consumer<br.com.policialadrao.common.Direction> onMove;
    private GameSnapshot snapshot;

    public BoardPanel(BooleanSupplier canMove, Consumer<br.com.policialadrao.common.Direction> onMove) {
        this.canMove = canMove;
        this.onMove = onMove;
        setOpaque(false);
        setPreferredSize(new Dimension(BOARD_SIZE + 36, BOARD_SIZE + 36));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { moveToward(event.getX(), event.getY()); }
        });
    }

    public void setSnapshot(GameSnapshot snapshot) {
        this.snapshot = snapshot;
        repaint();
    }

    private void moveToward(int x, int y) {
        if (snapshot == null || snapshot.status() != MatchStatus.RUNNING || !canMove.getAsBoolean()) return;
        int originX = (getWidth() - BOARD_SIZE) / 2;
        int originY = (getHeight() - BOARD_SIZE) / 2;
        int column = (x - originX) / TILE_SIZE;
        int row = (y - originY) / TILE_SIZE;
        if (row < 0 || row >= MapLayout.SIZE || column < 0 || column >= MapLayout.SIZE) return;
        int rowDelta = row - snapshot.self().row();
        int columnDelta = column - snapshot.self().column();
        if (Math.abs(rowDelta) + Math.abs(columnDelta) != 1) return;
        if (rowDelta < 0) onMove.accept(br.com.policialadrao.common.Direction.NORTH);
        else if (rowDelta > 0) onMove.accept(br.com.policialadrao.common.Direction.SOUTH);
        else if (columnDelta < 0) onMove.accept(br.com.policialadrao.common.Direction.WEST);
        else onMove.accept(br.com.policialadrao.common.Direction.EAST);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        int originX = (getWidth() - BOARD_SIZE) / 2;
        int originY = (getHeight() - BOARD_SIZE) / 2;
        g.setColor(new Color(7, 9, 18, 210));
        g.fillRect(originX - 10, originY - 10, BOARD_SIZE + 20, BOARD_SIZE + 20);
        drawTiles(g, originX, originY);
        if (snapshot != null) drawContents(g, originX, originY);
        g.setColor(new Color(127, 155, 184));
        g.drawRect(originX - 1, originY - 1, BOARD_SIZE + 1, BOARD_SIZE + 1);
        g.dispose();
    }

    private void drawTiles(Graphics2D g, int originX, int originY) {
        for (int row = 0; row < MapLayout.SIZE; row++) {
            for (int column = 0; column < MapLayout.SIZE; column++) {
                int x = originX + column * TILE_SIZE;
                int y = originY + row * TILE_SIZE;
                if (MapLayout.isWall(row, column)) {
                    g.drawImage(wallTexture, x, y, TILE_SIZE, TILE_SIZE, null);
                    g.setColor(new Color(11, 15, 29, 95));
                    g.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                } else if (explored(row, column) || isExitCell(row, column)) {
                    g.drawImage(floorTexture, x, y, TILE_SIZE, TILE_SIZE, null);
                    g.setColor(new Color(30, 42, 61, 84));
                    g.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                } else {
                    g.setColor(new Color(17, 22, 39));
                    g.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                    g.setColor(new Color(32, 40, 60));
                    for (int px = 8; px < TILE_SIZE; px += 16) g.fillRect(x + px, y + 12, 2, 2);
                }
                g.setColor(new Color(20, 28, 44, 150));
                g.drawRect(x, y, TILE_SIZE, TILE_SIZE);
            }
        }
    }

    private void drawExit(Graphics2D g, int x, int y) {
        g.setColor(new Color(16, 23, 40, 210));
        g.fillRect(x + 7, y + 7, TILE_SIZE - 14, TILE_SIZE - 14);
        g.setColor(GOLD);
        g.drawRect(x + 9, y + 9, TILE_SIZE - 19, TILE_SIZE - 19);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        g.drawString("SAÍDA", x + 10, y + 31);
    }

    private boolean isExitCell(int row, int column) {
        return snapshot != null && snapshot.exit() != null
                && snapshot.exit().row() == row && snapshot.exit().column() == column;
    }

    private boolean explored(int row, int column) {
        if (snapshot == null || snapshot.exploredMask().length() < MapLayout.SIZE * MapLayout.SIZE) return false;
        return snapshot.exploredMask().charAt(row * MapLayout.SIZE + column) == '1';
    }

    private void drawContents(Graphics2D g, int originX, int originY) {
        if (snapshot.exit() != null) {
            drawExit(g, originX + snapshot.exit().column() * TILE_SIZE,
                    originY + snapshot.exit().row() * TILE_SIZE);
        }
        for (Trail trail : snapshot.trails()) {
            drawAt(g, trail.cell(), originX, originY, (x, y) -> {
                g.setColor(new Color(247, 220, 143, 210));
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
                g.drawString(trail.direction().arrow(), x + 18, y + 37);
            });
        }
        for (Cell alarm : snapshot.alarms()) {
            drawAt(g, alarm, originX, originY, (x, y) -> g.drawImage(alarmSprite, x + 15, y + 15, 27, 27, null));
        }
        for (Cell chest : snapshot.visibleChests()) {
            drawAt(g, chest, originX, originY, (x, y) -> g.drawImage(chestSprite, x + 5, y + 4, 46, 46, null));
        }
        if (snapshot.smoke() != null) {
            int x = originX + snapshot.smoke().column() * TILE_SIZE - TILE_SIZE;
            int y = originY + snapshot.smoke().row() * TILE_SIZE - TILE_SIZE;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.42f));
            g.drawImage(smokeSprite, x, y, TILE_SIZE * 3, TILE_SIZE * 3, null);
            g.setComposite(AlphaComposite.SrcOver);
        }
        if (snapshot.other() != null) {
            BufferedImage other = snapshot.role() == Role.THIEF ? guardSprite : thiefSprite;
            drawPlayer(g, other, snapshot.other(), originX, originY);
        }
        BufferedImage self = snapshot.role() == Role.THIEF ? thiefSprite : guardSprite;
        drawPlayer(g, self, snapshot.self(), originX, originY);
        if (snapshot.status() == MatchStatus.THIEF_WON || snapshot.status() == MatchStatus.GUARD_WON) drawResult(g, originX, originY);
    }

    private void drawPlayer(Graphics2D g, BufferedImage sprite, Cell cell, int originX, int originY) {
        drawAt(g, cell, originX, originY, (x, y) -> {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillOval(x + 8, y + TILE_SIZE - 13, TILE_SIZE - 16, 8);
            g.drawImage(sprite, x + 5, y + 3, TILE_SIZE - 10, TILE_SIZE - 7, null);
        });
    }

    private void drawResult(Graphics2D g, int originX, int originY) {
        int boxX = originX + 74;
        int boxY = originY + BOARD_SIZE / 2 - 42;
        g.setColor(new Color(11, 14, 27, 238));
        g.fillRect(boxX, boxY, BOARD_SIZE - 148, 84);
        g.setColor(GOLD);
        g.drawRect(boxX, boxY, BOARD_SIZE - 148, 84);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        String title = snapshot.status() == MatchStatus.THIEF_WON ? "LADRÃO ESCAPOU" : "LADRÃO CAPTURADO";
        g.drawString(title, boxX + 25, boxY + 34);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setColor(new Color(215, 224, 239));
        g.drawString("Feche a janela para sair da sala.", boxX + 25, boxY + 60);
    }

    private void drawAt(Graphics2D g, Cell cell, int originX, int originY, TilePainter painter) {
        int x = originX + cell.column() * TILE_SIZE;
        int y = originY + cell.row() * TILE_SIZE;
        painter.paint(x, y);
    }

    @FunctionalInterface
    private interface TilePainter { void paint(int x, int y); }
}
