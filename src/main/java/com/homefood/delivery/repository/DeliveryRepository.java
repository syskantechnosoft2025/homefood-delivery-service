package com.homefood.delivery.repository;

import com.homefood.delivery.entity.Delivery;
import com.homefood.delivery.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    Optional<Delivery> findByOrderId(UUID orderId);

    List<Delivery> findByAgentIdAndStatusIn(UUID agentId, List<DeliveryStatus> statuses);

    List<Delivery> findByStatusAndAssignmentAttemptsLessThan(DeliveryStatus status, int maxAttempts);

    List<Delivery> findByAgentIdOrderByCreatedAtDesc(UUID agentId);
}
