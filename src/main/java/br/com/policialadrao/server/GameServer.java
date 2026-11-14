package br.com.policialadrao.server;

import br.com.policialadrao.common.Protocol;
import br.com.policialadrao.common.Role;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Aceita dois clientes, valida seus comandos e distribui visões filtradas. */
public final class GameServer implements AutoCloseable {
    private final int port;
    private final GameConfig config;
    private final GameEngine engine;
    private final ConcurrentHashMap<Role, PlayerSession> players = new ConcurrentHashMap<>();
    private final Set<Role> finalMessagesSent = ConcurrentHashMap.newKeySet();
    private final Object registrationLock = new Object();
    private final Object broadcastLock = new Object();
    private volatile ServerSocket serverSocket;
    private volatile ScheduledExecutorService clock;
    private volatile ScheduledExecutorService cleaner;

    public GameServer(int port, GameConfig config) {
        this.port = port;
        this.config = config;
        this.engine = new GameEngine(config);
    }

    public void start() throws IOException {
        try (ServerSocket listeningSocket = new ServerSocket()) {
            serverSocket = listeningSocket;
            listeningSocket.setReuseAddress(true);
            listeningSocket.bind(new InetSocketAddress("0.0.0.0", port));
            System.out.printf("Servidor aguardando dois jogadores na porta %d.%n", port);
            while (!listeningSocket.isClosed()) {
                Socket socket = listeningSocket.accept();
                socket.setTcpNoDelay(true);
                PlayerSession session = register(socket);
                if (session != null) {
                    session.start();
                    broadcast();
                }
            }
        } catch (IOException exception) {
            if (serverSocket == null || !serverSocket.isClosed()) throw exception;
        }
    }

    private PlayerSession register(Socket socket) throws IOException {
        synchronized (registrationLock) {
            Role role;
            if (!players.containsKey(Role.THIEF)) role = Role.THIEF;
            else if (!players.containsKey(Role.GUARD)) role = Role.GUARD;
            else {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write("E:partida-cheia");
                    writer.newLine();
                    writer.flush();
                }
                socket.close();
                return null;
            }

            PlayerSession session = new PlayerSession(socket, role);
            players.put(role, session);
            session.send("R:" + role.code());
            if (players.size() == 2) {
                engine.startGame();
                startBackgroundThreads();
            }
            return session;
        }
    }

    private void startBackgroundThreads() {
        if (clock != null) return;
        clock = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, "relogio-do-jogo"));
        cleaner = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, "faxina-do-jogo"));
        clock.scheduleAtFixedRate(() -> {
            engine.advanceTick();
            broadcast();
        }, config.tickMillis(), config.tickMillis(), TimeUnit.MILLISECONDS);
        cleaner.scheduleAtFixedRate(engine::cleanupExpired,
                Math.max(100, config.tickMillis() / 4), Math.max(100, config.tickMillis() / 4), TimeUnit.MILLISECONDS);
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private void broadcast() {
        synchronized (broadcastLock) {
            for (Role role : Role.values()) {
                PlayerSession player = players.get(role);
                if (player == null || player.closed) continue;
                GameEngine.Delivery delivery = engine.takeDelivery(role);
                for (String event : delivery.events()) {
                    if (event.startsWith("FIM:")) {
                        if (finalMessagesSent.add(role)) player.send(event);
                    } else {
                        player.send("EV:" + event);
                    }
                }
                player.send(Protocol.encode(delivery.snapshot()));
            }
        }
    }

    private void handleCommand(Role role, String command, PlayerSession player) {
        GameEngine.ActionResult result = engine.command(role, command);
        if (!result.accepted()) {
            player.send("E:" + result.errorCode());
            return;
        }
        broadcast();
    }

    private void disconnected(PlayerSession player) {
        if (player.disconnectHandled) return;
        player.disconnectHandled = true;
        player.closed = true;
        synchronized (registrationLock) {
            if (players.size() == 1) {
                players.remove(player.role, player);
                player.close();
                System.out.printf("Jogador %s saiu antes do início da partida.%n", player.role.label());
                return;
            }
        }
        engine.disconnected(player.role);
        player.close();
        broadcast();
        System.out.printf("Jogador %s desconectou.%n", player.role.label());
    }

    @Override
    public void close() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }
        if (clock != null) clock.shutdownNow();
        if (cleaner != null) cleaner.shutdownNow();
        for (PlayerSession player : players.values()) player.close();
    }

    private final class PlayerSession implements Runnable {
        private final Socket socket;
        private final Role role;
        private final BufferedWriter writer;
        private volatile boolean closed;
        private volatile boolean disconnectHandled;

        private PlayerSession(Socket socket, Role role) throws IOException {
            this.socket = socket;
            this.role = role;
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void start() {
            Thread reader = daemon(this, "jogador-" + role.code());
            reader.start();
        }

        private synchronized void send(String message) {
            if (closed) return;
            try {
                writer.write(message);
                writer.newLine();
                writer.flush();
            } catch (IOException exception) {
                close();
            }
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) handleCommand(role, line.trim(), this);
            } catch (IOException ignored) {
                // A desconexão é tratada no finally.
            } finally {
                disconnected(this);
            }
        }

        private synchronized void close() {
            closed = true;
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
