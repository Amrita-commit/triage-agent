package dev.copilot.approval;

import dev.copilot.core.remediation.ProposedRemediation;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RemediationPublisher} that writes the approved change onto a new Git branch and commits it,
 * using pure-Java JGit (no git binary required). The result is a branch a human can open a PR from
 * and review — the change is never applied to running infrastructure.
 */
public class GitBranchPublisher implements RemediationPublisher {

    private static final Logger log = LoggerFactory.getLogger(GitBranchPublisher.class);

    private final String repoPath;

    public GitBranchPublisher(String repoPath) {
        this.repoPath = repoPath;
    }

    @Override
    public String publish(ProposedRemediation r) {
        String branch = "remediation/" + sanitize(r.sourceIncidentId()) + "-" + shortId(r.id());
        try (Git git = Git.open(new File(repoPath))) {
            String original = git.getRepository().getBranch();
            git.checkout().setCreateBranch(true).setName(branch).call();
            try {
                Path target = Path.of(repoPath).resolve(r.targetPath());
                Files.createDirectories(target.getParent());
                Files.writeString(target, r.newContent());
                git.add().addFilepattern(r.targetPath()).call();
                git.commit()
                        .setMessage("remediation: " + r.title() + "\n\n" + r.rationale()
                                + "\n\nIncident: " + r.sourceIncidentId() + "\nRollback: " + r.rollbackNotes())
                        .setAuthor("sre-copilot", "copilot@localhost")
                        .call();
                log.info("Published remediation {} to branch {}", r.id(), branch);
            } finally {
                // Return the working tree to where it was; the change lives only on the new branch.
                git.checkout().setName(original).call();
            }
            return branch;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish remediation to a branch: " + e.getMessage(), e);
        }
    }

    private static String sanitize(String s) {
        return s == null ? "unknown" : s.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private static String shortId(String id) {
        return id == null ? "0000" : id.replace("-", "").substring(0, Math.min(8, id.replace("-", "").length()));
    }
}
