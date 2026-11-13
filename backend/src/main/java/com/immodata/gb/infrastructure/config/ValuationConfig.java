package com.immodata.gb.infrastructure.config;

import com.immodata.gb.domain.valuation.ValuationCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ValuationConfig {

    @Bean
    public ValuationCalculator valuationCalculator() {
        return new ValuationCalculator();
    }
}