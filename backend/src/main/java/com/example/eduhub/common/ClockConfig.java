package com.example.eduhub.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시각 판단(교육 시작 여부 등)을 테스트에서 바꿀 수 있도록 Clock을 빈으로 둔다. */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
