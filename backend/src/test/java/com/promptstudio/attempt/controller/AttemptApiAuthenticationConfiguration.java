package com.promptstudio.attempt.controller;

import com.promptstudio.global.security.AppUserDetails;
import com.promptstudio.user.repository.UserRepository;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestConfiguration
class AttemptApiAuthenticationConfiguration {

    @Bean
    MockMvcBuilderCustomizer attemptOwnerAuthentication(UserRepository userRepository) {
        return builder -> builder.defaultRequest(get("/").with(request -> {
            AppUserDetails owner = new AppUserDetails(userRepository.findByUsername("_test_owner").orElseThrow());
            return SecurityMockMvcRequestPostProcessors.user(owner).postProcessRequest(request);
        }));
    }
}
