package com.strangequark.kubernetesservice.kubernetes;

/**
 * Request object for Kubernetes scale requests
 */
public class KubernetesScaleRequest extends KubernetesServiceRequest {
    /**
     * Desired replica count
     */
    private int replicas;

    /**
     * Get replica count
     */
    public int getReplicas() {
        return replicas;
    }

    /**
     * Set replica count
     */
    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }
}
