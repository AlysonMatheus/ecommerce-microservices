# E-commerce Microservices

Sistema de pedidos com 3 serviços em Java 21 + Spring Boot 3, comunicação assíncrona via RabbitMQ.

| Serviço               | Porta | Responsabilidade                          |
|-----------------------|-------|-------------------------------------------|
| order-service         | 8081  | Cria e consulta pedidos                   |
| inventory-service     | 8082  | Reserva estoque                           |
| notification-service  | 8083  | Notifica o cliente sobre o status         |

## Rodando
    docker compose up --build

- Swagger: http://localhost:8081/swagger-ui.html
- RabbitMQ: http://localhost:15672 (guest/guest)

## Documentação
- [Eventos](docs/events.md)

## TODO
- [ ] CRUD de pedidos + testes
- [ ] Publicação de OrderCreated (Outbox)
- [ ] Reserva de estoque + idempotência
- [ ] Notificações
- [ ] DLQ, retry, métricas, diagrama de arquitetura
