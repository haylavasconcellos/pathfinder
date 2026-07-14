package com.example.pathfinder.risk;

import android.util.Log;
import android.util.Pair;

import com.example.pathfinder.detection.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Módulo de Análise de Risco
// Responsável por avaliar criticidade de objetos detectados e gerar os alertas
public class RiskAnalyzer {
    private static final String TAG = "RiskAnalyzer";

    // Thresholds de distância (em metros)
    private static final float DISTANCE_CRITICAL = 0.5f;
    private static final float DISTANCE_HIGH = 1.0f;
    private static final float DISTANCE_MEDIUM = 2.0f;
    private static final float DISTANCE_LOW = 3.0f;

    private final int screenWidth;
    private final int screenHeight;
    private final Map<String, Float> classPriorities;

    private long lastAlertTime = 0;
    private static final long ALERT_COOLDOWN_MS = 2000;
    private static final long NARRATION_COOLDOWN_MS = 5000;

    public RiskAnalyzer(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.classPriorities = initializeClassPriorities();
        Log.d(TAG, String.format("RiskAnalyzer inicializado com tela %dx%d", screenWidth, screenHeight));
    }

    // Inicializa pesos de prioridade para diferentes classes de objetos
    private Map<String, Float> initializeClassPriorities() {
        Map<String, Float> priorities = new HashMap<>();

        // Alta prioridade - pessoas e veículos
        priorities.put("person", 2.0f);
        priorities.put("car", 1.8f);
        priorities.put("bicycle", 1.7f);
        priorities.put("motorcycle", 1.7f);
        priorities.put("bus", 1.8f);
        priorities.put("truck", 1.8f);

        // Média prioridade - obstáculos fixos
        priorities.put("chair", 1.2f);
        priorities.put("bench", 1.2f);
        priorities.put("potted plant", 1.1f);
        priorities.put("traffic light", 1.0f);
        priorities.put("fire hydrant", 1.3f);
        priorities.put("stop sign", 1.0f);

        // Baixa prioridade - objetos pequenos ou menos críticos
        priorities.put("bottle", 0.8f);
        priorities.put("cup", 0.7f);
        priorities.put("backpack", 0.9f);

        return priorities;
    }

    // Analisa os objetos detectados e retorna a avaliação de risco
    public RiskAssessment analyzeRisk(List<Pair<BoundingBox, Float>> detectedObjectsWithDistance,
                                      float nearestWallDistance) {
        // Verifica parede
        if (nearestWallDistance > Float.MIN_VALUE && nearestWallDistance < DISTANCE_CRITICAL) {
            return createWallWarning(nearestWallDistance);
        }

        // Converter para DetectedObject
        List<DetectedObject> objects = new ArrayList<>();
        for (Pair<BoundingBox, Float> pair : detectedObjectsWithDistance) {
            objects.add(new DetectedObject(pair.first, pair.second));
        }

        Log.d(TAG, String.format("Analisando %d objetos detectados", objects.size()));

        if (objects.isEmpty()) {
            return new RiskAssessment(null, RiskLevel.SAFE,
                "Siga em frente", "frente", false);
        }

        // Calcular score de risco para cada objeto
        DetectedObject mostCritical = findMostCriticalObject(objects);

        // Gerar avaliação de risco
        return generateRiskAssessment(mostCritical);
    }

    // Encontra o objeto mais crítico baseado em heurística que considera:
    // - Distância
    // - Posição na ROI (centro da tela tem mais peso)
    // - Classe do objeto (pessoas têm prioridade)
    private DetectedObject findMostCriticalObject(List<DetectedObject> objects) {
        DetectedObject mostCritical = null;
        float highestScore = Float.MIN_VALUE;

        for (DetectedObject obj : objects) {
            float score = calculateRiskScore(obj);

            Log.d(TAG, String.format("Objeto: %s, Distância: %.2f, Score: %.2f",
                    obj.getClassName(), obj.getDistance(), score));

            if (score > highestScore) {
                highestScore = score;
                mostCritical = obj;
            }
        }

        if (mostCritical != null) {
            Log.d(TAG, String.format("Objeto mais crítico: %s a %.2f metros",
                    mostCritical.getClassName(), mostCritical.getDistance()));
        }

        return mostCritical;
    }

    // Calcula score de risco usando heurística baseada em (ordem decrescente):
    // 1. Distância
    // 2. Posição no ROI central
    // 3. Prioridade da classe
    // 4. Confiança da detecção
    private float calculateRiskScore(DetectedObject obj) {
        // 1. Score de distância (inverso - quanto mais perto, maior o score)
        float distanceScore = 1.0f / (obj.getDistance() + 0.1f);

        // 2. Score de posição no ROI (centro da tela = mais crítico)
        float positionScore = calculatePositionScore(obj.getCenterX(), obj.getCenterY());

        // 3. Prioridade da classe
        Float priorityValue = classPriorities.get(obj.getClassName());
        float classPriority = (priorityValue != null) ? priorityValue : 1.0f;

        // 4. Confiança da detecção
        float confidenceScore = obj.getConfidence();

        // Score final combinado
        return (distanceScore * 5.0f) +
               (positionScore * 3.0f) +
               (classPriority * 2.0f) +
               confidenceScore;
    }

