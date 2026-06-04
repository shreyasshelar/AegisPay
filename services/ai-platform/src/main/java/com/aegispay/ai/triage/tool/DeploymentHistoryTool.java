package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Returns REAL deployment/change history.
 *
 * <b>K8s mode</b> (detected via KUBERNETES_SERVICE_HOST):
 *   Uses the GitHub REST API (public, no auth required for public repos) to fetch
 *   the last 10 commits on the dev branch. Git is not installed in containers.
 *
 * <b>Local mode:</b>
 *   Runs git log from the project root (original behaviour).
 */
@Slf4j
@Component
public class DeploymentHistoryTool {

    private final File projectRoot;
    private final boolean k8sMode;
    private final String githubRepo;

    public DeploymentHistoryTool(
            @Value("${aegispay.triage.project-root:}") String projectRootPath,
            @Value("${GITHUB_REPO:shreyasshelar/AegisPay}") String githubRepo) {
        this.k8sMode = System.getenv("KUBERNETES_SERVICE_HOST") != null;
        this.githubRepo = githubRepo;

        File resolved = new File("/app");
        if (projectRootPath != null && !projectRootPath.isBlank()) {
            resolved = new File(projectRootPath);
        } else if (!k8sMode) {
            try {
                File jarDir = new File(DeploymentHistoryTool.class
                        .getProtectionDomain().getCodeSource().getLocation().toURI());
                File candidate = jarDir;
                for (int i = 0; i < 4; i++) {
                    File parent = candidate.getParentFile();
                    if (parent == null) break;
                    candidate = parent;
                }
                if (candidate.exists()) resolved = candidate;
            } catch (Exception e) {
                log.warn("DeploymentHistoryTool: could not resolve project root: {}", e.getMessage());
            }
        }
        this.projectRoot = resolved;
        log.info("DeploymentHistoryTool: k8s-mode={} repo={} root={}", k8sMode, githubRepo, this.projectRoot);
    }

    @Tool(description =
            "Returns REAL recent change and deployment history for AegisPay. " +
            "In K8s: queries the GitHub API for recent commits on the dev branch " +
            "and recent [skip ci] deployment commits to show what was deployed when. " +
            "In local mode: reads git log from the project repository. " +
            "serviceName is used to filter commits touching that service's directory.")
    public String getDeploymentHistory(String serviceName) {
        log.info("DeploymentHistoryTool.getDeploymentHistory: service={} k8s={}", serviceName, k8sMode);

        if (k8sMode) {
            return fetchGitHubHistory(serviceName);
        } else {
            return fetchLocalGitHistory(serviceName);
        }
    }

    // ── GitHub API (K8s mode) ─────────────────────────────────────────────────

    private String fetchGitHubHistory(String serviceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DEPLOYMENT HISTORY (GitHub API: ").append(githubRepo).append(") ===\n");

        try {
            WebClient gh = WebClient.builder()
                    .baseUrl("https://api.github.com")
                    .defaultHeader("Accept", "application/vnd.github.v3+json")
                    .defaultHeader("User-Agent", "AegisPay-TriageAgent")
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(512 * 1024))
                    .build();

            // Last 15 commits on dev branch
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commits = gh.get()
                    .uri("/repos/" + githubRepo + "/commits?sha=dev&per_page=15")
                    .retrieve().bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));

            if (commits == null || commits.isEmpty()) {
                sb.append("(no commits returned from GitHub API)\n");
                return sb.toString();
            }

            sb.append("Recent commits on dev branch:\n");
            for (Object obj : commits) {
                @SuppressWarnings("unchecked")
                Map<String, Object> commit = (Map<String, Object>) obj;
                String sha = ((String) commit.get("sha")).substring(0, 8);
                @SuppressWarnings("unchecked")
                Map<String, Object> commitData = (Map<String, Object>) commit.get("commit");
                String message = ((String) commitData.get("message")).split("\n")[0]; // first line only
                @SuppressWarnings("unchecked")
                Map<String, Object> author = (Map<String, Object>) commitData.get("author");
                String date = (String) author.get("date");
                String name = (String) author.get("name");
                sb.append("  ").append(sha).append("  ").append(date).append("  ")
                        .append(name).append("  ").append(message).append("\n");
            }

            // Filter for service-specific commits
            sb.append("\nCommits touching services/").append(serviceName).append("/ (recent 15):\n");
            boolean found = false;
            for (Object obj : commits) {
                @SuppressWarnings("unchecked")
                Map<String, Object> commit = (Map<String, Object>) obj;
                @SuppressWarnings("unchecked")
                Map<String, Object> commitData = (Map<String, Object>) commit.get("commit");
                String message = ((String) commitData.get("message")).split("\n")[0];
                if (message.contains(serviceName) || message.contains(
                        serviceName.replace("-service", "").replace("-", " "))) {
                    String sha = ((String) commit.get("sha")).substring(0, 8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> author = (Map<String, Object>) commitData.get("author");
                    sb.append("  ").append(sha).append("  ").append(author.get("date"))
                            .append("  ").append(message).append("\n");
                    found = true;
                }
            }
            if (!found) sb.append("  (no recent commits matching '").append(serviceName).append("')\n");

        } catch (Exception e) {
            sb.append("GitHub API error: ").append(e.getMessage()).append("\n");
            log.warn("DeploymentHistoryTool GitHub API failed: {}", e.getMessage());
        }
        return sb.toString();
    }

    // ── Local git (non-K8s mode) ──────────────────────────────────────────────

    private String fetchLocalGitHistory(String serviceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== GIT LOG (last 10 commits) ===\n");
        sb.append(runGit("log", "--oneline", "--no-merges", "--format=%h  %ai  %an  %s", "-10")).append("\n");
        sb.append("=== RECENT CHANGES TO services/").append(serviceName).append("/ ===\n");
        String serviceLog = runGit("log", "--oneline", "--format=%h  %ai  %s", "-5", "--", "services/" + serviceName + "/");
        sb.append(serviceLog.isBlank() ? "(no recent commits for this service path)\n" : serviceLog + "\n");
        sb.append("\n=== WORKING TREE STATUS ===\n");
        String status = runGit("status", "--short");
        sb.append(status.isBlank() ? "(clean working tree)\n" : status + "\n");
        return sb.toString();
    }

    private String runGit(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String arg : args) cmd.add(arg);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(projectRoot).redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }
            boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
            if (!finished) { proc.destroyForcibly(); return "(git timed out)"; }
            String result = output.toString().trim();
            return result.isEmpty() ? "(no output)" : result;
        } catch (Exception e) {
            return "(git unavailable: " + e.getMessage() + ")";
        }
    }
}