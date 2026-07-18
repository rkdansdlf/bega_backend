package com.example.support;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class HibernateStatisticsTestConfig {

    @Bean
    HibernatePropertiesCustomizer enableHibernateStatistics() {
        return properties -> properties.put("hibernate.generate_statistics", true);
    }
}