    // Calcula score baseado na posição do objeto na tela
    // Objetos no centro têm score maior
    private float calculatePositionScore(float x, float y) {
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;

        // Distância do centro (normalizada)
        float dx = (x - centerX) / (screenWidth / 2.0f);
        float dy = (y - centerY) / (screenHeight / 2.0f);
        float distanceFromCenter = (float) Math.sqrt(dx * dx + dy * dy);

        // Score decresce com a distância do centro (máximo 1.0 no centro)
        return Math.max(0, 1.0f - distanceFromCenter);
    }

    // Gera a avaliação de risco final com mensagens apropriadas
    private RiskAssessment generateRiskAssessment(DetectedObject obj) {
        float distance = obj.getDistance();
        RiskLevel level = determineRiskLevel(distance);
        String direction = determineDirection(obj.getCenterX());
        String message = generateMessage(level, direction, obj.getClassName());
        boolean shouldAlert = shouldTriggerAlert(level);

        Log.d(TAG, String.format("Nível de risco: %s", level));
        Log.d(TAG, String.format("Mensagem: %s", message));
        Log.d(TAG, String.format("Direção: %s", direction));

        return new RiskAssessment(obj, level, message, direction, shouldAlert);
    }

    // Determina o nível de risco baseado na distância
    private RiskLevel determineRiskLevel(float distance) {
        if (distance < DISTANCE_CRITICAL) {
            return RiskLevel.CRITICAL;
        } else if (distance < DISTANCE_HIGH) {
            return RiskLevel.HIGH;
        } else if (distance < DISTANCE_MEDIUM) {
            return RiskLevel.MEDIUM;
        } else if (distance < DISTANCE_LOW) {
            return RiskLevel.LOW;
        }
        return RiskLevel.SAFE;
    }

    // Determina a direção do objeto em relação ao usuário
    private String determineDirection(float centerX) {
        float leftThird = 0.3f;
        float rightThird = 0.6f;

        if (centerX < leftThird) {
            return "esquerda";
        } else if (centerX > rightThird) {
            return "direita";
        } else {
            return "frente";
        }
    }

    // Gera mensagem apropriada baseada no nível de risco e direção
    private String generateMessage(RiskLevel level, String direction, String className) {
        switch (level) {
            case CRITICAL:
                return "Pare! Obstáculo muito próximo";
            case HIGH:
                if (direction.equals("frente")) {
                    return "Cuidado! Obstáculo à frente";
                } else {
                    return "Cuidado! Obstáculo à " + direction;
                }
            case MEDIUM:
                if (!direction.equals("frente")) {
                    return "Atenção à " + direction;
                }
                return "Continue com cuidado";
            case LOW:
                return "Siga em frente com atenção";
            default:
                return "Siga em frente";
        }
    }

    // Verifica se deve disparar alerta (com rate limiting)
    private boolean shouldTriggerAlert(RiskLevel level) {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAlert = currentTime - lastAlertTime;

        boolean shouldAlert = false;

        if (level == RiskLevel.CRITICAL || level == RiskLevel.HIGH) {
            // Alertas críticos têm cooldown menor
            if (timeSinceLastAlert >= ALERT_COOLDOWN_MS) {
                shouldAlert = true;
                lastAlertTime = currentTime;
            } else {
                Log.d(TAG, "Alerta suprimido (cooldown ativo)");
            }
        } else if (level == RiskLevel.MEDIUM) {
            // Narrações normais têm cooldown maior
            if (timeSinceLastAlert >= NARRATION_COOLDOWN_MS) {
                shouldAlert = true;
                lastAlertTime = currentTime;
            } else {
                Log.d(TAG, "Narração suprimida (cooldown ativo)");
            }
        }

        return shouldAlert;
    }

    // Cria aviso para parede próxima
    private RiskAssessment createWallWarning(float wallDistance) {
        RiskLevel level = wallDistance < DISTANCE_CRITICAL
            ? RiskLevel.CRITICAL
            : RiskLevel.HIGH;

        String message = level == RiskLevel.CRITICAL
            ? "Pare! Parede à frente"
            : "Atenção, parede próxima";

        boolean shouldAlert = shouldTriggerAlert(level);

        Log.d(TAG, String.format("Parede detectada a %.2f metros", wallDistance));

        return new RiskAssessment(null, level, message, "frente", shouldAlert);
    }

    public void resetCooldown() {
        lastAlertTime = 0;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }
}