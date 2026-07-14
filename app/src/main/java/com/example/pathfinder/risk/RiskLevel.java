package com.example.pathfinder.risk;

// Níveis de risco para análise de colisão
public enum RiskLevel {
    SAFE(0, "Seguro"),
    LOW(1, "Baixo risco"),
    MEDIUM(2, "Risco médio"),
    HIGH(3, "Risco alto"),
    CRITICAL(4, "Risco crítico");

    private final int priority;
    private final String description;

    RiskLevel(int priority, String description) {
        this.priority = priority;
        this.description = description;
    }

public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }
}