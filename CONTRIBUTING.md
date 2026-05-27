# Contributing

Thank you for your interest in contributing to **PinDownloader**!

## Getting Started

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/your-username/pin-downloader.git
   ```
3. Open in Android Studio
4. Build with:
   ```bash
   ./gradlew assembleDebug
   ```

## Guidelines

- **Keep it GPL-compatible**: All contributions must be licensed under GPL-3.0 or any compatible license.
- **No trackers or ads**: Do not introduce analytics, advertising, or tracking SDKs. Keep the app privacy-respecting.
- **F-Droid friendly**: Avoid proprietary dependencies. Prefer open-source libraries available via standard repositories (Google/Maven Central).
- **Issue first**: Open an issue before starting significant work to discuss the approach.
- **Pull requests**:
  - Keep changes focused and minimal
  - Do not rename packages or change the app's core purpose
  - Test your changes with `./gradlew assembleDebug`
  - Update metadata (README, CHANGELOG) if applicable

## Code Style

This project uses standard Kotlin conventions. Follow the style of existing code — no special formatting rules beyond what Android Studio applies by default.

## Questions

Open a GitHub Discussion for questions or ideas.
