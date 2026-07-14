package com.example.pathfinder.risk;

// Resultado da análise de risco contendo informações sobre o objeto mais crítico
public class RiskAssessment {
    private final DetectedObject criticalObject;
    private final RiskLevel riskLevel;
    private final String message;
    private final String direction;
    private final boolean shouldAlert;

    public RiskAssessment(DetectedObject criticalObject, RiskLevel riskLevel, 
                         String message, String direction, boolean shouldAlert) {
        this.criticalObject = criticalObject;
        this.riskLevel = riskLevel;
        this.message = message;
        this.direction = direction;
        this.shouldAlert = shouldAlert;
    }

    public DetectedObject getCriticalObject() {
        return criticalObject;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getMessage() {
        return message;
    }

    public String getDirection() {
        return direction;
    }

    public boolean shouldAlert() {
        return shouldAlert;
    }

    public String getFullMessage() {
        if (criticalObject == null) {
            return "Caminho livre. Siga em frente.";
        }
        
        return String.format("%s a %.1f metros. %s", 
            criticalObject.getClassName(), 
            criticalObject.getDistance(), 
            message);
    }

    @Override
    public String toString() {
        return String.format("RiskAssessment[level=%s, alert=%b, message=%s]", 
            riskLevel, shouldAlert, message);
    }
}