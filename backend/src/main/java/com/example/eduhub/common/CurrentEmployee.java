package com.example.eduhub.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 사원의 사번. 토큰의 {@code sub}에서 읽는다(D-30, {@link CurrentEmployeeArgumentResolver}).
 *
 * <p>클라이언트가 값을 정할 수 없다. OpenAPI에는 파라미터로 나타나지 않도록 {@code @Parameter(hidden = true)}와 함께 쓴다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentEmployee {}
