package dev.copilot.approval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.copilot.core.remediation.ProposedRemediation;
import dev.copilot.core.remediation.RiskLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies that approving a remediation creates a branch with the change and leaves main untouched. */
class GitBranchPublisherTest {

    @Test
    void publishesChangeToNewBranchWithoutTouchingMain(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setInitialBranch("main").setDirectory(repo.toFile()).call()) {
            Files.createDirectories(repo.resolve("infra/local"));
            Files.writeString(repo.resolve("infra/local/main.tf"), "env = [\"LOG_LEVEL=DEBUG\"]\n");
            git.add().addFilepattern("infra/local/main.tf").call();
            git.commit().setMessage("init").setAuthor("t", "t@t").call();

            var remediation = new ProposedRemediation(
                    "abcd1234-0000-0000-0000-000000000000", "inc-9", "Fix log level",
                    "restore INFO", "infra/local/main.tf", "env = [\"LOG_LEVEL=INFO\"]\n",
                    "--- diff ---", "revert", RiskLevel.LOW);

            String branch = new GitBranchPublisher(repo.toString()).publish(remediation);

            assertThat(branch).startsWith("remediation/inc-9-");
            // Working tree is back on main; main.tf is unchanged there.
            assertThat(git.getRepository().getBranch()).isEqualTo("main");
            assertThat(Files.readString(repo.resolve("infra/local/main.tf"))).contains("DEBUG");

            // The change lives on the new branch.
            git.checkout().setName(branch).call();
            assertThat(Files.readString(repo.resolve("infra/local/main.tf"))).contains("INFO");
        }
    }
}
