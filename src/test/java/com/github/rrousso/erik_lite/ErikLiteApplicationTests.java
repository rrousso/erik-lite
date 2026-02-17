package com.github.rrousso.erik_lite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ErikLiteApplicationTests {

	@Test
	void contextLoads() {
		// Verifies Spring context boots with H2 test database
	}

}