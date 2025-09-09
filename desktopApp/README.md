# RealmsAI Desktop Bootstrap (Compose Multiplatform - Desktop)

Minimal starter for a Windows `.exe` using Compose Desktop.
Copies your mobile Material 3 palette and opens a themed window.

## Prereqs
* **JDK 17+** (Temurin 17 or 21 recommended)
* Either **Gradle 8.x installed locally** or use the **Gradle wrapper** included in this project.  The wrapper provides scripts (`gradlew` and `gradlew.bat`) and a configuration file but **does not include** the small bootstrap JAR (`gradle/wrapper/gradle-wrapper.jar`).  See the instructions below for using the wrapper.

## Run (dev)

### Using a local Gradle installation

If you already have Gradle 8.x installed, you can run the project directly:

```bash
gradle :desktopApp:run
```

### Using the Gradle wrapper

If you don't have Gradle installed, you can still build the project using the included wrapper scripts.  First, copy `gradle/wrapper/gradle-wrapper.jar` from the original RealmsAI repository (or generate it by running `gradle wrapper` once with a local Gradle installation) into `gradle/wrapper/`.  Then run:

**On Windows:**

```cmd
.\gradlew.bat :desktopApp:run
```

**On Unix/macOS:**

```bash
./gradlew :desktopApp:run
```

## Package Windows .exe
```bash
gradle :desktopApp:packageExe
```

Artifacts are created under `desktopApp/build/compose/binaries`.

## Next steps
* Create a `:shared` KMP module and move your models/session/prompt logic there.
* Wire Firebase via GitLive Firebase Kotlin SDK or Google Cloud clients.
* Port Android screens into Compose Desktop screens reusing your theme.