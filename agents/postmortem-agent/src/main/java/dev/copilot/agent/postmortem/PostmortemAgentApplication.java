package dev.copilot.agent.postmortem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Postmortem agent service: turns a resolved incident's findings + traces into a Markdown postmortem. */
@SpringBootApplication
public class PostmortemAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PostmortemAgentApplication.class, args);
    }
}
