#!/bin/bash

set -e
set -o pipefail

print_usage() {
    echo "Usage: ./launch_kubernetes.sh [cluster-name] [namespace]"
    echo ""
    echo "cluster-name and namespace default to values in kubernetesservice/.env"
}

get_env_value() {
    grep "^$1=" "$configuration_file" | cut -d '=' -f2- || true
}

check_command() {
    local command="$1"

    if ! command -v "$command" >/dev/null 2>&1; then
        echo "$command must be installed"
        exit 1
    fi
}

get_kind_command() {
    local kind_command
    kind_command=$(command -v kind)

    if [ -n "$kind_command" ]; then
        echo "$kind_command"
        return
    fi

    local machine
    machine=$(uname -m)

    case "$machine" in
        x86_64)
            machine="amd64"
            ;;
        aarch64|arm64)
            machine="arm64"
            ;;
        *)
            echo "Unsupported architecture: $machine"
            exit 1
            ;;
    esac

    mkdir -p "$kubernetes_service_folder/.tools"
    kind_command="$kubernetes_service_folder/.tools/kind"

    if [ ! -f "$kind_command" ]; then
        echo "Downloading Kind" >&2
        check_command curl
        curl -fsSL "https://kind.sigs.k8s.io/dl/v0.33.0/kind-linux-$machine" -o "$kind_command"
        chmod +x "$kind_command"
    fi

    echo "$kind_command"
}

if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    print_usage
    exit 0
fi

kubernetes_service_folder=$(cd "$(dirname "$0")" && pwd)
configuration_file="$kubernetes_service_folder/.env"
manifests_folder="$kubernetes_service_folder/k8s"

if [ ! -f "$configuration_file" ]; then
    echo "KubernetesService .env file was not found"
    exit 1
fi

cluster_name="${1:-$(get_env_value KUBERNETES_CLUSTER_NAME)}"
namespace="${2:-$(get_env_value KUBERNETES_NAMESPACE)}"
cluster_name="${cluster_name:-msinit}"
namespace="${namespace:-msinit}"

for ingress_mode in $(grep "_INGRESS_MODE=" "$configuration_file" | cut -d '=' -f2-); do
    if [ "$ingress_mode" != "disabled" ] && [ "$ingress_mode" != "local" ] && [ "$ingress_mode" != "public" ]; then
        echo "Ingress modes must be disabled, local, or public"
        exit 1
    fi

done

check_command docker
check_command kubectl
kind_command=$(get_kind_command)

if ! "$kind_command" get clusters | grep -qx "$cluster_name"; then
    echo "Creating Kind cluster: $cluster_name"

    mkdir -p "$kubernetes_service_folder/.tools"
    cat > "$kubernetes_service_folder/.tools/kind-local-config.yaml" <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30001
        hostPort: 6001
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30005
        hostPort: 6005
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30010
        hostPort: 6010
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30011
        hostPort: 6011
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30020
        hostPort: 6020
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30030
        hostPort: 6030
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30050
        hostPort: 6050
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30080
        hostPort: 6080
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30180
        hostPort: 1080
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30081
        hostPort: 8080
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30100
        hostPort: 6100
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 30040
        hostPort: 6040
        listenAddress: "127.0.0.1"
        protocol: TCP
      - containerPort: 51820
        hostPort: 51820
        listenAddress: "0.0.0.0"
        protocol: UDP
EOF
    "$kind_command" create cluster --name "$cluster_name" \
        --config "$kubernetes_service_folder/.tools/kind-local-config.yaml" --wait 5m
else
    echo "Kind cluster already exists: $cluster_name"

    for host_port in 1080 6001 6005 6010 6011 6020 6030 6040 6050 6080 8080 6100 51820; do
        if ! docker inspect "$cluster_name-control-plane" | grep -q '"HostPort"[[:space:]]*:[[:space:]]*"'"$host_port"'"'; then
            echo "The existing Kind cluster was not created with MSINIT local port mappings"
            echo "Delete and recreate it with: $kind_command delete cluster --name $cluster_name"
            exit 1
        fi
    done

    if ! docker inspect "$cluster_name-control-plane" | grep -q '"HostPort"[[:space:]]*:[[:space:]]*"80"'; then
        echo "The existing Kind cluster was not created with public ingress port mappings"
        echo "Delete and recreate it with: $kind_command delete cluster --name $cluster_name"
        exit 1
    fi
fi

context="kind-$cluster_name"
kubernetes_service_image=$(get_env_value KUBERNETES_SERVICE_IMAGE)
kubernetes_service_image="${kubernetes_service_image:-kubernetesservice:local}"

kubectl --context "$context" create namespace "$namespace" --dry-run=client -o yaml \
    | kubectl --context "$context" apply -f -

echo "Building KubernetesService image: $kubernetes_service_image"
docker build -t "$kubernetes_service_image" "$kubernetes_service_folder"

echo "Loading KubernetesService image into Kind"
"$kind_command" load docker-image "$kubernetes_service_image" --name "$cluster_name"

echo "Creating KubernetesService environment Secret"
kubectl --context "$context" -n "$namespace" create secret generic kubernetes-service-env \
    --from-env-file="$configuration_file" \
    --dry-run=client \
    -o yaml | kubectl --context "$context" -n "$namespace" apply -f -

echo "Installing ingress controller"
kubectl --context "$context" label node "$cluster_name-control-plane" ingress-ready=true --overwrite
kubectl --context "$context" apply -f \
    https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/kind/deploy.yaml
kubectl --context "$context" wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=120s

echo "Installing metrics server"
kubectl --context "$context" apply -f \
    https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.0/components.yaml
kubectl --context "$context" -n kube-system patch deployment metrics-server --type=json \
    -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl --context "$context" -n kube-system rollout status deployment/metrics-server --timeout=120s

echo "Deploying KubernetesService"
sed "s|__KUBERNETES_SERVICE_IMAGE__|$kubernetes_service_image|g" "$manifests_folder/kubernetes-service.yaml" \
    | kubectl --context "$context" -n "$namespace" apply -f -
kubectl --context "$context" -n "$namespace" delete service kubernetes-service --ignore-not-found
sleep 1
kubectl --context "$context" -n "$namespace" rollout restart deployment/kubernetes-service
kubectl --context "$context" -n "$namespace" patch service k8-service --type merge \
    -p '{"spec":{"type":"NodePort","ports":[{"name":"http","port":6011,"targetPort":6011,"nodePort":30011}]}}'
kubectl --context "$context" -n "$namespace" delete ingress kubernetes-service --ignore-not-found

echo "Waiting for KubernetesService"
kubectl --context "$context" -n "$namespace" rollout status deployment/kubernetes-service --timeout=10m

echo ""
echo "KubernetesService is running in Kind."
echo ""

echo "Access KubernetesService locally:"
echo "http://localhost:6011/api/kubernetes/health"
