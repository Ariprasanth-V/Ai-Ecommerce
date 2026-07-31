package com.yourname.aicommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Provides a safe test secret so the app context starts without JWT_SECRET env var.
        // This value is only used in tests — never in any deployed environment.
        "app.jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RpbmctMzI="
})
class AiCommerceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts successfully.
    }
}
