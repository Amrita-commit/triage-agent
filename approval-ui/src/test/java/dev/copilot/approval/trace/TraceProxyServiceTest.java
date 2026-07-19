package dev.copilot.approval.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraceProxyServiceTest {

    private HttpServer server;
    private TraceProxyService proxy;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/traces/inc-1", ex -> {
            byte[] body = "{\"traceId\":\"inc-1\",\"agent\":\"diagnostics-agent\",\"toolCalls\":[]}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        proxy = new TraceProxyService(Map.of("diagnostics", base));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void fetchesTraceFromWhitelistedSource() {
        String json = proxy.fetch("diagnostics", "inc-1");
        assertThat(json).contains("\"agent\":\"diagnostics-agent\"").contains("inc-1");
        assertThat(proxy.sources()).containsExactly("diagnostics");
    }

    @Test
    void rejectsUnknownSource() {
        assertThatThrownBy(() -> proxy.fetch("mystery", "inc-1"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
