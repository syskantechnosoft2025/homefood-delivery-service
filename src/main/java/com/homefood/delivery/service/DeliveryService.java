package com.homefood.delivery.service;

import com.homefood.delivery.entity.Delivery;
import com.homefood.delivery.entity.DeliveryStatus;
import com.homefood.delivery.kafka.DeliveryEventProducer;
import com.homefood.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventProducer deliveryEventProducer;
    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String GEO_KEY = "delivery:agents:geo";
    private static final String AGENT_STATUS_KEY = "delivery:agent:status:";

    @Transactional
    public Delivery createAndAssignDelivery(UUID orderId, String address, String city,
                                             String pincode, double lat, double lng) {
        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .deliveryAddress(address)
                .deliveryCity(city)
                .deliveryPincode(pincode)
                .deliveryLatitude(lat)
                .deliveryLongitude(lng)
                .status(DeliveryStatus.PENDING_ASSIGNMENT)
                .assignmentAttempts(0)
                .build();

        delivery = deliveryRepository.save(delivery);

        // Try to assign an agent
        assignAgentToDelivery(delivery);
        return delivery;
    }

    @Transactional
    public void assignAgentToDelivery(Delivery delivery) {
        try {
            // Find nearest available agent using Redis GEO
            GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();

            if (delivery.getDeliveryLatitude() != null && delivery.getDeliveryLongitude() != null
                    && delivery.getDeliveryLatitude() != 0) {
                Point deliveryPoint = new Point(delivery.getDeliveryLongitude(), delivery.getDeliveryLatitude());
                GeoResults<RedisGeoCommands.GeoLocation<String>> nearbyAgents = geoOps.radius(
                        GEO_KEY, deliveryPoint,
                        new Distance(10, Metrics.KILOMETERS),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .sortAscending()
                                .limit(5)
                );

                if (nearbyAgents != null && !nearbyAgents.getContent().isEmpty()) {
                    for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : nearbyAgents.getContent()) {
                        String agentIdStr = result.getContent().getName();
                        String agentStatus = redisTemplate.opsForValue().get(AGENT_STATUS_KEY + agentIdStr);
                        if ("AVAILABLE".equals(agentStatus)) {
                            UUID agentId = UUID.fromString(agentIdStr);
                            delivery.setAgentId(agentId);
                            delivery.setStatus(DeliveryStatus.AGENT_ASSIGNED);
                            delivery.setAssignedAt(LocalDateTime.now());
                            delivery.setDistanceKm(result.getDistance().getValue());
                            delivery.setEstimatedMinutes((int) (result.getDistance().getValue() * 3 + 10));
                            deliveryRepository.save(delivery);

                            // Mark agent as busy
                            redisTemplate.opsForValue().set(AGENT_STATUS_KEY + agentIdStr, "BUSY", 2, TimeUnit.HOURS);
                            deliveryEventProducer.publishDeliveryAssigned(delivery);

                            // Push real-time update
                            messagingTemplate.convertAndSend("/topic/delivery/" + delivery.getOrderId(),
                                    Map.of("status", "AGENT_ASSIGNED", "agentId", agentId,
                                           "estimatedMinutes", delivery.getEstimatedMinutes()));
                            log.info("Agent {} assigned to delivery {}", agentId, delivery.getId());
                            return;
                        }
                    }
                }
            }

            // Fallback: assign any available agent (no geo data)
            delivery.setAssignmentAttempts(delivery.getAssignmentAttempts() + 1);
            delivery.setEstimatedMinutes(45);
            deliveryRepository.save(delivery);
            log.warn("No agent found via geo for order {}. Attempt {}", delivery.getOrderId(), delivery.getAssignmentAttempts());

        } catch (Exception e) {
            log.error("Error assigning delivery agent: {}", e.getMessage(), e);
            delivery.setAssignmentAttempts(delivery.getAssignmentAttempts() + 1);
            deliveryRepository.save(delivery);
        }
    }

    @Transactional
    public void updateAgentLocation(UUID agentId, double latitude, double longitude) {
        GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();
        geoOps.add(GEO_KEY, new Point(longitude, latitude), agentId.toString());
        redisTemplate.expire(GEO_KEY, 1, TimeUnit.HOURS);

        // Find active deliveries for this agent and push location update
        List<Delivery> activeDeliveries = deliveryRepository.findByAgentIdAndStatusIn(agentId,
                List.of(DeliveryStatus.AGENT_ASSIGNED, DeliveryStatus.HEADING_TO_SELLER,
                        DeliveryStatus.AT_SELLER, DeliveryStatus.FOOD_PICKED_UP, DeliveryStatus.OUT_FOR_DELIVERY));

        for (Delivery delivery : activeDeliveries) {
            messagingTemplate.convertAndSend("/topic/delivery/" + delivery.getOrderId(),
                    Map.of("type", "LOCATION_UPDATE", "latitude", latitude, "longitude", longitude));
        }

        log.debug("Location updated for agent: {}", agentId);
    }

    @Transactional
    public void registerAgent(UUID agentId, double latitude, double longitude) {
        GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();
        geoOps.add(GEO_KEY, new Point(longitude, latitude), agentId.toString());
        redisTemplate.opsForValue().set(AGENT_STATUS_KEY + agentId, "AVAILABLE", 8, TimeUnit.HOURS);
        log.info("Agent {} registered at {},{}", agentId, latitude, longitude);
    }

    @Transactional
    public Delivery updateDeliveryStatus(UUID orderId, DeliveryStatus newStatus, String notes) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + orderId));

        delivery.setStatus(newStatus);
        delivery.setAgentNotes(notes);

        if (newStatus == DeliveryStatus.FOOD_PICKED_UP) {
            delivery.setPickedUpAt(LocalDateTime.now());
        } else if (newStatus == DeliveryStatus.DELIVERED) {
            delivery.setDeliveredAt(LocalDateTime.now());
            if (delivery.getAgentId() != null) {
                redisTemplate.opsForValue().set(AGENT_STATUS_KEY + delivery.getAgentId(), "AVAILABLE", 8, TimeUnit.HOURS);
            }
            deliveryEventProducer.publishDeliveryCompleted(delivery);
        }

        delivery = deliveryRepository.save(delivery);

        messagingTemplate.convertAndSend("/topic/delivery/" + orderId,
                Map.of("type", "STATUS_UPDATE", "status", newStatus.name()));
        return delivery;
    }

    @Transactional
    public void cancelDelivery(UUID orderId) {
        deliveryRepository.findByOrderId(orderId).ifPresent(delivery -> {
            delivery.setStatus(DeliveryStatus.CANCELLED);
            if (delivery.getAgentId() != null) {
                redisTemplate.opsForValue().set(AGENT_STATUS_KEY + delivery.getAgentId(), "AVAILABLE", 8, TimeUnit.HOURS);
            }
            deliveryRepository.save(delivery);
            log.info("Delivery cancelled for order: {}", orderId);
        });
    }

    public Delivery getDeliveryByOrderId(UUID orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found: " + orderId));
    }
}
