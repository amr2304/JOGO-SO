package br.com.policialadrao.server;

import br.com.policialadrao.common.Direction;
import br.com.policialadrao.common.GameSnapshot;
import br.com.policialadrao.common.GameSnapshot.Cell;
import br.com.policialadrao.common.GameSnapshot.MatchStatus;
import br.com.policialadrao.common.GameSnapshot.Trail;
import br.com.policialadrao.common.GameSnapshot.VaultPhase;
import br.com.policialadrao.common.MapLayout;
import br.com.policialadrao.common.Protocol;
import br.com.policialadrao.common.Role;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/** Estado único do servidor. Toda leitura ou alteração passa pelo mesmo mutex. */
public final class GameEngine {
    public static final int SIZE = MapLayout.SIZE;
    private static final int GUARD_SPOT_TICKS = 8;

    private final GameConfig config;
    private final ReentrantLock mutex = new ReentrantLock(true);
    private final Random random = new Random();
    private final char[][] map = new char[SIZE][SIZE];
    private final boolean[][] exploredThief = new boolean[SIZE][SIZE];
    private final boolean[][] exploredGuard = new boolean[SIZE][SIZE];
    private final EnumMap<Role, Deque<String>> events = new EnumMap<>(Role.class);
    private final EnumMap<Role, Integer> lastActionTick = new EnumMap<>(Role.class);
    private final List<Alarm> alarms = new ArrayList<>();
    private final List<Footprint> footprints = new ArrayList<>();

    private Cell thief;
    private Cell guard;
    private Cell exit;
    private Cell trueChest;
    private Cell falseChest;
    private boolean falseChestOpened;
    private boolean hasLoot;
    private int smokeUsesLeft;
    private int alarmUsesLeft;
    private int tick;
    private int cycleStartTick;
    private int exitMoveDueTick = -1;
    private int thiefStunnedThrough = -1;
    private int smokeExpiresAt = -1;
    private int guardSpottedUntilTick = -1;
    private Cell smokeOrigin;
    private int cloakMovesLeft;
    private MatchStatus status = MatchStatus.WAITING;
    private Role turn = Role.THIEF;
    private String guardAlarmRegion = "";
    private String endingEvent = "";

    public GameEngine(GameConfig config) {
        this.config = config;
        for (Role role : Role.values()) {
            events.put(role, new ArrayDeque<>());
            lastActionTick.put(role, -1);
        }
        loadMap();
        smokeUsesLeft = config.smokeUses();
        alarmUsesLeft = config.alarmUses();
        placeChests();
        revealNearby(Role.THIEF);
        revealNearby(Role.GUARD);
    }

