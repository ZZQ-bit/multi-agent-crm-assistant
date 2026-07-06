package cn.cordys.crm.agent;

import cn.cordys.crm.ai.dealdesk.config.AiDealDeskWebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiDealDeskWebMvcConfigTest {

    @Test
    void shouldConfigureLongStreamingTimeoutForDealDesk() throws Exception {
        AiDealDeskWebMvcConfig config = new AiDealDeskWebMvcConfig();
        ReflectionTestUtils.setField(config, "streamTimeoutMs", 600000L);

        AsyncSupportConfigurer configurer = new AsyncSupportConfigurer();
        config.configureAsyncSupport(configurer);

        Field timeoutField = AsyncSupportConfigurer.class.getDeclaredField("timeout");
        timeoutField.setAccessible(true);
        assertEquals(600000L, timeoutField.get(configurer));
    }
}
