package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * ACTION TOOL — restarts a K8s Deployment by patching the pod template annotation.
 *
 * Equivalent to: kubectl rollout restart deployment/{serviceName} -n {namespace}
 *
 * Uses the pod's ServiceAccount token for K8s API auth.
 * Requires RBAC: patch on apps/deployments in the service namespace.
 *
 * Only available in K8s mode. Returns a safe "not available" message in local mode.
 */
@Slf4j
@Component
public class PodRestartTool {

    private final String namespace;
    private final boolean k8sMode;
    private final String k8sApiBase;

    public PodRestartTool(@Value("${K8S_NAMESPACE:aegispay}") String namespace) {
        this.namespace = namespace;
        this.k8sMode = System.getenv("KUBERNETES_SERVICE_HOST") != null;
        String k8sHost = System.getenv("KUBERNETES_SERVICE_HOST");
        String k8sPort = System.getenv("KUBERNETES_SERVICE_PORT");
        this.k8sApiBase = (k8sHost != null && k8sPort != null)
                ? "https://" + k8sHost + ":" + k8sPort
                : "https://kubernetes.default.svc";
    }

    @Tool(description =
            "ACTION: Restart a K8s Deployment for an AegisPay service by triggering a rolling " +
            "restart. Equivalent to 'kubectl rollout restart deployment/{serviceName}'. " +
            "This patches the pod template annotation with a restart timestamp, causing K8s to " +
            "perform a rolling restart (new pods start before old ones terminate — zero downtime). " +
            "Only use this when logs/metrics confirm a service is in a bad state. " +
            "serviceName must match one of: api-gateway, user-service, transaction-service, " +
            "ledger-service, payment-orchestrator, risk-engine, notification-service, " +
            "reconciliation-service, data-pipeline, ai-platform. " +
            "reason must be a brief explanation of why the restart is being triggered.")
    public String restartDeployment(String serviceName, String reason) {
        log.info("PodRestartTool.restartDeployment: service={} reason={}", serviceName, reason);

        if (!k8sMode) {
            return "PodRestartTool: not in K8s mode — restart not available in local dev.\n" +
                   "To restart locally: stop and start the service via start-local.sh or bat script.";
        }

        try {
            String token = readServiceAccountToken();
            if (token == null) {
                return "Cannot restart: no service account token at /var/run/secrets/kubernetes.io/serviceaccount/token.\n" +
                       "Ensure the ai-platform ServiceAccount has 'patch' on apps/deployments.";
            }

            String restartedAt = Instant.now().toString();
            // PATCH body equivalent to kubectl rollout restart
            String patchBody = String.format(
                    "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{" +
                    "\"kubectl.kubernetes.io/restartedAt\":\"%s\"," +
                    "\"aegispay.ai/restart-reason\":\"%s\"" +
                    "}}}}}",
                    restartedAt, reason.replace("\"", "'")
            );

            String patchUrl = k8sApiBase + "/apis/apps/v1/namespaces/" + namespace
                    + "/deployments/" + serviceName;

            WebClient client = WebClient.builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/merge-patch+json")
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = client.patch().uri(patchUrl)
                    .contentType(MediaType.parseMediaType("application/merge-patch+json"))
                    .bodyValue(patchBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(15));

            if (result != null) {
                return "✅ Restart triggered for deployment/" + serviceName + " at " + restartedAt + "\n" +
                       "Reason: " + reason + "\n" +
                       "K8s will perform a rolling restart — new pods start before old ones terminate.\n" +
                       "Monitor with: kubectl rollout status deployment/" + serviceName + " -n " + namespace;
            }
            return "Restart patch sent but no confirmation received.";

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 403) {
                return "❌ 403 Forbidden — ai-platform ServiceAccount needs 'patch' on apps/deployments.\n" +
                       "Apply the RBAC role in infra/helm/aegispay/templates/ai-platform/role.yaml.";
            }
            if (e.getStatusCode().value() == 404) {
                return "❌ 404 Not Found — deployment '" + serviceName + "' not found in namespace '" + namespace + "'.\n" +
                       "Check the service name matches the K8s deployment name exactly.";
            }
            return "❌ K8s API error " + e.getStatusCode() + ": " + e.getResponseBodyAsString();
        } catch (Exception e) {
            log.error("PodRestartTool failed for {}: {}", serviceName, e.getMessage(), e);
            return "❌ Restart failed: " + e.getMessage();
        }
    }

    private String readServiceAccountToken() {
        try {
            java.nio.file.Path tokenPath = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token");
            if (!Files.exists(tokenPath)) return null;
            return Files.readString(tokenPath).trim();
        } catch (IOException e) {
            return null;
        }
    }
}