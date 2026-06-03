package com.homefood.delivery.service;

import com.homefood.delivery.entity.Delivery;
import com.homefood.delivery.entity.DeliveryStatus;
import com.homefood.delivery.kafka.DeliveryEventProducer;
import com.homefood.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryService Unit Tests")
class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryEventProducer deliveryEventProducer;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private DeliveryService deliveryService;

    private UUID orderId;
    private UUID agentId;
    private Delivery testDelivery;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        agentId = UUID.randomUUID();

        testDelivery = Delivery.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .agentId(agentId)
                .deliveryAddress("123 Main St")
                .deliveryCity("Bangalore")
                .deliveryPincode("560001")
                .status(DeliveryStatus.AGENT_ASSIGNED)
                .estimatedMinutes(45)
                .assignmentAttempts(0)
                .build();
    }

    @Test
    @DisplayName("Should create delivery and attempt assignment")
    void shouldCreateDelivery() {
        when(deliveryRepository.save(any(Delivery.class))).thenReturn(testDelivery);
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.radius(anyString(), any(), any(), any())).thenReturn(null);

        Delivery result = deliveryService.createAndAssignDelivery(orderId, "123 Main St", "Bangalore", "560001", 0, 0);

        assertThat(result).isNotNull();
        verify(deliveryRepository, atLeastOnce()).save(any(Delivery.class));
    }

    @Test
    @DisplayName("Should update delivery status to DELIVERED")
    void shouldUpdateStatusToDelivered() {
        when(deliveryRepository.findByOrderId(orderId)).thenReturn(Optional.of(testDelivery));
        when(deliveryRepository.save(any())).thenReturn(testDelivery);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), anyLong(), any())).thenReturn(null);
        doNothing().when(deliveryEventProducer).publishDeliveryCompleted(any());
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        deliveryService.updateDeliveryStatus(orderId, DeliveryStatus.DELIVERED, "Delivered on time");

        verify(deliveryEventProducer).publishDeliveryCompleted(any());
    }

    @Test
    @DisplayName("Should register agent location in Redis Geo")
    void shouldRegisterAgentLocation() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(geoOperations.add(anyString(), any(), anyString())).thenReturn(1L);
        when(valueOperations.set(anyString(), anyString(), anyLong(), any())).thenReturn(null);

        deliveryService.registerAgent(agentId, 12.9716, 77.5946);

        verify(geoOperations).add(eq("delivery:agents:geo"), any(), eq(agentId.toString()));
        verify(valueOperations).set(contains(agentId.toString()), eq("AVAILABLE"), anyLong(), any());
    }
}
