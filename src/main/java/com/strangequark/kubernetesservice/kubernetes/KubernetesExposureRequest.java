package com.strangequark.kubernetesservice.kubernetes;

/**
 * Request object for Kubernetes service exposure requests
 */
public class KubernetesExposureRequest extends KubernetesServiceRequest {
    /**
     * Exposure mode
     */
    private String mode;

    /**
     * Public ingress host
     */
    private String host;

    /**
     * Public ingress TLS Secret name
     */
    private String tlsSecretName;

    /**
     * Get exposure mode
     */
    public String getMode() {
        return mode;
    }

    /**
     * Set exposure mode
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * Get public ingress host
     */
    public String getHost() {
        return host;
    }

    /**
     * Set public ingress host
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Get public ingress TLS Secret name
     */
    public String getTlsSecretName() {
        return tlsSecretName;
    }

    /**
     * Set public ingress TLS Secret name
     */
    public void setTlsSecretName(String tlsSecretName) {
        this.tlsSecretName = tlsSecretName;
    }
}
