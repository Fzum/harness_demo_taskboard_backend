package com.example.taskboard;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The clock the application reads the current date from; tests can replace it. */
@Configuration
class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemDefaultZone();
  }
}
