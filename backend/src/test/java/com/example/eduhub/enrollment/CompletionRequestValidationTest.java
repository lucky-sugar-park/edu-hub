package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CompletionRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private boolean valid(List<String> ids) {
    return validator.validate(new CompletionRequest(ids)).isEmpty();
  }

  @Test
  void rejectsEmptyAndNullList() {
    assertThat(valid(List.of())).isFalse();
    assertThat(valid(null)).isFalse();
  }

  @Test
  void rejectsMoreThan200Elements() {
    assertThat(valid(ids(201))).isFalse();
  }

  @Test
  void acceptsExactly200Elements() {
    assertThat(valid(ids(200))).isTrue();
  }

  @Test
  void rejectsNullElement() {
    assertThat(valid(Arrays.asList("E1", null))).isFalse();
  }

  @Test
  void rejectsBlankElement() {
    assertThat(valid(List.of("E1", " "))).isFalse();
    assertThat(valid(List.of(""))).isFalse();
  }

  @Test
  void rejectsElementLongerThan20() {
    assertThat(valid(List.of("A".repeat(21)))).isFalse();
  }

  @Test
  void acceptsElementOfExactly20() {
    assertThat(valid(Collections.singletonList("A".repeat(20)))).isTrue();
  }

  private static List<String> ids(int count) {
    return new ArrayList<>(IntStream.range(0, count).mapToObj(i -> "E" + i).toList());
  }
}
