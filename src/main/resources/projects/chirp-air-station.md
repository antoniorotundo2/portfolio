---
id: "chirp-air-station"
title: "Chirp Air Station"
description: "Distributed urban weather & air-quality station: LoRa sensor nodes, an MQTT pipeline and a React dashboard"
tags:
  - IoT
  - ESP32
  - LoRa
  - MQTT
  - Node.js
  - MongoDB
  - WebSocket
  - Microservices
  - React
  - Docker
githubUrl: "https://github.com/antoniorotundo2/chirp-air-station"
status: "archived"
year: 2023
---

**Chirp Air Station** is a complete IoT service that measures **temperature, pressure, humidity and air-quality index** across a connected urban environment. It spans the full stack — from battery-powered sensor firmware up to a containerized backend and a real-time web dashboard.

The system is organised in four layers: low-power **edge** sensor nodes, a **Wi-Fi gateway** that bridges them to the Internet, a **service** backend that ingests and stores the data, and a **web client** that visualises it.

Chirp Air Station was a **multi-disciplinary university project**, developed across three exams — *Web Applications & Services*, *Distributed Systems* and *Project Management* — each adding a different lens: the web/IoT stack, the distributed architecture, and the planning and management process.

![System architecture](/static/img/cas_architecture.png)

## Edge: sensor nodes

Each sensor node pairs an **ESP32** (LILYGO LoRa board) with a **BME680** environmental sensor over I²C. The node samples the four metrics, packs them into a compact message and transmits over **LoRa** — a long-range, low-power radio link that lets nodes run on battery far from any Wi-Fi access point. The firmware is written in C++ on the **Arduino / PlatformIO** framework.

![Sensor node: ESP32 LoRa board with BME680](/static/img/cas_hardware.jpg)

## Gateway & data pipeline

A Wi-Fi-connected ESP32 acts as the **LoRa→IP gateway**: it receives the radio packets and republishes them to a public **MQTT broker** (HiveMQ). On the server side a small **Node.js** reader subscribes to the broker and persists every reading into **MongoDB**, while a second Node.js **web service** exposes the data to clients. The whole backend — MQTT reader, database and web service — is orchestrated with **Docker Compose** for one-command deployment.

## Web client

A **React** single-page application lets users browse their devices on a map, inspect per-device gauges for temperature, humidity and air quality, and drill into a device for live charts and a full history of measurements.

![Dashboard with device map and per-device metrics](/static/img/cas_dashboard.png)

![Real-time device view with live chart and history](/static/img/cas_device_realtime.png)

## Distributed-systems design

Beyond the stack, the platform was designed as a proper **distributed system**, organised into conceptual *fields* — Edge, IP-Local, Internet and Service — each with its own devices, protocols and responsibilities. The backend is split into two cleanly separated **microservices** with distinct concerns:

- the **MQTT Reader** owns *ingestion* — it subscribes to the sensor topic, **deduplicates** incoming messages and persists them to MongoDB;
- the **Web Service** stays **stateless** with respect to readings — it exposes authenticated REST APIs (split into User, Sensor and Reads routers) and pushes **real-time updates over WebSocket**, relaying each live reading straight to the connected client without storing it.

This separation of responsibilities lets the Web Service **scale horizontally** behind a load balancer. The sequence diagram below traces a full interaction, from a LoRa packet leaving a sensor all the way to a real-time update in the browser:

![Web Service interaction sequence — from LoRa packet to real-time browser update](/static/img/cas_sequence.png)

## Project management

Chirp Air Station was also run as a full **project-management** exercise, with the complete lifecycle documented: a Project Description Statement, a **Work Breakdown Structure**, a Resource Breakdown Structure, a **Gantt schedule**, a risk register, quality-assurance and deployment plans, and a closeout report. The Gantt below decomposes the work into firmware, gateway, MQTT and database services, web service and web client phases. Among the recorded **lessons learned**: testing time was underestimated, and hardware/software compatibility should be validated earlier during prototyping.

![Project Gantt chart](/static/img/cas_gantt.png)

The repository is organised as a multi-module project (Git submodules): `cas-sensor-lora` and `cas-sensor-gateway` for the firmware, `cas-mqtt-service` for ingestion, and `cas-web-service` for the backend and React client.
