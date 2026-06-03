# Delivery Service

Manages delivery agent assignment, real-time geo tracking via Redis GEO, and delivery lifecycle.

## Features
- Redis GEO for finding nearest available delivery agents
- WebSocket real-time location updates (`/topic/delivery/{orderId}`)
- Automatic agent assignment on order confirmation
- Agent availability management (AVAILABLE/BUSY status in Redis)

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/deliveries/order/{orderId} | Get delivery |
| PATCH | /api/v1/deliveries/order/{orderId}/status | Update status |
| POST | /api/v1/deliveries/agents/{agentId}/location | Update location |
| POST | /api/v1/deliveries/agents/{agentId}/register | Register agent |
