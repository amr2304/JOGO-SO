package br.com.policialadrao.server;

/** Números de equilíbrio reunidos em um único lugar. */
public record GameConfig(
        int tickMillis,
        int alarmAtTick,
        int vaultClosesAtTick,
        int vaultMovesAtTick,
        int exitDelayTicks,
        int thiefVision,
        int guardVision,
        int trackMinDistance,
        int trackLifetime,
        int trackVision,
        int smokeDuration,
        int alarmDuration,
        int stunDuration,
        int smokeUses,
        int alarmUses,
        int maxActiveAlarms,
        int cloakMoves
) {
    public static GameConfig standard() {
        return new GameConfig(1_000, 120, 180, 240, 60,
                2, 1, 4, 5, 3, 3, 20, 2, 1, 2, 2, 2);
    }

    /** Partida curta para gravar um vídeo de demonstração. */
    public static GameConfig demo() {
        return new GameConfig(500, 24, 36, 48, 12,
                2, 1, 4, 5, 3, 3, 20, 2, 1, 2, 2, 2);
    }
}
