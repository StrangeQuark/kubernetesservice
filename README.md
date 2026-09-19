# KubernetesService

Kubernetes infrastructure bundle for MSINIT stacks.

This repository provides the shared Kubernetes control plane for MSINIT stacks.
It owns Kubernetes manifests and exposes an API that deploys selected services.

## Local launch

Copy `.env.example` to `.env`, configure the desired exposure modes, and run:

```bash
bash launch_kubernetes.sh
```

The script creates the local Kind cluster with every MSINIT local port mapping,
installs the ingress controller for public exposure, installs metrics-server
for local autoscaling, and deploys KubernetesService. It does not deploy
application services.

The initial Kind cluster creation reserves the MSINIT localhost ports. After
that one-time setup, services can switch between disabled, local, and public
exposure without recreating the cluster.

KubernetesService supports AuthService, EmailService, FileService, VaultService,
GatewayService, TelemetryService, LoggerService, ReactService, and JenkinsService.
Deploy a selected service through the API:

```bash
curl -X POST \
  -H "X-CICD-TOKEN: <KUBERNETES_CICD_TOKEN>" \
  -F serviceName=authservice \
  -F image=authservice:local \
  -F environmentFile=@../authservice/.env \
  http://localhost:6011/api/kubernetes/deploy
```

Each service's manifests live under `k8s/<service-name>/`, so KubernetesService
does not rely on adjacent service checkouts.

## CI/CD deployment configuration

Kubernetes deployment selection belongs in Jenkins or GitHub Actions configuration,
not in a downstream service environment file. Configure these values per CI job or
repository:

```ini
KUBERNETES_ENABLED=true
SERVICE_IMAGE_REPOSITORY=authservice
KUBERNETESERVICE_URL=http://localhost:6011
KUBERNETES_CICD_TOKEN=change-me
```

`KUBERNETES_CICD_TOKEN` must be stored as a CI secret. The other values can be CI
environment variables. KubernetesService owns cluster configuration, including the
namespace and LoggerService Kubernetes log tailing settings.

## Operations

- `GET /api/kubernetes/services`: lists services with bundled manifests.
- `GET /api/kubernetes/status`: returns every service's deployment status.
- `POST /api/kubernetes/scale`: starts an application Deployment replica count change.
- `POST /api/kubernetes/restart`: starts a rolling application restart.
- `POST /api/kubernetes/rollback`: starts an application Deployment rollback by one revision.
- `POST /api/kubernetes/autoscaling`: configures a CPU-based Horizontal Pod Autoscaler.
- `POST /api/kubernetes/exposure`: switches a deployed service between disabled, local,
  and public exposure.
- `POST /api/kubernetes/undeploy`: starts removal of an application's Deployment, Service,
  ingress, and autoscaler while preserving persistent data.

Status responses include rollout counts and safe Pod/container diagnostics, including
restart counts, image-pull failures, and the most recent container termination reason.

Every operational endpoint requires `X-CICD-TOKEN`.

Deployment, scale, restart, rollback, and undeploy return `202 Accepted` after
Kubernetes accepts the requested change. Poll `GET /api/kubernetes/status` to
confirm the resulting workload state. Operational `POST` endpoints other than
`/deploy` accept JSON request bodies; `/deploy` remains multipart so it can
receive an optional environment file.

## Exposure modes

- `disabled`: service is available only to other Kubernetes services.
- `local`: service is available through its normal localhost port via a Kind NodePort.
- `public`: service uses a pre-installed production ingress controller and TLS Secret.

The `.env` exposure values are defaults for initial deployments. Runtime
`POST /api/kubernetes/exposure` requests are stored in Kubernetes and take
precedence for future deployments.
