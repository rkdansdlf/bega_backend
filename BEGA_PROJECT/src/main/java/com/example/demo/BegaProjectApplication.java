package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing; // ⭐️ 추가: Auditing 기능을 위한 임포트

@EnableJpaAuditing // ⭐️ 추가: JPA Auditing 활성화
@SpringBootApplication
public class BegaProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(BegaProjectApplication.class, args);
	}

}
