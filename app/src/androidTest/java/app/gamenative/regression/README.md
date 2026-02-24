# GameNative Deterministic Stability – Regression Suite

This directory holds the contract for regression testing verified titles.

## Scope

- **Boot test**: Does the game reach menu (or first render)?
- **Preflight**: Do verified titles pass preflight on reference devices?
- **Device matrix**: Adreno (Snapdragon), Mali (Exynos) – when available.

## Usage

1. Maintain a small list of verified AppIDs and their known-good profile ids in `verified_titles.json`.
2. In CI (or locally), before release:
   - Run preflight for each verified title (mock or real device).
   - Fail the build if a previously passing title fails preflight or no longer reaches menu.

## verified_titles.json (optional)

```json
{
  "entries": [
    { "steamAppId": 123456, "profileId": "default", "minSdk": 26 }
  ]
}
```

## Notes

- Full automated boot tests require a device or emulator with the game installed; often run manually.
- The preflight and launch path are the main regression surface; this suite documents the policy.
