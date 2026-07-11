package dev.copilot.demo.web;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.copilot.demo.fault.FaultState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ShopController.class, FaultController.class})
@Import({FaultState.class, ShopAndFaultControllerTest.TestBeans.class})
class ShopAndFaultControllerTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FaultState faults;

    @AfterEach
    void clearFaults() {
        // FaultState is a shared singleton across test methods; reset so injected faults
        // (e.g. 100% error rate) do not leak into other tests.
        faults.reset();
    }

    @Test
    void inventoryReturnsCatalog() throws Exception {
        mvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void checkoutConfirmsOrder() throws Exception {
        mvc.perform(post("/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.orderId").isNotEmpty());
    }

    @Test
    void latencyFaultIsAccepted() throws Exception {
        mvc.perform(post("/faults/latency").param("ms", "1500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.injectedLatencyMs").value(1500));
    }

    @Test
    void errorRateFaultIsClampedToRange() throws Exception {
        mvc.perform(post("/faults/error-rate").param("pct", "250"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorRatePct").value(100));
    }

    @Test
    void memoryLeakReportsRetainedBytes() throws Exception {
        mvc.perform(post("/faults/memory-leak").param("mb", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retainedBytes").value(greaterThanOrEqualTo(1_048_576)));
    }
}
