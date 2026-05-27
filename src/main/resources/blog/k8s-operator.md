---
id: k8s-operator
title: Kubernetes Operator in Scala
description: Custom Kubernetes operator for managing stateful Scala microservices
tags:
  - Scala
  - Kubernetes
  - DevOps
  - Operators
githubUrl: https://github.com/example/k8s-operator
status: archived
year: 2023
---

A production-grade **Kubernetes operator** that automates deployment, scaling, and health management of Scala-based microservices, written using the fabric8 Kubernetes client.

The operator watches a custom `ScalaApp` CRD and reconciles the desired state: creating Deployments, Services, HorizontalPodAutoscalers, and PodDisruptionBudgets automatically.

Capabilities:

- Custom `ScalaApp` CRD with strongly typed spec
- Automatic canary rollout with traffic splitting
- JVM-aware health checks (heap, GC pause, thread pool saturation)
- Graceful shutdown coordination for ZIO apps
- Integration with Prometheus Operator for automatic scrape config
