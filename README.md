# Package Sync Probe

`Package Sync Probe` is a controlled Android test app designed to mimic high-risk capability combinations for the `high-risk-app-isolation-system` project.

It is intentionally shaped to look risky to the prototype:

- package name contains `package`
- app label contains `Package`
- requests contacts, media, location, microphone, camera, package-visibility, and network capabilities
- can perform visible foreground collection actions
- can schedule a delayed background probe and optional upload

## What It Does

- requests a high-risk permission set at runtime
- reads contacts and shows a summary
- scans image metadata from `MediaStore`
- enumerates installed packages
- captures last-known location
- records a 5-second microphone sample
- saves a camera preview frame
- uploads the current report to a configurable HTTP endpoint
- schedules a background `WorkManager` probe that scans packages and can upload again
- keeps a local event log and the latest probe report

## Why It Fits The Detector

This app was built to exercise the detector's current strengths:

- baseline risk surface detection
- suspicious profile keywords
- sensitive-permission and network combinations
- package visibility exposure
- scenario-driven demonstrations for foreground versus background behavior
- bounded `VpnService` validation using a selected target app

## Suggested Demo Flow

1. Install this app on the same device as the detector prototype.
2. Grant contacts, media, location, microphone, and camera permissions.
3. Trigger `Read Contacts Snapshot`, `Scan Photos Snapshot`, and `Scan Installed Packages`.
4. Trigger `Upload Current Report` to create network activity.
5. Trigger `Schedule Background Probe` and wait 15 seconds.
6. In the detector prototype, select this app and compare strict versus compatibility mode outputs.
7. Use the detector's bounded `VpnService` path to test network control against this app.

## Build Notes

- Android Gradle Plugin: `8.5.2`
- Kotlin: `1.9.24`
- Min SDK: `26`
- Target SDK: `34`

This repository was scaffolded from scratch, so you may need a local JDK and Android SDK before running:

```bash
./gradlew assembleDebug
```
