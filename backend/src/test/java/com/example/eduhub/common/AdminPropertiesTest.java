package com.example.eduhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

// OS 환경 변수(EDUHUB_ADMIN_EMPLOYEE_IDS)가 yml 값을 덮을 수 있으므로 테스트에서 값을 고정한다.
@SpringBootTest(properties = "eduhub.admin.employee-ids=TEST-ADMIN")
@ActiveProfiles("test")
class AdminPropertiesTest {

  @Autowired private AdminProperties adminProperties;

  @Test
  void testContextPinsAdminListToTestAdmin() {
    assertThat(adminProperties.employeeIds()).containsExactly("TEST-ADMIN");
  }

  @Test
  void unsetPlaceholderBindsToEmptyList() {
    var env = new MockEnvironment();
    var source =
        new MapConfigurationPropertySource(
            Map.of("eduhub.admin.employee-ids", "${EDUHUB_ADMIN_EMPLOYEE_IDS:}"));
    var binder =
        new Binder(java.util.List.of(source), new PropertySourcesPlaceholdersResolver(env));

    AdminProperties bound = binder.bind("eduhub.admin", AdminProperties.class).get();

    assertThat(bound.employeeIds()).isEmpty();
  }

  private static AdminProperties bind(String value) {
    var source = new MapConfigurationPropertySource(Map.of("eduhub.admin.employee-ids", value));
    return new Binder(source).bind("eduhub.admin", AdminProperties.class).get();
  }

  @Test
  void emptyStringBindsToEmptyList() {
    assertThat(bind("").employeeIds()).isEmpty();
    assertThat(bind("").isAdmin("")).isFalse();
  }

  @Test
  void commaSeparatedValuesAreSplitAndBlankElementsDropped() {
    assertThat(bind("A1,, B2 , ").employeeIds()).containsExactly("A1", "B2");
  }

  @Test
  void nullListBecomesEmpty() {
    assertThat(new AdminProperties(null).employeeIds()).isEqualTo(List.of());
  }
}
