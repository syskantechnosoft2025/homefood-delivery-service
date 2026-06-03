package com.homefood.delivery.controller;

import com.homefood.delivery.entity.Delivery;
import com.homefood.delivery.entity.DeliveryStatus;
import com.homefood.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<Delivery> getDelivery(@PathVariable UUID orderId) {
        return ResponseEntity.ok(deliveryService.getDeliveryByOrderId(orderId));
    }

    @PatchMapping("/order/{orderId}/status")
    public ResponseEntity<Delivery> updateStatus(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> body) {
        DeliveryStatus status = DeliveryStatus.valueOf(body.get("status"));
        String notes = body.getOrDefault("notes", "");
        return ResponseEntity.ok(deliveryService.updateDeliveryStatus(orderId, status, notes));
    }

    @PostMapping("/agents/{agentId}/location")
    public ResponseEntity<Void> updateAgentLocation(
            @PathVariable UUID agentId,
            @RequestBody Map<String, Double> body) {
        deliveryService.updateAgentLocation(agentId, body.get("latitude"), body.get("longitude"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/agents/{agentId}/register")
    public ResponseEntity<Map<String, String>> registerAgent(
            @PathVariable UUID agentId,
            @RequestBody Map<String, Double> body) {
        deliveryService.registerAgent(agentId, body.get("latitude"), body.get("longitude"));
        return ResponseEntity.ok(Map.of("message", "Agent registered successfully"));
    }
}
