package cn.cordys.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShiroFilterTest {

    @Test
    void shouldAllowAnonymousAiDealDeskDemoEndpoints() {
        Map<String, String> chain = ShiroFilter.loadBaseFilterChain();

        assertEquals("anon", chain.get("/ai/deal-desk/**"));
        assertEquals("anon", chain.get("/front/ai/deal-desk/**"));
    }
}
