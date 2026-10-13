# Eventos

Exchange: `orders.exchange` (topic)

| Evento          | Routing key       | Produtor          | Consumidores                          |
|-----------------|-------------------|-------------------|---------------------------------------|
| OrderCreated    | `order.created`   | order-service     | inventory-service                     |
| StockReserved   | `stock.reserved`  | inventory-service | order-service, notification-service   |
| StockRejected   | `stock.rejected`  | inventory-service | order-service, notification-service   |

## Payloads (JSON)

OrderCreated:
{ "eventId": "uuid", "orderId": "uuid", "customerId": "uuid",
  "items": [ { "productId": "uuid", "quantity": 2 } ] }

StockReserved / StockRejected:
{ "eventId": "uuid", "orderId": "uuid", "reason": "opcional (só no Rejected)" }

## Status do pedido
PENDING -> CONFIRMED (StockReserved) | CANCELLED (StockRejected)

## Regras
- Consumidores são idempotentes (tabela `processed_events`).
- Falhas repetidas vão para Dead Letter Queue.
