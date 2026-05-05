package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeploymentHistoryTool {

    @Tool(description = "Fetch recent deployment history for a service. Returns the last 5 deployments with image tag, timestamp, deployer, and rollback status.")
    public String getDeploymentHistory(String serviceName) {
        log.info("DeploymentHistoryTool: fetching deployments for service={}", serviceName);

        // In production: query ArgoCD API or GitHub Deployments API.
        return """
                Deployment history for service '%s' (most recent first):
                1. sha-a8f3c12  deployed 2026-05-02T09:14:00Z by ci-bot           [current]
                2. sha-7e2b891  deployed 2026-05-02T06:00:00Z by ci-bot           [previous]
                3. sha-4d1a033  deployed 2026-05-01T18:22:00Z by john.doe         [rolled back after 12min]
                4. sha-9c5f217  deployed 2026-05-01T14:05:00Z by ci-bot
                5. sha-2a8d604  deployed 2026-05-01T09:00:00Z by ci-bot

                Note: sha-a8f3c12 introduced a connection pool size reduction (50→30) that was reverted in sha-7e2b891 but the config-map was NOT updated — mismatch suspected.
                """.formatted(serviceName);
    }
}
