package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 추가: 스케줄링 활성화
import org.springframework.data.jpa.repository.config.EnableJpaAuditing; // 추가: Auditing 기능을 위한 임포트

@EnableScheduling // 추가: 스케줄링 기능 활성화
@EnableJpaAuditing // 추가: JPA Auditing 활성화
@SpringBootApplication(scanBasePackages = "com.example") // 최상단 패키지로 스캔 범위 확대
@org.springframework.data.web.config.EnableSpringDataWebSupport(pageSerializationMode = org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class BegaProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(BegaProjectApplication.class, args);
	}

}
