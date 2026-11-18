package com.portfolio.inventory.containers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ContainersConfig.class)
public class ContextLoadsit {
    @Test
    void contextLoads(){

    }
}
