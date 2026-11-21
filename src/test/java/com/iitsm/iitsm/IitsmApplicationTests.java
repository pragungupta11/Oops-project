package com.iitsm.iitsm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
// Never touch the real demo database (data/iitsm) from a test.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:applicationtest")
class IitsmApplicationTests {

	@Test
	void contextLoads() {
	}

}
