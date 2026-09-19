package com.strangequark.kubernetesservice.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@link Service} responsible for Kubernetes deployments
 */
@Service
public class KubernetesService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesService.class);
    private static final String MANIFESTS_DIRECTORY = "/manifests/";
    private static final String EXPOSURE_CONFIG_MAP = "service-exposure";

    private final KubernetesClient kubernetesClient;
    private final Environment environment;

    @Value("${kubernetes.namespace}")
    private String namespace;

    @Value("${kubernetes.cicd.token}")
    private String cicdToken;

    public KubernetesService(KubernetesClient kubernetesClient, Environment environment) {
        this.kubernetesClient = kubernetesClient;
        this.environment = environment;
    }

    /**
     * Deploy a service to Kubernetes
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> deployService(
            String requestCicdToken,
            String serviceName,
            String image,
            MultipartFile environmentFile
    ) {
        LOGGER.info("Attempting to deploy {}", serviceName);

        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            if(!isServiceSupported(serviceName))
                return ResponseEntity.status(400).body("Unsupported service: " + serviceName);

            if(image.isBlank())
                return ResponseEntity.status(400).body("Service image is required");

            if(environmentFile != null && !environmentFile.isEmpty())
                updateEnvironmentSecret(serviceName, environmentFile);
            else if(kubernetesClient.secrets().inNamespace(namespace).withName(serviceName + "-env").get() == null)
                return ResponseEntity.status(400).body("Service environment file is required");

            applyManifests(serviceName, image);
            configureServiceExposure(serviceName);

            LOGGER.info("{} deployment started", serviceName);
            return ResponseEntity.accepted().body(serviceName + " deployment started");
        } catch(Exception ex) {
            LOGGER.error("Failed to deploy {}: {}", serviceName, ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to deploy " + serviceName);
        }
    }

    /**
     * Scale a service deployment
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> scaleService(String requestCicdToken, KubernetesScaleRequest request) {
        String serviceName = request.getServiceName();
        int replicas = request.getReplicas();
        LOGGER.info("Attempting to scale {}", serviceName);

        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            if(!isServiceSupported(serviceName))
                return ResponseEntity.status(400).body("Unsupported service: " + serviceName);

            if(replicas < 0)
                return ResponseEntity.status(400).body("Replica count cannot be negative");

            Deployment deployment = getDeployment(serviceName);
            deployment.getSpec().setReplicas(replicas);
            kubernetesClient.apps().deployments().inNamespace(namespace).resource(deployment).update();

            return ResponseEntity.accepted().body(serviceName + " scaling to " + replicas + " replicas started");
        } catch(Exception ex) {
            LOGGER.error("Failed to scale {}: {}", serviceName, ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to scale " + serviceName);
        }
    }

    /**
     * Restart a service deployment
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> restartService(String requestCicdToken, KubernetesServiceRequest request) {
        String serviceName = request.getServiceName();
        LOGGER.info("Attempting to restart {}", serviceName);

        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            if(!isServiceSupported(serviceName))
                return ResponseEntity.status(400).body("Unsupported service: " + serviceName);

            kubernetesClient.apps().deployments().inNamespace(namespace).withName(getDeploymentName(serviceName))
                    .rolling().restart();

            return ResponseEntity.accepted().body(serviceName + " restart started");
        } catch(Exception ex) {
            LOGGER.error("Failed to restart {}: {}", serviceName, ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to restart " + serviceName);
        }
    }

    /**
     * Get service deployment status
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> getServiceStatus(String requestCicdToken, String serviceName) {
        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            if(serviceName != null) {
                if(!isServiceSupported(serviceName))
                    return ResponseEntity.status(400).body("Unsupported service: " + serviceName);

                return ResponseEntity.ok(getDeploymentStatus(serviceName));
            }

            List<Map<String, Object>> serviceStatuses = new ArrayList<>();
            for(String supportedService : getSupportedServices())
                serviceStatuses.add(getDeploymentStatus(supportedService));

            return ResponseEntity.ok(serviceStatuses);
        } catch(Exception ex) {
            LOGGER.error("Failed to get service status: {}", ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to get service status");
        }
    }

    /**
     * Get supported services
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> getServices(String requestCicdToken) {
        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            return ResponseEntity.ok(getSupportedServices());
        } catch(Exception ex) {
            LOGGER.error("Failed to get supported services: {}", ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to get supported services");
        }
    }

    /**
     * Undeploy a service application
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> undeployService(String requestCicdToken, KubernetesServiceRequest request) {
        String serviceName = request.getServiceName();
        LOGGER.info("Attempting to undeploy {}", serviceName);

        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            if(!isServiceSupported(serviceName))
                return ResponseEntity.status(400).body("Unsupported service: " + serviceName);

            String deploymentName = getDeploymentName(serviceName);
            kubernetesClient.autoscaling().v2().horizontalPodAutoscalers().inNamespace(namespace)
                    .withName(deploymentName).delete();
            kubernetesClient.network().v1().ingresses().inNamespace(namespace).withName(deploymentName).delete();
            kubernetesClient.apps().deployments().inNamespace(namespace).withName(deploymentName).delete();
            kubernetesClient.services().inNamespace(namespace).withName(deploymentName).delete();

            return ResponseEntity.accepted().body(serviceName + " undeployment started");
        } catch(Exception ex) {
            LOGGER.error("Failed to undeploy {}: {}", serviceName, ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to undeploy " + serviceName);
        }
    }

    /**
     * Roll back a service deployment
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> rollbackService(String requestCicdToken, KubernetesServiceRequest request) {
        String serviceName = request.getServiceName();
        LOGGER.info("Attempting to roll back {}", serviceName);

        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            if(!isServiceSupported(serviceName))
                return ResponseEntity.status(400).body("Unsupported service: " + serviceName);

            kubernetesClient.apps().deployments().inNamespace(namespace).withName(getDeploymentName(serviceName))
                    .rolling().undo();

            return ResponseEntity.accepted().body(serviceName + " rollback started");
        } catch(Exception ex) {
            LOGGER.error("Failed to roll back {}: {}", serviceName, ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to roll back " + serviceName);
        }
    }

    /**
     * Configure service autoscaling
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> configureAutoscaling(String requestCicdToken, KubernetesAutoscalingRequest request) {
        String serviceName = request.getServiceName();
        int minimumReplicas = request.getMinimumReplicas();
        int maximumReplicas = request.getMaximumReplicas();
        int targetCpuUtilization = request.getTargetCpuUtilization();
        LOGGER.info("Attempting to configure {} autoscaling", serviceName);

        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            if(!isServiceSupported(serviceName))
                return ResponseEntity.status(400).body("Unsupported service: " + serviceName);

            if(minimumReplicas < 1 || maximumReplicas < minimumReplicas)
                return ResponseEntity.status(400).body("Invalid replica range");

            if(targetCpuUtilization < 1 || targetCpuUtilization > 100)
                return ResponseEntity.status(400).body("CPU utilization must be between 1 and 100");

            String autoscalingManifest = """
                    apiVersion: autoscaling/v2
                    kind: HorizontalPodAutoscaler
                    metadata:
                      name: %s
                    spec:
                      scaleTargetRef:
                        apiVersion: apps/v1
                        kind: Deployment
                        name: %s
                      minReplicas: %d
                      maxReplicas: %d
                      metrics:
                        - type: Resource
                          resource:
                            name: cpu
                            target:
                              type: Utilization
                              averageUtilization: %d
                    """.formatted(getDeploymentName(serviceName), getDeploymentName(serviceName), minimumReplicas,
                    maximumReplicas, targetCpuUtilization);

            kubernetesClient.load(new ByteArrayInputStream(autoscalingManifest.getBytes(StandardCharsets.UTF_8)))
                    .inNamespace(namespace)
                    .serverSideApply();

            return ResponseEntity.ok(serviceName + " autoscaling configured successfully");
        } catch(Exception ex) {
            LOGGER.error("Failed to configure {} autoscaling: {}", serviceName, ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to configure " + serviceName + " autoscaling");
        }
    }

    /**
     * Configure service exposure
     * @return {@link ResponseEntity}
     */
    public ResponseEntity<?> configureExposure(String requestCicdToken, KubernetesExposureRequest request) {
        String serviceName = request.getServiceName();
        String exposureMode = request.getMode();
        LOGGER.info("Attempting to configure {} exposure", serviceName);

        try {
            if(!validateCicdToken(requestCicdToken))
                return ResponseEntity.status(401).body("Invalid CI/CD token");

            if(!isServiceSupported(serviceName))
                return ResponseEntity.status(400).body("Unsupported service: " + serviceName);

            if(exposureMode == null || (!exposureMode.equals("disabled") && !exposureMode.equals("local")
                    && !exposureMode.equals("public")))
                return ResponseEntity.status(400).body("Invalid exposure mode");

            if(exposureMode.equals("public") && (request.getHost() == null || request.getHost().isBlank()
                    || request.getTlsSecretName() == null || request.getTlsSecretName().isBlank()))
                return ResponseEntity.status(400).body("Public exposure requires host and TLS secret name");

            if(kubernetesClient.services().inNamespace(namespace).withName(getDeploymentName(serviceName)).get() == null)
                return ResponseEntity.status(400).body("Service is not deployed");

            saveExposureConfiguration(request);
            configureServiceExposure(serviceName);
            return ResponseEntity.ok(serviceName + " exposure updated to " + exposureMode);
        } catch(Exception ex) {
            LOGGER.error("Failed to configure {} exposure: {}", serviceName, ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body("Failed to configure " + serviceName + " exposure");
        }
    }

    /**
     * Validate a Kubernetes CI/CD token
     * @return boolean
     */
    private boolean validateCicdToken(String requestCicdToken) {
        return MessageDigest.isEqual(
                cicdToken.getBytes(StandardCharsets.UTF_8),
                requestCicdToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Get supported services
     */
    private List<String> getSupportedServices() throws Exception {
        try(Stream<Path> servicePaths = Files.list(Path.of(MANIFESTS_DIRECTORY))) {
            return servicePaths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(serviceName -> serviceName.matches("[a-z]+service"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Get a service deployment
     */
    private Deployment getDeployment(String serviceName) {
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(namespace)
                .withName(getDeploymentName(serviceName))
                .get();

        if(deployment == null)
            throw new RuntimeException("Service is not deployed");

        return deployment;
    }

    /**
     * Get a service deployment status
     */
    private Map<String, Object> getDeploymentStatus(String serviceName) {
        Map<String, Object> deploymentStatus = new HashMap<>();
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(namespace)
                .withName(getDeploymentName(serviceName))
                .get();

        deploymentStatus.put("serviceName", serviceName);

        if(deployment == null) {
            deploymentStatus.put("deployed", false);
            return deploymentStatus;
        }

        Integer replicas = deployment.getSpec().getReplicas();
        Integer availableReplicas = deployment.getStatus() == null ? Integer.valueOf(0)
                : deployment.getStatus().getAvailableReplicas();
        Integer updatedReplicas = deployment.getStatus() == null ? Integer.valueOf(0)
                : deployment.getStatus().getUpdatedReplicas();
        Integer unavailableReplicas = deployment.getStatus() == null ? Integer.valueOf(0)
                : deployment.getStatus().getUnavailableReplicas();

        deploymentStatus.put("deployed", true);
        deploymentStatus.put("replicas", replicas);
        deploymentStatus.put("updatedReplicas", updatedReplicas == null ? 0 : updatedReplicas);
        deploymentStatus.put("availableReplicas", availableReplicas == null ? 0 : availableReplicas);
        deploymentStatus.put("unavailableReplicas", unavailableReplicas == null ? 0 : unavailableReplicas);
        deploymentStatus.put("ready", replicas == 0 || replicas.equals(availableReplicas));
        deploymentStatus.put("image", deployment.getSpec().getTemplate().getSpec().getContainers().getFirst().getImage());
        deploymentStatus.put("pods", getPodStatuses(deployment.getMetadata().getName()));
        return deploymentStatus;
    }

    /**
     * Get service Pod statuses
     */
    private List<Map<String, Object>> getPodStatuses(String deploymentName) {
        return kubernetesClient.pods().inNamespace(namespace).withLabel("app", deploymentName).list().getItems()
                .stream()
                .map(this::getPodStatus)
                .toList();
    }

    /**
     * Get a Pod status
     */
    private Map<String, Object> getPodStatus(Pod pod) {
        Map<String, Object> podStatus = new HashMap<>();
        List<Map<String, Object>> containerStatuses = new ArrayList<>();

        podStatus.put("name", pod.getMetadata().getName());
        podStatus.put("phase", pod.getStatus().getPhase());

        if(pod.getStatus().getContainerStatuses() != null) {
            for(ContainerStatus containerStatus : pod.getStatus().getContainerStatuses())
                containerStatuses.add(getContainerStatus(containerStatus));
        }

        podStatus.put("ready", !containerStatuses.isEmpty()
                && containerStatuses.stream().allMatch(containerStatus -> Boolean.TRUE.equals(containerStatus.get("ready"))));
        podStatus.put("containers", containerStatuses);
        return podStatus;
    }

    /**
     * Get a container status
     */
    private Map<String, Object> getContainerStatus(ContainerStatus containerStatus) {
        Map<String, Object> status = new HashMap<>();

        status.put("name", containerStatus.getName());
        status.put("image", containerStatus.getImage());
        status.put("ready", Boolean.TRUE.equals(containerStatus.getReady()));
        status.put("restartCount", containerStatus.getRestartCount());

        if(containerStatus.getState() != null && containerStatus.getState().getWaiting() != null) {
            ContainerStateWaiting waiting = containerStatus.getState().getWaiting();
            status.put("status", waiting.getReason());
            status.put("message", getStatusMessage(waiting.getMessage()));
        } else if(containerStatus.getState() != null && containerStatus.getState().getTerminated() != null) {
            ContainerStateTerminated terminated = containerStatus.getState().getTerminated();
            status.put("status", terminated.getReason());
            status.put("exitCode", terminated.getExitCode());
            status.put("message", getStatusMessage(terminated.getMessage()));
        } else {
            status.put("status", "Running");
        }

        if(containerStatus.getLastState() != null && containerStatus.getLastState().getTerminated() != null) {
            ContainerStateTerminated lastTermination = containerStatus.getLastState().getTerminated();
            status.put("lastTerminationReason", lastTermination.getReason());
            status.put("lastTerminationExitCode", lastTermination.getExitCode());
        }

        return status;
    }

    /**
     * Limit a Kubernetes status message
     */
    private String getStatusMessage(String message) {
        if(message == null)
            return null;

        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    /**
     * Update a service environment Secret
     */
    private void updateEnvironmentSecret(String serviceName, MultipartFile environmentFile) throws Exception {
        Map<String, String> environmentVariables = new HashMap<>();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(environmentFile.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .forEach(line -> addEnvironmentVariable(environmentVariables, line));
        }

        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(serviceName + "-env")
                    .withNamespace(namespace)
                .endMetadata()
                .withType("Opaque")
                .withStringData(environmentVariables)
                .build();

        kubernetesClient.secrets().inNamespace(namespace).resource(secret).serverSideApply();
        restartEnvironmentSecretDeployments(serviceName + "-env");
    }

    /**
     * Get an environment value without optional dotenv quotes
     */
    private String getEnvironmentValue(String value) {
        value = value.trim();

        if(value.length() > 1 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))))
            return value.substring(1, value.length() - 1);

        int commentIndex = value.indexOf(" #");
        if(commentIndex > -1)
            return value.substring(0, commentIndex).trim();

        return value;
    }

    /**
     * Add a dotenv environment variable
     */
    private void addEnvironmentVariable(Map<String, String> environmentVariables, String line) {
        int separatorIndex = line.indexOf('=');
        if(separatorIndex < 1)
            return;

        String key = line.substring(0, separatorIndex).trim();
        String value = line.substring(separatorIndex + 1);
        environmentVariables.put(key, getEnvironmentValue(value));
    }

    /**
     * Restart deployments which use an updated environment Secret
     */
    private void restartEnvironmentSecretDeployments(String secretName) {
        kubernetesClient.apps().deployments().inNamespace(namespace).list().getItems().stream()
                .filter(deployment -> usesEnvironmentSecret(deployment.getSpec().getTemplate().getSpec().getContainers(), secretName)
                        || usesEnvironmentSecret(deployment.getSpec().getTemplate().getSpec().getInitContainers(), secretName))
                .forEach(deployment -> kubernetesClient.apps().deployments().inNamespace(namespace)
                        .withName(deployment.getMetadata().getName()).rolling().restart());
    }

    /**
     * Check whether a list of containers uses an environment Secret
     */
    private boolean usesEnvironmentSecret(List<Container> containers, String secretName) {
        if(containers == null)
            return false;

        return containers.stream().anyMatch(container -> (container.getEnvFrom() != null && container.getEnvFrom().stream()
                .anyMatch(environment -> environment.getSecretRef() != null
                        && environment.getSecretRef().getName().equals(secretName)))
                || (container.getEnv() != null && container.getEnv().stream().anyMatch(environment -> environment.getValueFrom() != null
                        && environment.getValueFrom().getSecretKeyRef() != null
                        && environment.getValueFrom().getSecretKeyRef().getName().equals(secretName))));
    }

    /**
     * Apply a service's Kubernetes manifests
     */
    private void applyManifests(String serviceName, String image) throws Exception {
        try(Stream<Path> manifestPaths = Files.list(getManifestDirectory(serviceName))) {
            manifestPaths
                    .filter(path -> path.toString().endsWith(".yaml"))
                    .filter(path -> !path.getFileName().toString().endsWith("-ingress.yaml"))
                    .sorted()
                    .forEach(path -> applyManifest(path, image));
        }
    }

    /**
     * Apply a Kubernetes manifest
     */
    private void applyManifest(Path manifestPath, String image) {
        try {
            String manifest = Files.readString(manifestPath)
                    .replace("__SERVICE_IMAGE__", image)
                    .replace("__KUBERNETES_NAMESPACE__", namespace);

            kubernetesClient.load(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)))
                    .inNamespace(namespace)
                    .fieldManager("kubernetes-service")
                    .forceConflicts()
                    .serverSideApply();
        } catch(Exception ex) {
            throw new RuntimeException("Failed to apply Kubernetes manifest", ex);
        }
    }

    /**
     * Configure a service's exposure
     */
    private void configureServiceExposure(String serviceName) {
        String exposureMode = getExposureConfiguration(serviceName, "mode",
                environment.getProperty(serviceName + ".ingress.mode", "disabled"));

        if(exposureMode.equals("disabled")) {
            configureClusterIpService(serviceName);
            kubernetesClient.network().v1().ingresses().inNamespace(namespace)
                    .withName(getDeploymentName(serviceName)).delete();
            return;
        }

        if(exposureMode.equals("local")) {
            configureLocalNodePortService(serviceName);
            kubernetesClient.network().v1().ingresses().inNamespace(namespace)
                    .withName(getDeploymentName(serviceName)).delete();
            return;
        }

        if(exposureMode.equals("public")) {
            configureClusterIpService(serviceName);
            String ingressHost = getExposureConfiguration(serviceName, "host",
                    environment.getProperty(serviceName + ".ingress.host", ""));
            String ingressTlsSecretName = getExposureConfiguration(serviceName, "tls-secret-name",
                    environment.getProperty(serviceName + ".ingress.tls.secret.name", ""));

            if(ingressHost.isBlank() || ingressTlsSecretName.isBlank())
                throw new RuntimeException("Public ingress configuration is incomplete");

            try {
                String manifest = Files.readString(getManifestDirectory(serviceName).resolve("public-ingress.yaml"))
                        .replace("__INGRESS_HOST__", ingressHost)
                        .replace("__INGRESS_TLS_SECRET_NAME__", ingressTlsSecretName);

                kubernetesClient.load(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)))
                        .inNamespace(namespace)
                        .serverSideApply();
            } catch(Exception ex) {
                throw new RuntimeException("Failed to apply Kubernetes manifest", ex);
            }
            return;
        }

        throw new RuntimeException("Invalid service exposure mode");
    }

    /**
     * Configure a service as internal only
     */
    private void configureClusterIpService(String serviceName) {
        io.fabric8.kubernetes.api.model.Service service = kubernetesClient.services()
                .inNamespace(namespace).withName(getDeploymentName(serviceName)).get();

        service.getSpec().setType("ClusterIP");
        service.getSpec().getPorts().getFirst().setNodePort(null);
        kubernetesClient.services().inNamespace(namespace).resource(service).update();
    }

    /**
     * Configure a service for local NodePort access
     */
    private void configureLocalNodePortService(String serviceName) {
        int localNodePort = Integer.parseInt(environment.getRequiredProperty(serviceName + ".local.node.port"));
        io.fabric8.kubernetes.api.model.Service service = kubernetesClient.services()
                .inNamespace(namespace).withName(getDeploymentName(serviceName)).get();

        service.getSpec().setType("NodePort");
        service.getSpec().getPorts().getFirst().setNodePort(localNodePort);
        kubernetesClient.services().inNamespace(namespace).resource(service).update();
    }

    /**
     * Get a service exposure setting
     */
    private String getExposureConfiguration(String serviceName, String propertyName, String defaultValue) {
        ConfigMap exposureConfigMap = kubernetesClient.configMaps().inNamespace(namespace)
                .withName(EXPOSURE_CONFIG_MAP).get();

        if(exposureConfigMap == null || exposureConfigMap.getData() == null)
            return defaultValue;

        return exposureConfigMap.getData().getOrDefault(serviceName + "." + propertyName, defaultValue);
    }

    /**
     * Save a service exposure setting
     */
    private void saveExposureConfiguration(KubernetesExposureRequest request) {
        ConfigMap exposureConfigMap = kubernetesClient.configMaps().inNamespace(namespace)
                .withName(EXPOSURE_CONFIG_MAP).get();

        if(exposureConfigMap == null) {
            exposureConfigMap = new ConfigMapBuilder()
                    .withNewMetadata()
                        .withName(EXPOSURE_CONFIG_MAP)
                    .endMetadata()
                    .withData(new HashMap<>())
                    .build();
        }

        exposureConfigMap.getData().put(request.getServiceName() + ".mode", request.getMode());

        if(request.getHost() != null && !request.getHost().isBlank())
            exposureConfigMap.getData().put(request.getServiceName() + ".host", request.getHost());

        if(request.getTlsSecretName() != null && !request.getTlsSecretName().isBlank())
            exposureConfigMap.getData().put(request.getServiceName() + ".tls-secret-name",
                    request.getTlsSecretName());

        kubernetesClient.configMaps().inNamespace(namespace).resource(exposureConfigMap).createOrReplace();
    }

    /**
     * Check if a service has Kubernetes manifests
     */
    private boolean isServiceSupported(String serviceName) {
        return serviceName.matches("[a-z]+service") && Files.isDirectory(getManifestDirectory(serviceName));
    }

    /**
     * Get a service Kubernetes manifest directory
     */
    private Path getManifestDirectory(String serviceName) {
        return Path.of(MANIFESTS_DIRECTORY).resolve(serviceName);
    }

    /**
     * Get a service deployment name
     */
    private String getDeploymentName(String serviceName) {
        return serviceName.replace("service", "-service");
    }

}
