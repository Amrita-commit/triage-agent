package dev.copilot.demo;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Demo "shop" microservice. Serves the {@code /checkout} and {@code /inventory} business endpoints
 * and a family of {@code /faults/*} endpoints used to deliberately inject infrastructure and
 * application faults that the copilot agents must diagnose.
 */
@SpringBootApplication
public class DemoAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoAppApplication.class, args);
    }

    /** Enables the {@link io.micrometer.core.annotation.Timed} annotation on controller methods. */
    @Bean
    TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
