package com.compartix.backend.controller;

import com.compartix.backend.service.IaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class MetricasIaController {

    private final IaService iaService;

    /** Métricas de evaluación del modelo (accuracy, precision, recall, F1 por intención). */
    @GetMapping("/metricas")
    public ResponseEntity<Map<String, Object>> metricas() {
        return ResponseEntity.ok(iaService.obtenerMetricas());
    }
}
