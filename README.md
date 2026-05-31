# Post-Consent Test Probe

`Post-Consent Test Probe` is a controlled Android app used to exercise the
updated `high-risk-app-isolation-system` prototype.

It is no longer just a generic risky sample. It is now structured around the
same five feature modules as the detector:

1. baseline profiling
2. runtime observation
3. outbound supervision
4. permission surface
5. audit snapshot

## What It Covers

- suspicious package label and broad declared permissions
- contacts, media, location, phone-state, microphone, and camera access
- package enumeration with `QUERY_ALL_PACKAGES`
- direct report upload to a configurable HTTP endpoint
- foreground-service upload chain for repeated outbound traffic
- delayed background `WorkManager` probe with optional upload and notification
- review notifications that can be tapped to reopen the probe
- local event logging, structured report persistence, and JSON audit export

## Why It Fits The Detector

This probe is designed to match the detector's current post-consent model:

- baseline risk surface detection
- identity, package-visibility, and network exposure review
- foreground versus background runtime observation
- notification-driven return-to-app behavior
- bounded `VpnService` validation against reproducible outbound traffic
- local evidence export for thesis screenshots and replay

## Suggested Demo Flow

1. Install this app on the same device as the detector prototype.
2. Grant contacts, media, location, phone-state, microphone, camera, and notification permissions.
3. Trigger `Refresh App Profile` and `Scan Installed Packages`.
4. Trigger `Read Contacts Snapshot`, `Scan Photos Snapshot`, `Capture Location`, `Read Phone State Snapshot`, `Record 5s Audio`, and `Take Camera Preview`.
5. Trigger `Upload Current Report` to create direct outbound activity.
6. Trigger `Start Foreground Upload Chain` to generate repeated foreground egress.
7. Trigger `Schedule Background Probe` and wait 15 seconds for background upload plus notification replay.
8. Tap the posted notification to generate a notification-return runtime path.
9. Export the JSON audit snapshot after the run.
10. In the detector prototype, select this app as the observation target and validate the five module outputs plus bounded VPN control.

## Build Notes

- Android Gradle Plugin: `8.5.2`
- Kotlin: `1.9.24`
- Min SDK: `26`
- Target SDK: `34`

Build with:

```bash
./gradlew assembleDebug
```
