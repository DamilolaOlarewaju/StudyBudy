# StudyBudy 🎓

StudyBudy is an AI-powered academic tutor and exam coach designed for MIVA Open University students. It brings guided study support into a modern Android experience so learners can revise, ask questions, and prepare more confidently.

## Highlights

- AI-assisted academic guidance
- Exam-preparation support
- Android app built with Jetpack Compose
- Local persistence with Room
- Network communication with Retrofit and OkHttp
- Structured JSON parsing with Moshi
- PDF-related study workflows with iText
- Firebase Analytics integration
- Kotlin coroutines for asynchronous work

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Android SDK 36
- Room
- Retrofit
- OkHttp
- Moshi
- Firebase Analytics
- Kotlin Coroutines
- Gradle Kotlin DSL

## Requirements

- Android Studio with Android SDK 36 support
- JDK 11
- An Android device or emulator running API 24 or newer
- A configured Gemini/API environment value as described in `.env.example`

## Getting started

1. Clone the repository:

   ```bash
   git clone https://github.com/DamilolaOlarewaju/StudyBudy.git
   cd StudyBudy
   ```

2. Open the project in Android Studio.

3. Create the local environment file from the example:

   ```bash
   cp .env.example .env
   ```

4. Add the required API configuration to `.env` without committing secrets.

5. Sync Gradle and run the `app` configuration on an emulator or connected device.

## Build from the command line

```bash
./gradlew assembleDebug
```

On Windows, use:

```powershell
.\\gradlew.bat assembleDebug
```

## Project status

StudyBudy is an actively evolving project. Future improvements may include richer learning plans, expanded course coverage, improved offline support, and additional testing coverage.

## Security note

Never commit `.env`, API keys, keystores, or signing passwords. Use the provided environment template and local or CI secret storage instead.

## License

No license has been specified yet. Until a license is added, all rights are reserved by the project owner.
