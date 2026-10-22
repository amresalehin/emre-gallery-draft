# Contributing

Use `Plan.md` for product behavior and `Task.md` for implementation acceptance criteria. Keep the README honest about implemented, partially implemented and future functionality.

## Development

- Kotlin and Jetpack Compose; target Android SDK 35, minimum SDK 26.
- Prefer stable MediaStore content URIs over filesystem paths.
- Keep local library work offline-first and avoid unnecessary permissions.
- Label unconfigured AI/OCR/map/face functionality as unavailable instead of rendering fabricated results.
- Add tests for state transitions and data filtering with every significant change.

## Build

Open the root project in Android Studio and run `assembleDebug` and `testDebugUnitTest`. The repository's CI workflow runs these steps on pull requests.
