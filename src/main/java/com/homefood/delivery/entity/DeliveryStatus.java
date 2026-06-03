package com.homefood.delivery.entity;

public enum DeliveryStatus {
    PENDING_ASSIGNMENT,
    AGENT_ASSIGNED,
    HEADING_TO_SELLER,
    AT_SELLER,
    FOOD_PICKED_UP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    CANCELLED
}