    private void loadMap() {
        String[] rows = MapLayout.rows();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                char symbol = rows[row].charAt(column);
                if (symbol == 'L') thief = new Cell(row, column);
                else if (symbol == 'G') guard = new Cell(row, column);
                else if (symbol == 'S') exit = new Cell(row, column);
                map[row][column] = symbol == '#' ? '#' : '.';
            }
        }
    }

    public void startGame() {
        mutex.lock();
        try {
            if (status != MatchStatus.WAITING) return;
            status = MatchStatus.RUNNING;
            event(Role.THIEF, "INICIO");
            event(Role.GUARD, "INICIO");
        } finally {
            mutex.unlock();
        }
    }

    public ActionResult command(Role role, String command) {
        mutex.lock();
        try {
            if (status == MatchStatus.WAITING) return ActionResult.error("aguarde");
            if (status != MatchStatus.RUNNING) return ActionResult.error("partida-encerrada");

            if (command.equals(Protocol.useCloak()) && role == Role.THIEF) {
                if (tick <= thiefStunnedThrough) return ActionResult.error("preso");
                if (lastActionTick.get(role) == tick) return ActionResult.error("aguarde");
                if (smokeUsesLeft <= 0) return ActionResult.error("sem-manto");
                smokeUsesLeft--;
                smokeOrigin = thief;
                smokeExpiresAt = tick + config.smokeDuration();
                cloakMovesLeft = config.cloakMoves();
                lastActionTick.put(role, tick);
                event(Role.THIEF, "X:U");
                return ActionResult.ok();
            }

            if (command.equals(Protocol.placeAlarm()) && role == Role.GUARD) {
                if (lastActionTick.get(role) == tick) return ActionResult.error("aguarde");
                if (alarmUsesLeft <= 0) return ActionResult.error("sem-alarme");
                if (alarms.size() >= config.maxActiveAlarms()) return ActionResult.error("limite-alarmes");
                if (alarms.stream().anyMatch(alarm -> alarm.cell.equals(guard))) return ActionResult.error("alarme-ja-existe");
                alarms.add(new Alarm(guard, tick + config.alarmDuration()));
                alarmUsesLeft--;
                lastActionTick.put(role, tick);
                event(Role.GUARD, "A:C");
                return ActionResult.ok();
            }

            String prefix = role.code() + ":M:";
            if (!command.startsWith(prefix)) return ActionResult.error("comando-invalido");
            Direction direction;
            try {
                direction = Direction.fromCode(command.substring(prefix.length()));
            } catch (IllegalArgumentException ex) {
                return ActionResult.error("comando-invalido");
            }
            if (role == Role.THIEF && tick <= thiefStunnedThrough) return ActionResult.error("preso");
            if (lastActionTick.get(role) == tick) return ActionResult.error("aguarde");
            if (role != turn) return ActionResult.error("vez");

            Cell current = role == Role.THIEF ? thief : guard;
            Cell destination = new Cell(current.row() + direction.rowDelta(), current.column() + direction.columnDelta());
            if (!inside(destination) || map[destination.row()][destination.column()] == '#') {
                return ActionResult.error("parede");
            }

            if (role == Role.THIEF) {
                thief = destination;
                cloakMovesLeft = Math.max(0, cloakMovesLeft - 1);
            } else guard = destination;
            lastActionTick.put(role, tick);
            turn = role.opponent();
            revealNearby(role);

            if (thief.equals(guard)) {
                finish(MatchStatus.GUARD_WON, "CAPTURA");
                return ActionResult.ok();
            }

            if (role == Role.THIEF) {
                handleThiefArrival(destination, direction);
            }
            return ActionResult.ok();
        } finally {
            mutex.unlock();
        }
    }

    private void handleThiefArrival(Cell destination, Direction direction) {
        if (!hasLoot && !falseChestOpened && destination.equals(falseChest) && vaultPhase() != VaultPhase.LOCKED) {
            falseChestOpened = true;
            thiefStunnedThrough = tick + config.stunDuration();
            event(Role.THIEF, "C:V");
            guardAlarmRegion = region(thief);
            event(Role.GUARD, "AF:" + guardAlarmRegion);
        } else if (!hasLoot && destination.equals(trueChest)) {
            if (vaultPhase() == VaultPhase.LOCKED) {
                event(Role.THIEF, "C:F");
            } else {
                hasLoot = true;
                event(Role.THIEF, "C:P");
                event(Role.GUARD, "C:P");
            }
        }

        if (hasLoot && destination.equals(exit)) {
            finish(MatchStatus.THIEF_WON, "FUGA");
            return;
        }

        if (alarms.removeIf(alarm -> alarm.cell.equals(destination))) {
            guardSpottedUntilTick = tick + GUARD_SPOT_TICKS;
            event(Role.GUARD, "A:H");
            event(Role.THIEF, "A:P");
        }

        if (distance(thief, guard) >= config.trackMinDistance() && cloakMovesLeft <= 0 && !isSmokeActive()) {
            footprints.add(new Footprint(destination, direction, tick + config.trackLifetime()));
        }
    }

    public void advanceTick() {
        mutex.lock();
        try {
            if (status != MatchStatus.RUNNING) return;
            tick++;
            cleanupExpiredLocked();

            if (!hasLoot) {
                int elapsed = tick - cycleStartTick;
                if (elapsed == config.alarmAtTick()) {
                    guardAlarmRegion = region(trueChest);
                    event(Role.GUARD, "A:" + guardAlarmRegion);
                    event(Role.THIEF, "A");
                }
                if (elapsed == config.vaultClosesAtTick()) {
                    event(Role.THIEF, "P:F");
                }
                if (elapsed >= config.vaultMovesAtTick()) {
                    placeChests();
                    cycleStartTick = tick;
                    guardAlarmRegion = "";
                    exitMoveDueTick = tick + config.exitDelayTicks();
                    event(Role.THIEF, "P:R");
                }
            }

            if (!hasLoot && exitMoveDueTick >= 0 && tick >= exitMoveDueTick) {
                relocateExit();
                exitMoveDueTick = -1;
                event(Role.THIEF, "S:M");
                event(Role.GUARD, "S:M");
            }
        } finally {
            mutex.unlock();
        }
    }

    public void cleanupExpired() {
        mutex.lock();
        try {
            cleanupExpiredLocked();
        } finally {
            mutex.unlock();
        }
    }

    private void cleanupExpiredLocked() {
        alarms.removeIf(alarm -> tick >= alarm.expiresAtTick);
        footprints.removeIf(footprint -> tick >= footprint.expiresAtTick);
    }

    public void disconnected(Role role) {
        mutex.lock();
        try {
            if (status == MatchStatus.RUNNING) {
                finish(role == Role.THIEF ? MatchStatus.GUARD_WON : MatchStatus.THIEF_WON, "DESCONECTOU");
            }
        } finally {
            mutex.unlock();
        }
    }

    public Delivery takeDelivery(Role role) {
        mutex.lock();
        try {
            GameSnapshot snapshot = snapshotLocked(role);
            List<String> pending = new ArrayList<>(events.get(role));
            events.get(role).clear();
            return new Delivery(snapshot, pending, endingEvent);
        } finally {
            mutex.unlock();
        }
    }

    private GameSnapshot snapshotLocked(Role role) {
        revealNearby(role);
        Cell self = role == Role.THIEF ? thief : guard;
        Cell other = role == Role.THIEF ? guard : thief;
        int vision = role == Role.THIEF ? config.thiefVision() : config.guardVision();
        boolean otherVisible = distance(self, other) <= vision;
        if (role == Role.GUARD && cloakMovesLeft > 0) otherVisible = false;
        if (role == Role.GUARD && tick <= guardSpottedUntilTick) otherVisible = true;
        if (status == MatchStatus.THIEF_WON || status == MatchStatus.GUARD_WON) otherVisible = true;

        List<Cell> chests = new ArrayList<>();
        if (role == Role.THIEF && !hasLoot && vaultPhase() != VaultPhase.LOCKED) {
            if (distance(self, trueChest) <= config.thiefVision()) chests.add(trueChest);
            if (!falseChestOpened && distance(self, falseChest) <= config.thiefVision()) chests.add(falseChest);
        }

        List<Cell> knownAlarms = role == Role.GUARD ? alarms.stream().map(alarm -> alarm.cell).toList() : List.of();
        List<Trail> knownTrails = role == Role.GUARD
                ? footprints.stream()
                    .filter(footprint -> distance(self, footprint.cell) <= config.trackVision())
                    .map(footprint -> new Trail(footprint.cell, footprint.direction,
                            Math.max(0, footprint.expiresAtTick - tick)))
                    .toList()
                : List.of();

        Cell smoke = role == Role.THIEF && isSmokeActive() ? smokeOrigin : null;

        String mask = exploredMask(role);
        VaultPhase phase = role == Role.THIEF ? vaultPhase() : VaultPhase.OPEN;
        int vaultRemainingTicks = role == Role.THIEF ? Math.max(0, config.vaultMovesAtTick() - (tick - cycleStartTick)) : 0;
        int vaultRemaining = (vaultRemainingTicks * config.tickMillis() + 999) / 1_000;
        int stunRemaining = role == Role.THIEF ? Math.max(0, thiefStunnedThrough - tick) : 0;
        return new GameSnapshot(
                role, tick, (tick * config.tickMillis() + 999) / 1_000,
                status, hasLoot, phase, vaultRemaining,
                role == Role.THIEF ? smokeUsesLeft : 0,
                role == Role.GUARD ? alarmUsesLeft : 0,
                stunRemaining,
                mask, self, otherVisible ? other : null, chests, knownAlarms, knownTrails,
                smoke, isSmokeActive() ? smokeExpiresAt - tick : 0,
                role == Role.GUARD ? guardAlarmRegion : "",
                turn, exit);
    }

    private String exploredMask(Role role) {
        boolean[][] explored = role == Role.THIEF ? exploredThief : exploredGuard;
        StringBuilder mask = new StringBuilder(SIZE * SIZE);
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) mask.append(explored[row][column] ? '1' : '0');
        }
        return mask.toString();
    }

    private void revealNearby(Role role) {
        Cell center = role == Role.THIEF ? thief : guard;
        boolean[][] explored = role == Role.THIEF ? exploredThief : exploredGuard;
        int vision = role == Role.THIEF ? config.thiefVision() : config.guardVision();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                Cell cell = new Cell(row, column);
                if (map[row][column] != '#' && distance(center, cell) <= vision) explored[row][column] = true;
            }
        }
    }

    private VaultPhase vaultPhase() {
        if (hasLoot) return VaultPhase.CARRIED;
        int elapsed = tick - cycleStartTick;
        if (elapsed >= config.vaultClosesAtTick()) return VaultPhase.LOCKED;
        if (elapsed >= config.alarmAtTick()) return VaultPhase.ALARM;
        return VaultPhase.OPEN;
    }

    private void placeChests() {
        List<Cell> candidates = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                Cell cell = new Cell(row, column);
                if (map[row][column] == '.' && !cell.equals(thief) && !cell.equals(guard) && !cell.equals(exit)
                        && distance(cell, new Cell(0, SIZE - 1)) >= 4
                        && distance(cell, new Cell(SIZE - 1, 0)) >= 4) candidates.add(cell);
            }
        }
        trueChest = candidates.get(random.nextInt(candidates.size()));
        do {
            falseChest = candidates.get(random.nextInt(candidates.size()));
        } while (falseChest.equals(trueChest) || distance(falseChest, trueChest) < 3);
        falseChestOpened = false;
    }

    private void relocateExit() {
        List<Cell> candidates = new ArrayList<>();
        List<Cell> fallback = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                Cell cell = new Cell(row, column);
                if (map[row][column] != '.' || cell.equals(thief) || cell.equals(guard) || cell.equals(exit)
                        || cell.equals(trueChest) || cell.equals(falseChest)) continue;
                fallback.add(cell);
                if (distance(cell, thief) >= 3 && distance(cell, guard) >= 3) candidates.add(cell);
            }
        }
        List<Cell> pool = candidates.isEmpty() ? fallback : candidates;
        if (!pool.isEmpty()) exit = pool.get(random.nextInt(pool.size()));
    }

    private String region(Cell cell) {
        boolean north = cell.row() < SIZE / 2;
        boolean west = cell.column() < SIZE / 2;
        if (north) return west ? "NO" : "NE";
        return west ? "SO" : "SE";
    }

    private boolean isSmokeActive() { return tick < smokeExpiresAt; }

    private void finish(MatchStatus result, String reason) {
        if (status == MatchStatus.THIEF_WON || status == MatchStatus.GUARD_WON) return;
        status = result;
        endingEvent = reason;
        String code = result == MatchStatus.THIEF_WON ? "FIM:L" : "FIM:G";
        event(Role.THIEF, code);
        event(Role.GUARD, code);
    }

    private void event(Role role, String message) { events.get(role).addLast(message); }
    private static boolean inside(Cell cell) { return cell.row() >= 0 && cell.row() < SIZE && cell.column() >= 0 && cell.column() < SIZE; }
    private static int distance(Cell first, Cell second) {
        return Math.abs(first.row() - second.row()) + Math.abs(first.column() - second.column());
    }

    public record ActionResult(boolean accepted, String errorCode) {
        static ActionResult ok() { return new ActionResult(true, ""); }
        static ActionResult error(String code) { return new ActionResult(false, code); }
    }

    public record Delivery(GameSnapshot snapshot, List<String> events, String endingEvent) { }
    private record Alarm(Cell cell, int expiresAtTick) { }
    private record Footprint(Cell cell, Direction direction, int expiresAtTick) { }
}
