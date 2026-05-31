package com.aegispay.user.config;

import com.aegispay.user.config.UserServiceProperties.AiPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableJpaAuditing
@EnableAsync
@EnableConfigurationProperties(UserServiceProperties.class)
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestClient aiPlatformRestClient(UserServiceProperties props) {
        AiPlatform ai = props.getAiPlatform();
        return RestClient.builder()
                .baseUrl(ai.getBaseUrl())
                .build();
    }
}
