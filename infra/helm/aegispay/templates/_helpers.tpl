{{/*
Expand the name of the chart.
*/}}
{{- define "aegispay.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create chart label value.
*/}}
{{- define "aegispay.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "aegispay.labels" -}}
helm.sh/chart: {{ include "aegispay.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: aegispay
{{- end }}

{{/*
Selector labels for a specific service.
Usage: {{ include "aegispay.selectorLabels" (dict "name" "api-gateway" "context" .) }}
*/}}
{{- define "aegispay.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .context.Release.Name }}
{{- end }}

{{/*
Standard pod annotations for observability.
*/}}
{{- define "aegispay.podAnnotations" -}}
prometheus.io/scrape: "true"
prometheus.io/port: "{{ .port }}"
prometheus.io/path: "/actuator/prometheus"
{{- end }}

{{/*
Generic backend service Deployment.
Usage: {{ include "aegispay.serviceDeployment" (dict "name" "user-service" "key" "user_service" "svc" .Values.services.user_service "context" .) }}
Callers must provide: name (kebab), key (underscore), svc (the service values block), context (root .)
*/}}
{{- define "aegispay.serviceDeployment" -}}
{{- $name := .name -}}
{{- $svc  := .svc  -}}
{{- $ctx  := .context -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $name }}
  namespace: {{ $ctx.Values.global.namespace }}
  labels:
    {{- include "aegispay.labels" $ctx | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
    app.kubernetes.io/component: {{ $name }}
    app.kubernetes.io/version: {{ $svc.image.tag | quote }}
spec:
  replicas: {{ $svc.replicas | default 2 }}
  selector:
    matchLabels:
      {{- include "aegispay.selectorLabels" (dict "name" $name "context" $ctx) | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "aegispay.labels" $ctx | nindent 8 }}
        {{- include "aegispay.selectorLabels" (dict "name" $name "context" $ctx) | nindent 8 }}
        app.kubernetes.io/component: {{ $name }}
        app.kubernetes.io/version: {{ $svc.image.tag | quote }}
      annotations:
        {{- include "aegispay.podAnnotations" (dict "port" $svc.port) | nindent 8 }}
        checksum/config: {{ include (print $ctx.Template.BasePath "/" $name "/configmap.yaml") $ctx | sha256sum }}
    spec:
      serviceAccountName: {{ $name }}
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        runAsGroup: 1001
        fsGroup: 1001
        seccompProfile:
          type: RuntimeDefault
      {{- with $ctx.Values.global.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- if $svc.initContainers }}
      initContainers:
        {{- toYaml $svc.initContainers | nindent 8 }}
      {{- end }}
      containers:
        - name: {{ $name }}
          image: "{{ $ctx.Values.global.imageRepositoryPrefix }}/{{ $svc.image.repository }}:{{ $svc.image.tag }}"
          imagePullPolicy: {{ $ctx.Values.global.imagePullPolicy }}
          ports:
            - name: http
              containerPort: {{ $svc.port }}
              protocol: TCP
          envFrom:
            - configMapRef:
                name: {{ $name }}-config
          env:
            - name: SERVER_PORT
              value: {{ $svc.port | quote }}
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: aegispay-db-secret
                  key: password
            - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
              value: {{ $ctx.Values.global.kafka.brokers | quote }}
            - name: JAVA_OPTS
              value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dspring.config.location=classpath:/application.yml,/config/application.yml"
            {{- if $svc.extraEnv }}
            {{- toYaml $svc.extraEnv | nindent 12 }}
            {{- end }}
          volumeMounts:
            - name: config
              mountPath: /config
              readOnly: true
          resources:
            {{- toYaml $svc.resources | nindent 12 }}
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            initialDelaySeconds: 40
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            initialDelaySeconds: 25
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
      volumes:
        - name: config
          configMap:
            name: {{ $name }}-config
      terminationGracePeriodSeconds: 30
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: DoNotSchedule
          labelSelector:
            matchLabels:
              app.kubernetes.io/name: {{ $name }}
{{- end }}

{{/*
Generic ClusterIP Service.
*/}}
{{- define "aegispay.serviceService" -}}
{{- $name := .name -}}
{{- $svc  := .svc  -}}
{{- $ctx  := .context -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ $name }}
  namespace: {{ $ctx.Values.global.namespace }}
  labels:
    {{- include "aegispay.labels" $ctx | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
spec:
  type: ClusterIP
  selector:
    {{- include "aegispay.selectorLabels" (dict "name" $name "context" $ctx) | nindent 4 }}
  ports:
    - name: http
      port: {{ $svc.port }}
      targetPort: http
      protocol: TCP
{{- end }}

{{/*
Generic HPA.
*/}}
{{- define "aegispay.serviceHpa" -}}
{{- $name := .name -}}
{{- $svc  := .svc  -}}
{{- $ctx  := .context -}}
{{- if $svc.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ $name }}
  namespace: {{ $ctx.Values.global.namespace }}
  labels:
    {{- include "aegispay.labels" $ctx | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ $name }}
  minReplicas: {{ $svc.autoscaling.minReplicas }}
  maxReplicas: {{ $svc.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ $svc.autoscaling.targetCPUUtilizationPercentage }}
{{- end }}
{{- end }}

{{/*
Generic PodDisruptionBudget.
*/}}
{{- define "aegispay.servicePdb" -}}
{{- $name := .name -}}
{{- $ctx  := .context -}}
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {{ $name }}
  namespace: {{ $ctx.Values.global.namespace }}
  labels:
    {{- include "aegispay.labels" $ctx | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
spec:
  minAvailable: {{ $ctx.Values.pdb.minAvailable }}
  selector:
    matchLabels:
      {{- include "aegispay.selectorLabels" (dict "name" $name "context" $ctx) | nindent 6 }}
{{- end }}

{{/*
Generic ServiceAccount.
*/}}
{{- define "aegispay.serviceAccount" -}}
{{- $name := .name -}}
{{- $ctx  := .context -}}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ $name }}
  namespace: {{ $ctx.Values.global.namespace }}
  labels:
    {{- include "aegispay.labels" $ctx | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
automountServiceAccountToken: false
{{- end }}

{{/*
Generic NetworkPolicy — deny all ingress except from api-gateway and same-namespace.
*/}}
{{- define "aegispay.serviceNetworkPolicy" -}}
{{- $name := .name -}}
{{- $ctx  := .context -}}
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: {{ $name }}
  namespace: {{ $ctx.Values.global.namespace }}
  labels:
    {{- include "aegispay.labels" $ctx | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: {{ $name }}
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: api-gateway
        - podSelector:
            matchLabels:
              app.kubernetes.io/part-of: aegispay
      ports:
        - protocol: TCP
          port: {{ (.svc).port }}
  egress:
    - {}   # Allow all egress (Kafka, DB, Redis, external)
{{- end }}
