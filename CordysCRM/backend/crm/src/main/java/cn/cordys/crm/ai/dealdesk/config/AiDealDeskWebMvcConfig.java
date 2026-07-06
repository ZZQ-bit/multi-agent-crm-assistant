package cn.cordys.crm.ai.dealdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AiDealDeskWebMvcConfig implements WebMvcConfigurer {

    @Value("${ai.deal-desk.stream-timeout-ms:300000}")
    private long streamTimeoutMs;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(streamTimeoutMs);
    }
}
