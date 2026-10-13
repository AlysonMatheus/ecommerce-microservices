package com.portfolio.inventory.execption;

public class InsufficientStockException extends RuntimeException {
  public InsufficientStockException(String message) {
    super(message);
  }
}
