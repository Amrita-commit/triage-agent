package dev.copilot.core.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    @Test
    void rejectsInvertedRange() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new TimeRange(now, now.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lastOfProducesRequestedDuration() {
        TimeRange range = TimeRange.lastOf(Duration.ofMinutes(5));
        assertThat(range.duration()).isCloseTo(Duration.ofMinutes(5), Duration.ofSeconds(2));
    }
}
