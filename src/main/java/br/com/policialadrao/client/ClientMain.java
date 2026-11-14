package br.com.policialadrao.client;

import javax.swing.SwingUtilities;

public final class ClientMain {
    private ClientMain() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameWindow().show());
    }
}
