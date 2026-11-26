package com.example.demo.config;

import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryConfig {

	// 1. 定义一个 SessionRepository (这里用内存版，适合演示)
    @Bean
    public SessionRepository sessionRepository() {
        return InMemorySessionRepository.builder().build();
    }

    // 2. 用自动装配的 SessionService 来构建 SessionAdvisors
    @Bean
    public ChatClientBuilderCustomizer sessionAdvisorCustomizer(SessionService sessionService) {
        return builder -> builder.defaultAdvisors(
            SessionAdvisors.builder(sessionService).build()
        );
    }
}
