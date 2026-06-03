package com.homefood.delivery.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homefood.delivery.entity.Delivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishDeliveryAssigned(Delivery delivery) {
        try {
            String event = objectMapper.writeValueAsString(Map.of(
                    "type", "DELIVERY_ASSIGNED",
                    "orderId", delivery.getOrderId().toString(),
                    "deliveryId", delivery.getId().toString(),
                    "agentId", delivery.getAgentId().toString(),
                    "estimatedMinutes", delivery.getEstimatedMinutes() != null ? delivery.getEstimatedMinutes() : 45
            ));
            kafkaTemplate.send("delivery.assigned", delivery.getOrderId().toString(), event);
            log.info("Published delivery.assigned for order: {}", delivery.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish delivery.assigned: {}", e.getMessage());
        }
    }

    public void publishDeliveryCompleted(Delivery delivery) {
        try {
            String event = objectMapper.writeValueAsString(Map.of(
                    "type", "DELIVERY_COMPLETED",
                    "orderId", delivery.getOrderId().toString(),
                    "deliveryId", delivery.getId().toString(),
                    "agentId", delivery.getAgentId() != null ? delivery.getAgentId().toString() : ""
            ));
            kafkaTemplate.send("delivery.completed", delivery.getOrderId().toString(), event);
            log.info("Published delivery.completed for order: {}", delivery.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish delivery.completed: {}", e.getMessage());
        }
    }
}
