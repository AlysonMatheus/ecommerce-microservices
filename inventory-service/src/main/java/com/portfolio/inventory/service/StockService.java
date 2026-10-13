package com.portfolio.inventory.service;

import com.portfolio.inventory.dtos.request.StockRequest;
import com.portfolio.inventory.dtos.response.StockResponse;
import com.portfolio.inventory.entity.ProcessedEvent;
import com.portfolio.inventory.entity.Stock;
import com.portfolio.inventory.execption.StockAlreadyExistsException;
import com.portfolio.inventory.execption.StockNotFoundException;
import com.portfolio.inventory.messaging.event.OrderCreatedEvent;
import com.portfolio.inventory.messaging.event.StockRejectedEvent;
import com.portfolio.inventory.messaging.event.StockReservedEvent;
import com.portfolio.inventory.messaging.publisher.StockEventPublisher;
import com.portfolio.inventory.repository.ProcessedEventRepository;
import com.portfolio.inventory.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final StockEventPublisher publisher;

    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        Map<UUID, Integer> requested = event.items().stream()
                .collect(Collectors.toMap(
                        OrderCreatedEvent.Item::productId,
                        OrderCreatedEvent.Item::quantity,
                        Integer::sum,
                        TreeMap::new));

        Map<UUID, Stock> stocks = stockRepository.findAllByIdWithLock(requested.keySet()).stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Evento {} já processado, ignorando", event.eventId());
            return;
        }

        String reason = findRejectionReason(requested, stocks);

        if (reason == null) {
            requested.forEach((productId, quantity) -> stocks.get(productId).reserve(quantity));
            processedEventRepository.save(new ProcessedEvent(event.eventId()));
            publisher.publishReserved(StockReservedEvent.from(event.orderId()));
            log.info("Estoque reservado para o pedido {}", event.orderId());
        } else {
            processedEventRepository.save(new ProcessedEvent(event.eventId()));
            publisher.publishRejected(StockRejectedEvent.from(event.orderId(), reason));
            log.info("Pedido {} rejeitado: {}", event.orderId(), reason);
        }
    }

    private String findRejectionReason(Map<UUID, Integer> requested, Map<UUID, Stock> stocks) {
        for (Map.Entry<UUID, Integer> entry : requested.entrySet()) {
            Stock stock = stocks.get(entry.getKey());
            if (stock == null) {
                return "Produto não encontrado: " + entry.getKey();
            }
            if (stock.getAvailable() < entry.getValue()) {
                return "Estoque insuficiente para o produto " + entry.getKey();
            }
        }
        return null;
    }
    @Transactional
    public StockResponse create(StockRequest dto) {
        if (stockRepository.existsById(dto.productId())) {
            throw new StockAlreadyExistsException(dto.productId());
        }
        return new StockResponse(stockRepository.save(new Stock(dto)));
    }

    @Transactional(readOnly = true)
    public StockResponse findById(UUID productId) {
        return new StockResponse(stockRepository.findById(productId)
                .orElseThrow(() -> new StockNotFoundException(productId)));
    }

    @Transactional(readOnly = true)
    public List<StockResponse> listAll() {
        return stockRepository.findAll().stream()
                .map(StockResponse::new)
                .toList();
    }
}