package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrollmentSeatCounterTest {

  @Mock private EnrollmentRepository enrollmentRepository;

  @InjectMocks private EnrollmentSeatCounter counter;

  @Test
  void returnsEmptyMapWithoutQueryWhenNoSessionIds() {
    assertThat(counter.countConfirmedBySessionIds(List.of())).isEmpty();

    verifyNoInteractions(enrollmentRepository);
  }

  @Test
  void mapsCountsBySessionId() {
    List<SessionConfirmedCount> counts = List.of(count(1L, 4), count(3L, 7));
    given(
            enrollmentRepository.countBySessionIdsAndStatus(
                List.of(1L, 2L, 3L), EnrollmentStatus.CONFIRMED))
        .willReturn(counts);

    Map<Long, Long> result = counter.countConfirmedBySessionIds(List.of(1L, 2L, 3L));

    assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 4L, 3L, 7L));
  }

  private static SessionConfirmedCount count(Long sessionId, long confirmed) {
    SessionConfirmedCount count = mock(SessionConfirmedCount.class);
    given(count.getSessionId()).willReturn(sessionId);
    given(count.getConfirmed()).willReturn(confirmed);
    return count;
  }
}
