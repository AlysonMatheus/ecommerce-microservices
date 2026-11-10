package com.portfolio.order.containers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ContainersConfig.class)
public class ContextLoadsIT {
    @Test
    void contextLoads() {
    }
}
