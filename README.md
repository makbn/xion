# Xion

Xion is a macOS Apple Silicon container runtime: a Quarkus Picocli CLI/daemon that
isolates processes with Seatbelt (`sandbox-exec`), publishes ports via a loopback
NIO proxy, applies resource limits (FFM `setrlimit` / `taskpolicy`), and persists
container state in SQLite.

**Target:** native executable for **macOS aarch64** with **Mandrel / GraalVM 25**.
This project builds and runs tests on any OS in JVM mode; Darwin-only tests are
skipped on Linux.

## Why Xion?

Docker on a Mac does **not** run Linux containers on the Apple chip directly. It runs
them inside a **Linux virtual machine**. That VM is great for “same Linux as
production,” but it sits between your app and the real hardware.

Because of that VM wall, workloads often **cannot use the full Apple Silicon stack**:
Media Engine (hardware encode/decode), Neural Engine, Metal GPU, and other
macOS-only accelerators stay on the host side. You pay VM overhead, and you miss
the silicon features that make M-series Macs fast at video, ML, and graphics.

Xion takes a different path: it runs **real macOS processes** on the host, fenced
with **Seatbelt** sandboxes (plus port proxying and resource limits). No Linux VM
in the middle — so your process can talk to the Media Engine and the rest of the
chip the way a normal Mac app would.

### Pros

- **Uses the silicon** — Media Engine, GPU/Metal, Neural Engine, and other host
  hardware are available to sandboxed processes (when the app supports them).
- **Less overhead** — no Linux VM tax for CPU, memory, or I/O on every container.
- **Mac-native feel** — Docker-ish CLI (`run`, `ps`, `logs`, `network`, `docker`
  translator) while staying on Darwin APIs.
- **Lighter isolation model** — Seatbelt profiles instead of a full guest OS.

### Cons

- **Not Linux containers** — you run host executables / Mac-oriented workflows, not
  OCI Linux images from Docker Hub as-is. Production parity with Linux servers is
  weaker than Docker Desktop.
- **macOS Apple Silicon only** — Mandrel native builds and Seatbelt are Darwin/aarch64
  focused; this is not a general Linux container engine.
- **Weaker / different isolation** — Seatbelt is process sandboxing, not a VM or
  full kernel namespace stack. Threat model differs from Docker-on-Linux.
- **Smaller ecosystem** — no full Docker networking, volumes, or build pipeline;
  `xion docker` translates what it can and drops the rest.

**Rule of thumb:** use Docker when you need “same Linux as the cloud.” Use Xion when
you want container-style lifecycle on a Mac **and** you care about Apple Silicon
hardware (video, ML, GPU) without a VM in the way.

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

Examples below assume the native binary is available as `./target/*-runner`
(or copy/symlink it to `xion` on your `PATH`).

## CLI help

Every command has Docker-style detailed help (description, options, examples):

```bash
./target/*-runner --help
./target/*-runner run --help
./target/*-runner docker --help
./target/*-runner help network
```

### Docker → Xion translator

`xion docker` (aliases: `from-docker`, `compat`) accepts a Docker CLI command **or a
Dockerfile**, shows the mapped Xion argv, asks for approval, then executes:

```bash
# Interactive from a docker run line
./target/*-runner docker -- run -d --name web -p 8080:80 nginx

# From a Dockerfile (ENTRYPOINT/CMD → xion run)
./target/*-runner docker -f Dockerfile --name web --yes
./target/*-runner docker --dockerfile ./deploy/Dockerfile \
  --dry-run --volume-host-root /srv/data
./target/*-runner docker -f Dockerfile --name api -- \
  --memory 512m -p 8080:8080

# Skip confirmation + allow unsupported flags to be dropped
./target/*-runner docker --yes --allow-partial -- \
  run -it --rm -e FOO=1 -p 8080:80 --memory 256m nginx:latest

# Preview only / skip bad port specs
./target/*-runner docker --dry-run --allow-partial --partial-ports -- \
  run -p 80 -p 9000:90 /usr/bin/sleep 30
```

Dockerfile mode maps **ENTRYPOINT + CMD** to `xion run`, optionally `EXPOSE` → `-p N:N`
(`--publish-expose`), `VOLUME` → `-v` (`--volume-host-root DIR`), and `WORKDIR` via
`sh -c 'cd … && exec …'`. Build layers (`FROM`/`RUN`/`COPY`/…) are not executed.

Useful flags: `--yes`/`-y`, `--dry-run`, `--print-only`, `--allow-partial`,
`--drop-unsupported`, `--partial-ports`, `--keep-image-tag`,
`-f`/`--dockerfile`, `--publish-expose` / `--no-publish-expose`, `--volume-host-root`,
`--no-workdir`.

## Example workflow

```bash
# Terminal 1 — start daemon (Unix domain socket ~/.xion/xion.sock)
./target/*-runner daemon start

# Terminal 2 — create a bridge, run a process, list, logs, stop
./target/*-runner network create frontend
./target/*-runner run \
  --name web --network frontend -p 8080:80 --memory 256m --cpus 1 \
  -- /usr/bin/sleep 60
./target/*-runner ps
./target/*-runner logs web
./target/*-runner stop web
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
