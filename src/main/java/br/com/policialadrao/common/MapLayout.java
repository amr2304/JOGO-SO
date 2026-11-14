package br.com.policialadrao.common;

/** Grade 7 x 7 simétrica: o ladrão começa embaixo à esquerda e o guarda em cima à direita. */
public final class MapLayout {
    public static final int SIZE = 7;
    private static final String[] ROWS = {
            "......G",
            ".#.#.#.",
            "..#.#..",
            ".......",
            "..#.#..",
            ".#.#.#.",
            "L.....S"
    };

    private MapLayout() { }

    public static String[] rows() { return ROWS.clone(); }
    public static boolean isWall(int row, int column) { return ROWS[row].charAt(column) == '#'; }
    public static boolean isExit(int row, int column) { return ROWS[row].charAt(column) == 'S'; }
}
