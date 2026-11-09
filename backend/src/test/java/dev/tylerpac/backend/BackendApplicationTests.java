package dev.tylerpac.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests {

	// It calls Stripe with a real key at startup
	@org.springframework.test.context.bean.override.mockito.MockitoBean
	private dev.tylerpac.backend.config.StripeStartupValidator stripeStartupValidator;

	@Test
	void contextLoads() {
	}

}
