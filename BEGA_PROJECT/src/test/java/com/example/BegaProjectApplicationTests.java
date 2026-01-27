package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ORACLE_TESTS", matches = "true")
class BegaProjectApplicationTests {

	@Test
	void contextLoads() {
	}

}
