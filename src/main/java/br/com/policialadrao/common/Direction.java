package br.com.policialadrao.common;

public enum Direction {
    NORTH("N", -1, 0, "↑"),
    SOUTH("S", 1, 0, "↓"),
    EAST("L", 0, 1, "→"),
    WEST("O", 0, -1, "←");

    private final String code;
    private final int rowDelta;
    private final int columnDelta;
    private final String arrow;

    Direction(String code, int rowDelta, int columnDelta, String arrow) {
        this.code = code;
        this.rowDelta = rowDelta;
        this.columnDelta = columnDelta;
        this.arrow = arrow;
    }

    public String code() { return code; }
    public int rowDelta() { return rowDelta; }
    public int columnDelta() { return columnDelta; }
    public String arrow() { return arrow; }

    public static Direction fromCode(String code) {
        for (Direction direction : values()) {
            if (direction.code.equalsIgnoreCase(code)) return direction;
        }
        throw new IllegalArgumentException("Direção desconhecida: " + code);
    }
}
