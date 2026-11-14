package br.com.policialadrao.server;

public final class ServerMain {
    private ServerMain() { }

    public static void main(String[] args) throws Exception {
        int port = 5050;
        boolean demo = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--demo")) demo = true;
            else if (args[i].equalsIgnoreCase("--port") && i + 1 < args.length) port = Integer.parseInt(args[++i]);
        }

        GameServer server = new GameServer(port, demo ? GameConfig.demo() : GameConfig.standard());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "encerrar-servidor"));
        System.out.printf("Modo: %s | ciclo: %d/%d/%d ticks | tempo máximo: %d segundos%n",
                demo ? "demonstração" : "padrão",
                demo ? 24 : 120, demo ? 36 : 180, demo ? 48 : 240,
                demo ? 60 : 600);
        server.start();
    }
}
