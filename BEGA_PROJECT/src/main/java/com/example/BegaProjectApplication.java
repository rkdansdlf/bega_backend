package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing; // 추가: Auditing 기능을 위한 임포트
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaAuditing // 추가: JPA Auditing 활성화
@SpringBootApplication(scanBasePackages = "com.example") // 최상단 패키지로 스캔 범위 확대
@EnableJpaRepositories(basePackages = "com.example") // 모든 리포지토리 스캔 (demo.repo + cheerboard.repo)
@EntityScan(basePackages = "com.example") // 모든 엔티티 스캔
public class BegaProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(BegaProjectApplication.class, args);
	}

}
