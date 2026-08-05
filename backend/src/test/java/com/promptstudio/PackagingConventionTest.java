package com.promptstudio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모듈 구조가 말없이 흐트러지는 것을 막는다.
 *
 * <p>이 레포에는 표준 문서가 없어 규칙이 "다른 파일들이 하는 대로"에만 존재한다. 그런 규칙은 새 모듈이
 * 하나 들어올 때 조용히 깨지고, 리뷰가 잡지 못하면 그대로 굳는다. 컴파일이 잡아주지 않는 종류라
 * 테스트로 박아 둔다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다 — 패키지 배치는 빈 생성과 무관하고, 클래스패스 스캔만으로 충분하다.
 */
class PackagingConventionTest {

    private static final String ROOT_PACKAGE = "com.promptstudio";

    @Test
    void 영속성_타입은_모듈의_repository_패키지에_둔다() {
        assertThat(persistenceTypes())
                .isNotEmpty()
                .allSatisfy(className -> assertThat(className)
                        .as("%s는 영속성 타입인데 <모듈>.repository 패키지에 있지 않다", className)
                        .contains(".repository."));
    }

    /**
     * {@code @Repository}가 붙은 구현체와, 이름이 Repository로 끝나는 타입을 함께 본다.
     *
     * <p>Spring Data 인터페이스(AttemptJpaRepository 등)에는 애너테이션이 없어 애너테이션만 보면
     * 그냥 지나친다 — 가장 흔할 위반이 정작 안 걸리는 셈이라 이름 규칙을 함께 건다.
     */
    private Set<String> persistenceTypes() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Repository$")));

        return scanner.findCandidateComponents(ROOT_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .collect(Collectors.toSet());
    }
}
