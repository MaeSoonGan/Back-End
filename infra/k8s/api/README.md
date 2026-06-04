# API Service Kubernetes Manifests

This directory contains the initial Kubernetes manifests for API services.

## Prerequisites

Create the secret in the cluster before syncing the workloads.

```bash
kubectl apply -f infra/k8s/api/namespace.yaml
cp infra/k8s/api/secret.example.yaml /tmp/maesoongan-api-secret.yaml
# Edit /tmp/maesoongan-api-secret.yaml with real values.
kubectl apply -f /tmp/maesoongan-api-secret.yaml
```

Do not commit real secret values.

## Apply

```bash
kubectl apply -k infra/k8s/api
```

## Images

The manifests use `latest` tags for the first deployment flow.
For stricter GitOps, update image tags to immutable commit SHA tags or add Argo CD Image Updater.
