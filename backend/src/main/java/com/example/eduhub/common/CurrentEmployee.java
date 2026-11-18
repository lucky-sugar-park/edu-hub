package com.example.eduhub.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 사원의 사번. 인증(D-11) 전에는 {@code X-Employee-Id} 헤더(D-21)에서 읽는다.
 *
 * <p>인증 전에는 클라이언트가 헤더 값을 마음대로 보낼 수 있어 위조 위험(FR-04 최종 검토 W2)이 남는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentEmployee {}
