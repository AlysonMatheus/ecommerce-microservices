package com.portfolio.order.controller;

import com.portfolio.order.dtos.request.CreateOrderRequest;
import com.portfolio.order.dtos.response.CreateOrderResponse;
import com.portfolio.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(  "/order")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    @PostMapping( "/create")
    public ResponseEntity<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest dto){
        var create = orderService.create(dto);
        return ResponseEntity.ok(create);
    }
    @GetMapping("/{id}")
    public ResponseEntity<CreateOrderResponse>findById(@PathVariable UUID id){
        return ResponseEntity.ok(orderService.findById(id));

    }
    @GetMapping
    public ResponseEntity<List<CreateOrderResponse>>listAll(){
        var list = orderService.listAll();
        return ResponseEntity.ok(list);
    }

}
