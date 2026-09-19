package com.strangequark.kubernetesservice.kubernetes;

/**
 * Request object for Kubernetes autoscaling requests
 */
public class KubernetesAutoscalingRequest extends KubernetesServiceRequest {
    /**
     * Minimum replica count
     */
    private int minimumReplicas;

    /**
     * Maximum replica count
     */
    private int maximumReplicas;

    /**
     * Target CPU utilization percentage
     */
    private int targetCpuUtilization;

    /**
     * Get minimum replica count
     */
    public int getMinimumReplicas() {
        return minimumReplicas;
    }

    /**
     * Set minimum replica count
     */
    public void setMinimumReplicas(int minimumReplicas) {
        this.minimumReplicas = minimumReplicas;
    }

    /**
     * Get maximum replica count
     */
    public int getMaximumReplicas() {
        return maximumReplicas;
    }

    /**
     * Set maximum replica count
     */
    public void setMaximumReplicas(int maximumReplicas) {
        this.maximumReplicas = maximumReplicas;
    }

    /**
     * Get target CPU utilization percentage
     */
    public int getTargetCpuUtilization() {
        return targetCpuUtilization;
    }

    /**
     * Set target CPU utilization percentage
     */
    public void setTargetCpuUtilization(int targetCpuUtilization) {
        this.targetCpuUtilization = targetCpuUtilization;
    }
}
