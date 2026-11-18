package com.example.eduhub.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자(교육 담당자)의 사번. {@code X-Employee-Id} 헤더의 사번이 관리자 목록({@code eduhub.admin.employee-ids})에 없으면
 * 403이다(D-29).
 *
 * <p>인증(D-11) 전에는 클라이언트가 헤더 값을 마음대로 보낼 수 있어 관리자 사번 위조 위험(FR-04 최종 검토 W2)이 남는다. 인증 도입 때 인증 정보로 바꾼다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentAdmin {}
