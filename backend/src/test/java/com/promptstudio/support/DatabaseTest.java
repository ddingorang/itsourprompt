package com.promptstudio.support;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class DatabaseTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void 테이블을_비운다() {
        dsl.execute("TRUNCATE idempotency_record, turn_file_change, attempt_turn, attempt_file, attempt, problem_file, problem, sync_state, users RESTART IDENTITY CASCADE");
    }
}
