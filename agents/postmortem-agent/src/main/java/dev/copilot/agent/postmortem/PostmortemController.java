package dev.copilot.agent.postmortem;

import dev.copilot.core.postmortem.Postmortem;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entrypoint: generate a postmortem for a resolved incident. */
@RestController
public class PostmortemController {

    private final PostmortemAgent agent;

    public PostmortemController(PostmortemAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/postmortem")
    public ResponseEntity<?> generate(@RequestBody PostmortemInput input) {
        if (input.incidentId() == null || input.incidentId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "'incidentId' is required"));
        }
        Postmortem postmortem = agent.generate(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(postmortem);
    }
}
