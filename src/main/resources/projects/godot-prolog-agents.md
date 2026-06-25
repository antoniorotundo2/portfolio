---
id: "godot-prolog-agents"
title: "POLARIS"
description: "Multi-agent system with declarative Prolog logic in real-time 3D simulations on Godot"
tags:
  - Scala
  - Prolog
  - Godot
  - Cats Effect
  - WebSocket
  - Multi-Agent Systems
githubUrl: "https://github.com/antoniorotundo2/godot-prolog-agents"
status: "archived"
year: 2026
---

**POLARIS** (Prolog Orchestrated Logic Agents for Real-time Interactive Simulation) is a multi-agent system that cleanly separates three concerns: 3D physics simulation in **Godot 4**, declarative decision logic in **Prolog**, and an orchestration bridge written in **Scala 3**.

Each Godot agent collects percepts from the simulated world (sensors, collisions, distances) and sends them via WebSocket to the Scala server. The server updates the agent's state, invokes the Prolog engine (tuProlog 3.3.0) with the agent's theory, and returns the action to execute. Godot then applies physics and local behaviour accordingly.

The system supports per-agent Prolog theories: each agent can load its own `.pl` file from the Godot inspector and push it dynamically to the server, which binds the theory to the agent's id for all subsequent decisions. If no theory is provided, the server falls back to its built-in `logic.pl`.

The back-end is built with **Cats Effect** and **fs2** for concurrent WebSocket connection handling, **Kleisli** for functional dependency injection, and **EitherT** for explicit error management across parsing and domain logic. It also includes a per-agent energy model and a 30ms decision reuse window to reduce CPU load and eliminate oscillations at high tick frequencies.

Four demo scenarios are included:

**Simple Agent Test** — two agent types with distinct Prolog theories, with runtime spawning.

![Simple Agent Test](/static/img/simple_agents_test_demo.png)

**Top-down Tank Test** — two teams of tanks sharing a Prolog theory per team, fighting to eliminate each other.

![Tank Test](/static/img/tank_test_demo.png)

**Soccer Test** — two agents competing to push a rigid-body ball into the opponent's goal.

![Soccer Test](/static/img/soccer_test_demo.png)

**Vehicle Test** — circular road with a four-way intersection, traffic lights, right-of-way rules, and safety distances simulated via raycasts.

![Vehicle Test](/static/img/vehicles_test_demo.png)
