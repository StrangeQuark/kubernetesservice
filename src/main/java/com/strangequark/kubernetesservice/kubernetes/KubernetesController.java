package com.strangequark.kubernetesservice.kubernetes;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@link RestController} responsible for Kubernetes deployment requests
 */
@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesController {
    private final KubernetesService kubernetesService;

    public KubernetesController(KubernetesService kubernetesService) {
        this.kubernetesService = kubernetesService;
    }

    /**
     * Post request endpoint for deploying a service
     * @return {@link ResponseEntity}
     */
    @PostMapping("/deploy")
    public ResponseEntity<?> deployService(@RequestHeader("X-CICD-TOKEN") String cicdToken, @RequestParam String serviceName,
            @RequestParam String image, @RequestParam(required = false) MultipartFile environmentFile) {
        return kubernetesService.deployService(cicdToken, serviceName, image, environmentFile);
    }

    /**
     * Post request endpoint for scaling a service
     * @return {@link ResponseEntity}
     */
    @PostMapping("/scale")
    public ResponseEntity<?> scaleService(@RequestHeader("X-CICD-TOKEN") String cicdToken,
            @RequestBody KubernetesScaleRequest request) {
        return kubernetesService.scaleService(cicdToken, request);
    }

    /**
     * Post request endpoint for restarting a service
     * @return {@link ResponseEntity}
     */
    @PostMapping("/restart")
    public ResponseEntity<?> restartService(@RequestHeader("X-CICD-TOKEN") String cicdToken,
            @RequestBody KubernetesServiceRequest request) {
        return kubernetesService.restartService(cicdToken, request);
    }

    /**
     * Get request endpoint for service deployment status
     * @return {@link ResponseEntity}
     */
    @GetMapping("/status")
    public ResponseEntity<?> getServiceStatus(@RequestHeader("X-CICD-TOKEN") String cicdToken,
            @RequestParam(required = false) String serviceName) {
        return kubernetesService.getServiceStatus(cicdToken, serviceName);
    }

    /**
     * Get request endpoint for supported services
     * @return {@link ResponseEntity}
     */
    @GetMapping("/services")
    public ResponseEntity<?> getServices(@RequestHeader("X-CICD-TOKEN") String cicdToken) {
        return kubernetesService.getServices(cicdToken);
    }

    /**
     * Post request endpoint for undeploying a service
     * @return {@link ResponseEntity}
     */
    @PostMapping("/undeploy")
    public ResponseEntity<?> undeployService(@RequestHeader("X-CICD-TOKEN") String cicdToken,
            @RequestBody KubernetesServiceRequest request) {
        return kubernetesService.undeployService(cicdToken, request);
    }

    /**
     * Post request endpoint for rolling back a service
     * @return {@link ResponseEntity}
     */
    @PostMapping("/rollback")
    public ResponseEntity<?> rollbackService(@RequestHeader("X-CICD-TOKEN") String cicdToken,
            @RequestBody KubernetesServiceRequest request) {
        return kubernetesService.rollbackService(cicdToken, request);
    }

    /**
     * Post request endpoint for configuring service autoscaling
     * @return {@link ResponseEntity}
     */
    @PostMapping("/autoscaling")
    public ResponseEntity<?> configureAutoscaling(@RequestHeader("X-CICD-TOKEN") String cicdToken,
            @RequestBody KubernetesAutoscalingRequest request) {
        return kubernetesService.configureAutoscaling(cicdToken, request);
    }

    /**
     * Post request endpoint for configuring service exposure
     * @return {@link ResponseEntity}
     */
    @PostMapping("/exposure")
    public ResponseEntity<?> configureExposure(@RequestHeader("X-CICD-TOKEN") String cicdToken,
            @RequestBody KubernetesExposureRequest request) {
        return kubernetesService.configureExposure(cicdToken, request);
    }
}
