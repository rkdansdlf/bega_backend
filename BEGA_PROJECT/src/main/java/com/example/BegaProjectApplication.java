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
		// 배포 환경(Docker/로컬/CI)에 관계없이 JVM 기본 타임존을 KST로 고정.
		// JPA auditing 등 시스템 기본 타임존에 의존하는 빈이 뜨기 전에 실행되어야 함.
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(BegaProjectApplication.class, args);
	}

}
