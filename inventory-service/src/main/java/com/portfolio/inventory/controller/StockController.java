package com.portfolio.inventory.controller;

import com.portfolio.inventory.dtos.request.StockRequest;
import com.portfolio.inventory.dtos.response.StockResponse;
import com.portfolio.inventory.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping
    public ResponseEntity<StockResponse> create(@Valid @RequestBody StockRequest dto) {
        StockResponse created = stockService.create(dto);
        URI location = URI.create("/stock/" + created.productId());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> findById(@PathVariable UUID productId) {
        return ResponseEntity.ok(stockService.findById(productId));
    }

    @GetMapping
    public ResponseEntity<List<StockResponse>> listAll() {
        return ResponseEntity.ok(stockService.listAll());
    }
}