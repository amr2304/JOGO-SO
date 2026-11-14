package br.com.policialadrao.common;

public enum Role {
    THIEF("L", "Ladrão"),
    GUARD("G", "Guarda");

    private final String code;
    private final String label;

    Role(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public Role opponent() {
        return this == THIEF ? GUARD : THIEF;
    }

    public static Role fromCode(String code) {
        for (Role role : values()) {
            if (role.code.equalsIgnoreCase(code)) return role;
        }
        throw new IllegalArgumentException("Papel desconhecido: " + code);
    }
}
