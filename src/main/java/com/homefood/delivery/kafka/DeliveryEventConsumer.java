package com.homefood.delivery.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefood.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventConsumer {

    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.confirmed", groupId = "delivery-service")
    public void handleOrderConfirmed(@Payload String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            UUID orderId = UUID.fromString(node.get("orderId").asText());
            String city = node.has("deliveryCity") ? node.get("deliveryCity").asText() : "Bangalore";
            String pincode = node.has("deliveryPincode") ? node.get("deliveryPincode").asText() : "";
            String address = node.has("deliveryAddress") ? node.get("deliveryAddress").asText() : "";
            double lat = node.has("deliveryLat") ? node.get("deliveryLat").asDouble() : 0.0;
            double lng = node.has("deliveryLng") ? node.get("deliveryLng").asDouble() : 0.0;

            deliveryService.createAndAssignDelivery(orderId, address, city, pincode, lat, lng);
            log.info("Delivery initiated for confirmed order: {}", orderId);
        } catch (Exception e) {
            log.error("Error handling order.confirmed event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order.cancelled", groupId = "delivery-service")
    public void handleOrderCancelled(@Payload String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            UUID orderId = UUID.fromString(node.get("orderId").asText());
            deliveryService.cancelDelivery(orderId);
            log.info("Delivery cancelled for order: {}", orderId);
        } catch (Exception e) {
            log.error("Error handling order.cancelled event: {}", e.getMessage(), e);
        }
    }
}
