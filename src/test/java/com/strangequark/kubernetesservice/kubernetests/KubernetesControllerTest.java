package com.strangequark.kubernetesservice.kubernetests;

import com.strangequark.kubernetesservice.kubernetes.KubernetesAutoscalingRequest;
import com.strangequark.kubernetesservice.kubernetes.KubernetesController;
import com.strangequark.kubernetesservice.kubernetes.KubernetesExposureRequest;
import com.strangequark.kubernetesservice.kubernetes.KubernetesScaleRequest;
import com.strangequark.kubernetesservice.kubernetes.KubernetesService;
import com.strangequark.kubernetesservice.kubernetes.KubernetesServiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link KubernetesController}
 */
@WebMvcTest(KubernetesController.class)
class KubernetesControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KubernetesService kubernetesService;

    /**
     * Test scaling a service with a request body
     */
    @Test
    void scaleService() throws Exception {
        ResponseEntity<?> response = ResponseEntity.accepted().body("authservice scaling started");
        doReturn(response).when(kubernetesService).scaleService(eq("token"), any(KubernetesScaleRequest.class));

        mockMvc.perform(post("/api/kubernetes/scale")
                        .header("X-CICD-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serviceName": "authservice",
                                    "replicas": 2
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(kubernetesService).scaleService(eq("token"), any(KubernetesScaleRequest.class));
    }

    /**
     * Test restarting a service with a request body
     */
    @Test
    void restartService() throws Exception {
        ResponseEntity<?> response = ResponseEntity.accepted().body("authservice restart started");
        doReturn(response).when(kubernetesService).restartService(eq("token"), any(KubernetesServiceRequest.class));

        mockMvc.perform(post("/api/kubernetes/restart")
                        .header("X-CICD-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serviceName": "authservice"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(kubernetesService).restartService(eq("token"), any(KubernetesServiceRequest.class));
    }

    /**
     * Test undeploying a service with a request body
     */
    @Test
    void undeployService() throws Exception {
        ResponseEntity<?> response = ResponseEntity.accepted().body("authservice undeployment started");
        doReturn(response).when(kubernetesService).undeployService(eq("token"), any(KubernetesServiceRequest.class));

        mockMvc.perform(post("/api/kubernetes/undeploy")
                        .header("X-CICD-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serviceName": "authservice"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(kubernetesService).undeployService(eq("token"), any(KubernetesServiceRequest.class));
    }

    /**
     * Test rolling back a service with a request body
     */
    @Test
    void rollbackService() throws Exception {
        ResponseEntity<?> response = ResponseEntity.accepted().body("authservice rollback started");
        doReturn(response).when(kubernetesService).rollbackService(eq("token"), any(KubernetesServiceRequest.class));

        mockMvc.perform(post("/api/kubernetes/rollback")
                        .header("X-CICD-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serviceName": "authservice"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(kubernetesService).rollbackService(eq("token"), any(KubernetesServiceRequest.class));
    }

    /**
     * Test configuring service autoscaling with a request body
     */
    @Test
    void configureAutoscaling() throws Exception {
        ResponseEntity<?> response = ResponseEntity.ok("authservice autoscaling configured successfully");
        doReturn(response).when(kubernetesService).configureAutoscaling(eq("token"), any(KubernetesAutoscalingRequest.class));

        mockMvc.perform(post("/api/kubernetes/autoscaling")
                        .header("X-CICD-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serviceName": "authservice",
                                    "minimumReplicas": 1,
                                    "maximumReplicas": 3,
                                    "targetCpuUtilization": 70
                                }
                                """))
                .andExpect(status().isOk());

        verify(kubernetesService).configureAutoscaling(eq("token"), any(KubernetesAutoscalingRequest.class));
    }

    /**
     * Test configuring service exposure with a request body
     */
    @Test
    void configureExposure() throws Exception {
        ResponseEntity<?> response = ResponseEntity.ok("authservice exposure updated to local");
        doReturn(response).when(kubernetesService).configureExposure(eq("token"), any(KubernetesExposureRequest.class));

        mockMvc.perform(post("/api/kubernetes/exposure")
                        .header("X-CICD-TOKEN", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serviceName": "authservice",
                                    "mode": "local"
                                }
                                """))
                .andExpect(status().isOk());

        verify(kubernetesService).configureExposure(eq("token"), any(KubernetesExposureRequest.class));
    }
}
