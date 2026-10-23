package com.example.log_ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestContainersConfiguration.class)
class LogIngestionApplicationTests {

	@Test
	void contextLoads() {
	}

}
