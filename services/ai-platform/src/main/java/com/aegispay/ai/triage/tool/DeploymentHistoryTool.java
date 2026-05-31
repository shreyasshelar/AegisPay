package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Returns REAL deployment/change context from git history.
 *
 * AegisPay runs locally (not on K8s in dev), so there is no ArgoCD or Helm
 * rollout history to query.  The most relevant change signal is git commits —
 * they show exactly what changed and when.
 *
 * The tool reads `git log` from the project root so the agent sees real commits,
 * not invented deployment SHAs.
 */
@Slf4j
@Component
public class DeploymentHistoryTool {

    private final File projectRoot;

    public DeploymentHistoryTool(
            @Value("${aegispay.triage.project-root:}") String projectRootPath) {
        // Compute into a local variable first — a final field cannot be assigned
        // inside multiple branches of a try-catch in Java.
        File resolved;
        if (projectRootPath != null && !projectRootPath.isBlank()) {
            resolved = new File(projectRootPath);
        } else {
            // Auto-detect: JAR is at <root>/services/ai-platform/target/*.jar
            // Walk 4 levels up: BOOT-INF/classes → target → ai-platform → services → root
            File fallback = new File(".");
            try {
                File jarDir = new File(DeploymentHistoryTool.class
                        .getProtectionDomain().getCodeSource().getLocation().toURI());
                fallback = jarDir.getParentFile()   // target/ (or BOOT-INF/classes/)
                        .getParentFile()              // ai-platform/ (or target/)
                        .getParentFile()              // services/   (or ai-platform/)
                        .getParentFile();             // <project root> (or services/)
            } catch (Exception e) {
                log.warn("DeploymentHistoryTool: could not resolve project root from JAR path: {}", e.getMessage());
            }
            resolved = fallback;
        }
        this.projectRoot = resolved;
        log.debug("DeploymentHistoryTool: project root resolved to {}", this.projectRoot.getAbsolutePath());
    }

    @Tool(description =
            "Returns REAL recent change history for the AegisPay project. " +
            "Reads the last 10 git commits from the project repository so you can identify " +
            "what changed recently and correlate it with an incident. " +
            "This is a local dev environment (no K8s/ArgoCD deployment history). " +
            "serviceName is accepted but ignored — git log covers the whole project.")
    public String getDeploymentHistory(String serviceName) {
        log.info("DeploymentHistoryTool.getDeploymentHistory: service={} (git log covers all services)",
                serviceName);

        StringBuilder sb = new StringBuilder();
        sb.append("=== ENVIRONMENT: local dev (Windows, docker compose, no K8s) ===\n");
        sb.append("There is no ArgoCD/Helm deployment history in this environment.\n");
        sb.append("Change history comes from git commits.\n\n");

        // ── git log ──────────────────────────────────────────────────────────────
        sb.append("=== GIT LOG (last 10 commits) ===\n");
        String gitLog = runGit(
                "log", "--oneline", "--no-merges", "--format=%h  %ai  %an  %s", "-10");
        sb.append(gitLog).append("\n");

        // ── recent changes to the named service ─────────────────────────────────
        sb.append("=== RECENT CHANGES TO services/").append(serviceName).append("/ (last 5 commits) ===\n");
        String serviceLog = runGit(
                "log", "--oneline", "--format=%h  %ai  %s",
                "-5", "--", "services/" + serviceName + "/");
        sb.append(serviceLog.isBlank() ? "(no recent commits for this service path)\n" : serviceLog + "\n");

        // ── unstaged / staged changes (indicates in-flight work) ─────────────────
        sb.append("\n=== WORKING TREE STATUS ===\n");
        String status = runGit("status", "--short");
        sb.append(status.isBlank() ? "(clean working tree)\n" : status + "\n");

        return sb.toString();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String runGit(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String arg : args) cmd.add(arg);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(projectRoot)
                    .redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return "(git timed out)";
            }
            String result = output.toString().trim();
            return result.isEmpty() ? "(no output)" : result;
        } catch (Exception e) {
            log.warn("DeploymentHistoryTool: git command {} failed: {}", cmd, e.getMessage());
            return "(git unavailable: " + e.getMessage() + ")";
        }
    }
}
