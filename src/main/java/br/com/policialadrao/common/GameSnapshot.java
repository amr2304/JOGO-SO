package br.com.policialadrao.common;

import java.util.List;

public record GameSnapshot(
        Role role,
        int tick,
        int secondsRemaining,
        MatchStatus status,
        boolean hasLoot,
        VaultPhase vaultPhase,
        int vaultSecondsRemaining,
        int smokeUsesLeft,
        int alarmUsesLeft,
        int stunTicksLeft,
        String exploredMask,
        Cell self,
        Cell other,
        List<Cell> visibleChests,
        List<Cell> alarms,
        List<Trail> trails,
        Cell smoke,
        int smokeTicksLeft,
        String alarmRegion,
        Role turn,
        Cell exit
) {
    public GameSnapshot {
        visibleChests = List.copyOf(visibleChests);
        alarms = List.copyOf(alarms);
        trails = List.copyOf(trails);
    }

    public boolean isMyTurn() {
        return turn == role;
    }

    public enum MatchStatus { WAITING, RUNNING, THIEF_WON, GUARD_WON }
    public enum VaultPhase { OPEN, ALARM, LOCKED, CARRIED }

    public record Cell(int row, int column) { }
    public record Trail(Cell cell, Direction direction, int ticksLeft) { }
}
