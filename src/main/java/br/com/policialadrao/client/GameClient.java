package br.com.policialadrao.client;

import br.com.policialadrao.common.GameSnapshot;
import br.com.policialadrao.common.Protocol;
import br.com.policialadrao.common.Role;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Conexão de um processo cliente com o servidor. */
public final class GameClient implements AutoCloseable {
    public interface Listener {
        default void onRole(Role role) { }
        default void onSnapshot(GameSnapshot snapshot) { }
        default void onEvent(String message) { }
        default void onError(String message) { }
        default void onEnd(Role winner) { }
        default void onClosed() { }
    }

    private final Listener listener;
    private final Socket socket = new Socket();
    private volatile BufferedWriter writer;
    private volatile Role role;
    private volatile boolean closed;

    public GameClient(Listener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port) throws IOException {
        socket.connect(new InetSocketAddress(host, port), 5_000);
        socket.setTcpNoDelay(true);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(this::readMessages, "leitor-do-servidor");
        reader.setDaemon(true);
        reader.start();
    }

    public void move(br.com.policialadrao.common.Direction direction) {
        if (role != null) send(Protocol.move(role, direction));
    }

    public void useItem() {
        if (role == Role.THIEF) send(Protocol.useCloak());
        if (role == Role.GUARD) send(Protocol.placeAlarm());
    }

    private synchronized void send(String message) {
        if (closed || writer == null) return;
        try {
            writer.write(message);
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            listener.onError("A conexão com o servidor foi interrompida.");
            close();
        }
    }

    private void readMessages() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) handle(line.trim());
        } catch (IOException exception) {
            if (!closed) listener.onError("Não foi possível manter a conexão com o servidor.");
        } finally {
            closed = true;
            listener.onClosed();
        }
    }

    private void handle(String line) {
        try {
            if (line.startsWith("R:")) {
                role = Role.fromCode(line.substring(2));
                listener.onRole(role);
            } else if (line.startsWith("S:")) {
                if (role != null) listener.onSnapshot(Protocol.decode(line, role));
            } else if (line.startsWith("EV:")) {
                listener.onEvent(describeEvent(line.substring(3)));
            } else if (line.startsWith("E:")) {
                listener.onError(describeError(line.substring(2)));
            } else if (line.startsWith("FIM:")) {
                listener.onEnd(Role.fromCode(line.substring(4)));
            }
        } catch (RuntimeException exception) {
            listener.onError("Mensagem inesperada recebida do servidor.");
        }
    }

    private static String describeEvent(String code) {
        if (code.equals("INICIO")) return "Os dois jogadores entraram. A partida começou!";
        if (code.equals("A")) return "O alarme do cofre tocou. O guarda ouviu o aviso.";
        if (code.startsWith("A:")) return "Alarme geral: cofre na região " + code.substring(2) + ".";
        if (code.equals("P:F")) return "A porta do cofre fechou.";
        if (code.equals("P:R")) return "A porta abriu. Os cofres mudaram de lugar.";
        if (code.equals("S:M")) return "A saída mudou de lugar! Procure a porta dourada.";
        if (code.equals("C:P")) return "O ladrão pegou o cofre verdadeiro! Corra até a saída.";
        if (code.equals("C:V")) return "O cofre era uma isca. Você perdeu tempo revistando.";
        if (code.equals("C:F")) return "O cofre está trancado neste momento.";
        if (code.startsWith("AF:")) return "Alarme da isca: o ladrão está na região " + code.substring(3) + ".";
        if (code.equals("A:C")) return "Alarme armado neste piso.";
        if (code.equals("A:H")) return "Seu alarme foi disparado! O ladrão está exposto.";
        if (code.equals("A:P")) return "Você acionou um alarme. O guarda sabe onde você está.";
        if (code.equals("X:U")) return "Manto ativado. O guarda não consegue te ver pelas próximas jogadas.";
        if (code.equals("CAPTURA")) return "O ladrão foi capturado.";
        if (code.equals("FUGA")) return "O ladrão escapou com o cofre.";
        if (code.equals("TEMPO")) return "O tempo acabou.";
        if (code.equals("DESCONECTOU")) return "O outro jogador saiu da partida.";
        return code;
    }

    private static String describeError(String code) {
        return switch (code) {
            case "aguarde" -> "Você já agiu neste tick. Aguarde o próximo segundo.";
            case "vez" -> "Não é a sua vez. Só é possível andar depois que o adversário jogar.";
            case "parede" -> "Esse caminho está bloqueado.";
            case "preso" -> "Você está paralisado por mais " + "alguns ticks" + ".";
            case "sem-manto" -> "Você já usou o manto desta partida.";
            case "sem-alarme" -> "Você já usou os dois alarmes.";
            case "limite-alarmes" -> "Já existem dois alarmes ativos.";
            case "alarme-ja-existe" -> "Já existe um alarme nesse piso.";
            case "cofre-fechado" -> "A porta do cofre está fechada.";
            case "partida-cheia" -> "A sala já tem dois jogadores.";
            case "partida-encerrada" -> "Esta partida já terminou.";
            case "comando-invalido" -> "Comando inválido.";
            default -> "Ação não realizada: " + code + ".";
        };
    }

    @Override
    public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
