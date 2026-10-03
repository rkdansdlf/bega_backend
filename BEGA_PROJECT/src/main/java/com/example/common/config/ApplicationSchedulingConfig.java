package com.example.common.config;

import com.example.common.readonly.ReadOnlyVerificationPolicy;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile("!" + ReadOnlyVerificationPolicy.PROFILE)
@EnableScheduling
public class ApplicationSchedulingConfig {
}
