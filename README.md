# Xion

Xion is a macOS Apple Silicon container runtime: a Quarkus Picocli CLI/daemon that
isolates processes with Seatbelt (`sandbox-exec`), publishes ports via a loopback
NIO proxy, applies resource limits (FFM `setrlimit` / `taskpolicy`), and persists
container state in SQLite.

**Target:** native executable for **macOS aarch64** with **Mandrel / GraalVM 25**.
This project builds and runs tests on any OS in JVM mode; Darwin-only tests are
skipped on Linux.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | **25** LTS |
| Maven | Wrapper included (`./mvnw`) |
| Mandrel / GraalVM | **25** (native builds on macOS only) |

## Build & test (JVM)

```bash
./mvnw test
./mvnw package
```

Run the CLI (JVM):

```bash
java -jar target/quarkus-app/quarkus-run.jar --help
java -jar target/quarkus-app/quarkus-run.jar version
```

## Native build (macOS Apple Silicon)

1. Install Mandrel 25 (or GraalVM 25) for **darwin-aarch64** and set:

   ```bash
   export GRAALVM_HOME=/path/to/mandrel-25
   export PATH="$GRAALVM_HOME/bin:$PATH"
   ```

2. Build with no-fallback native image:

   ```bash
   ./mvnw package -Dnative
   ```

3. The runner binary is `target/xion-1.0.0-SNAPSHOT-runner` (name may vary slightly
   by Quarkus packaging). Verify:

   ```bash
   ./target/*-runner version
   ./target/*-runner --help
   ```

> **Note:** A Linux CI/agent **cannot** produce the macOS native binary. Configure
> and document Mandrel 25 locally on Apple Silicon; use `./mvnw test` on Linux.

Native flags are set in `application.properties`:

- `quarkus.native.additional-build-args=--no-fallback,...`

## Example workflow

```bash
# Terminal 1 — start daemon (Unix domain socket ~/.xion/xion.sock)
java -jar target/quarkus-app/quarkus-run.jar daemon start

# Terminal 2 — create a bridge, run a process, list, logs, stop
java -jar target/quarkus-app/quarkus-run.jar network create frontend
java -jar target/quarkus-app/quarkus-run.jar run \
  --name web --network frontend -p 8080:80 --memory 256m --cpus 1 \
  -- /usr/bin/sleep 60
java -jar target/quarkus-app/quarkus-run.jar ps
java -jar target/quarkus-app/quarkus-run.jar logs web
java -jar target/quarkus-app/quarkus-run.jar stop web
```

On macOS, `run`/`start` generate Seatbelt profiles under `/tmp/xion-profiles/` and
spawn via `sandbox-exec`. On Linux (CI), a fake sandbox executor runs the binary
directly so unit/integration tests pass without Darwin APIs.

## Layout

```
io.xion
├── presentation.cli          # Picocli xion top command + subcommands
├── application.mediator      # Mediator + IPC dispatcher + daemon
├── application.handlers      # Create/Start/Stop/Logs/List/Network
├── domain                    # ContainerProfile, ResourceLimits, …
├── infrastructure.ipc        # Length-prefixed JSON over UDS
├── infrastructure.seatbelt   # .sb generator + SandboxExecutor
├── infrastructure.network    # NIO port proxy + bridge resolver
├── infrastructure.resources  # FFM setrlimit / taskpolicy governor
└── infrastructure.store      # SQLite container state
```

## Stack

- Java 25, Quarkus 3.33 LTS, quarkus-picocli
- JUnit 5, AssertJ, Mockito, `@QuarkusTest` / `@QuarkusMainTest`
- SQLite via Quarkus Agroal + JDBC SQLite
