package br.com.policialadrao.common;

import br.com.policialadrao.common.GameSnapshot.Cell;
import br.com.policialadrao.common.GameSnapshot.Trail;

import java.util.ArrayList;
import java.util.List;

/** Protocolo de texto, com uma mensagem por linha, conforme o projeto. */
public final class Protocol {
    private Protocol() { }

    public static String move(Role role, Direction direction) {
        return role.code() + ":M:" + direction.code();
    }

    public static String useCloak() { return "L:X"; }
    public static String placeAlarm() { return "G:A"; }

    public static String encode(GameSnapshot state) {
        StringBuilder message = new StringBuilder("S:");
        append(message, state.tick());
        append(message, state.secondsRemaining());
        append(message, state.status().name());
        append(message, state.hasLoot());
        append(message, state.vaultPhase().name());
        append(message, state.vaultSecondsRemaining());
        append(message, state.smokeUsesLeft());
        append(message, state.alarmUsesLeft());
        append(message, state.stunTicksLeft());
        append(message, state.exploredMask());
        append(message, state.self().row());
        append(message, state.self().column());
        append(message, state.other() == null ? -1 : state.other().row());
        append(message, state.other() == null ? -1 : state.other().column());
        append(message, cells(state.visibleChests()));
        append(message, cells(state.alarms()));
        append(message, trails(state.trails()));
        append(message, state.smoke() == null ? -1 : state.smoke().row());
        append(message, state.smoke() == null ? -1 : state.smoke().column());
        append(message, state.smokeTicksLeft());
        append(message, state.alarmRegion().isBlank() ? "-" : state.alarmRegion());
        append(message, state.turn().code());
        append(message, state.exit().row());
        append(message, state.exit().column());
        message.setLength(message.length() - 1);
        return message.toString();
    }

    public static GameSnapshot decode(String message, Role role) {
        if (!message.startsWith("S:")) throw new IllegalArgumentException("Não é uma mensagem de estado.");
        String[] parts = message.substring(2).split(";", -1);
        if (parts.length != 24) throw new IllegalArgumentException("Estado incompleto recebido do servidor.");
        return new GameSnapshot(
                role,
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                GameSnapshot.MatchStatus.valueOf(parts[2]),
                Boolean.parseBoolean(parts[3]),
                GameSnapshot.VaultPhase.valueOf(parts[4]),
                Integer.parseInt(parts[5]),
                Integer.parseInt(parts[6]),
                Integer.parseInt(parts[7]),
                Integer.parseInt(parts[8]),
                parts[9],
                new Cell(Integer.parseInt(parts[10]), Integer.parseInt(parts[11])),
                cellOrNull(parts[12], parts[13]),
                decodeCells(parts[14]),
                decodeCells(parts[15]),
                decodeTrails(parts[16]),
                cellOrNull(parts[17], parts[18]),
                Integer.parseInt(parts[19]),
                parts[20].equals("-") ? "" : parts[20],
                Role.fromCode(parts[21]),
                new Cell(Integer.parseInt(parts[22]), Integer.parseInt(parts[23]))
        );
    }

    private static void append(StringBuilder target, Object value) {
        target.append(value).append(';');
    }

    private static String cells(List<Cell> cells) {
        if (cells.isEmpty()) return "-";
        return cells.stream().map(cell -> cell.row() + "," + cell.column()).reduce((a, b) -> a + "," + b).orElse("-");
    }

    private static List<Cell> decodeCells(String encoded) {
        List<Cell> cells = new ArrayList<>();
        if (encoded.equals("-") || encoded.isBlank()) return cells;
        String[] values = encoded.split(",");
        for (int i = 0; i + 1 < values.length; i += 2) {
            cells.add(new Cell(Integer.parseInt(values[i]), Integer.parseInt(values[i + 1])));
        }
        return cells;
    }

    private static String trails(List<Trail> trails) {
        if (trails.isEmpty()) return "-";
        return trails.stream()
                .map(trail -> trail.cell().row() + "," + trail.cell().column() + ","
                        + trail.direction().code() + "," + trail.ticksLeft())
                .reduce((a, b) -> a + "," + b).orElse("-");
    }

    private static List<Trail> decodeTrails(String encoded) {
        List<Trail> trails = new ArrayList<>();
        if (encoded.equals("-") || encoded.isBlank()) return trails;
        String[] values = encoded.split(",");
        for (int i = 0; i + 3 < values.length; i += 4) {
            trails.add(new Trail(
                    new Cell(Integer.parseInt(values[i]), Integer.parseInt(values[i + 1])),
                    Direction.fromCode(values[i + 2]),
                    Integer.parseInt(values[i + 3])));
        }
        return trails;
    }

    private static Cell cellOrNull(String row, String column) {
        int r = Integer.parseInt(row);
        int c = Integer.parseInt(column);
        return r < 0 || c < 0 ? null : new Cell(r, c);
    }
}
