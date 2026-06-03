package com.homefood.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deliveries", indexes = {
        @Index(name = "idx_delivery_order", columnList = "orderId"),
        @Index(name = "idx_delivery_agent", columnList = "agentId"),
        @Index(name = "idx_delivery_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column
    private UUID agentId;

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(nullable = false)
    private String deliveryCity;

    @Column(nullable = false)
    private String deliveryPincode;

    @Column
    private Double deliveryLatitude;

    @Column
    private Double deliveryLongitude;

    @Column
    private String pickupAddress;

    @Column
    private Double pickupLatitude;

    @Column
    private Double pickupLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column
    private Integer estimatedMinutes;

    @Column
    private Double distanceKm;

    @Column
    private LocalDateTime assignedAt;

    @Column
    private LocalDateTime pickedUpAt;

    @Column
    private LocalDateTime deliveredAt;

    @Column
    private LocalDateTime failedAt;

    @Column
    private String failureReason;

    @Column
    private String agentNotes;

    @Column
    private int assignmentAttempts;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
