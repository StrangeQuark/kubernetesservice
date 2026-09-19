package com.strangequark.kubernetesservice.kubernetes;

/**
 * Request object for Kubernetes service requests
 */
public class KubernetesServiceRequest {
    /**
     * Name of the service being managed
     */
    private String serviceName;

    public KubernetesServiceRequest() {
    }

    /**
     * Get service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Set service name
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
