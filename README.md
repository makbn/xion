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

## Commands

| Command | Description |
| --- | --- |
| `daemon` | Start/stop the background daemon (UDS, Seatbelt, proxies, SQLite). |
| `run` | Create a container profile and start it immediately. |
| `create` | Create a container record without starting it. |
| `start` | Start a created or stopped container by id or name. |
| `stop` | Stop a running container (SIGTERM, tear down proxy). |
| `ps` | List containers (running by default; `-a` for all). |
| `logs` | Print captured stdout or stderr for a container. |
| `inspect` | Show container details as JSON. |
| `update` | Change memory/CPU limits and/or bridge network. |
| `rm` | Remove a container from the store (`--force` to stop first). |
| `network` | Manage bridge networks with per-app IPs (`create`, `ls`, `inspect`, `rm`). |
| `docker` | Translate a Docker CLI command or Dockerfile into Xion. |
| `version` | Print the Xion version (daemon not required). |
| `help` | Show help for any subcommand (`xion help network`). |

### `docker` examples

`xion docker` (aliases: `from-docker`, `compat`) shows the mapped Xion argv, asks for
approval, then executes. Use `--yes` to skip the prompt and `--allow-partial` when
some Docker flags cannot be mapped 1:1.

**From a `docker run` command:**

```bash
./target/*-runner docker --yes --allow-partial -- \
  run -d --name web -p 8080:80 --memory 256m nginx
```

**From a Dockerfile** (maps `ENTRYPOINT`/`CMD` → `xion run`; build layers are not executed):

```bash
./target/*-runner docker -f Dockerfile --name web --publish-expose --yes
```

Dockerfile mode optionally maps `EXPOSE` → `-p N:N` (`--publish-expose`), `VOLUME` → `-v`
(`--volume-host-root DIR`), and `WORKDIR` via `sh -c 'cd … && exec …'`.

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

### Networking: same container port, different host ports

Each bridge network owns a subnet (default `10.89.N.0/24`). On start, Xion assigns the
container a unique loopback IP (via `lo0` alias on macOS), injects `XION_IP` /
`XION_NETWORK` / `PORT`, and reverse-proxies published host ports to
`containerIP:containerPort`.

Apps must bind to `$XION_IP:$PORT` (not `0.0.0.0`) so two containers can both use
listen port `8087`:

```bash
./target/*-runner network create frontend
# app1 → e.g. 10.89.0.2:8087  published as localhost:9000
./target/*-runner run --name app1 --network frontend -p 9000:8087 -- /path/to/app1
# app2 → e.g. 10.89.0.3:8087  published as localhost:9001
./target/*-runner run --name app2 --network frontend -p 9001:8087 -- /path/to/app2
```

Optional custom subnet: `xion network create backend --subnet 10.89.5.0/24`.
Managing `lo0` aliases typically requires elevated privileges for the daemon.

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
├── infrastructure.network    # Namespace IPs + reverse PortProxy + bridges
├── infrastructure.resources  # FFM setrlimit / taskpolicy governor
└── infrastructure.store      # SQLite container state
```

## Stack

- Java 25, Quarkus 3.33 LTS, quarkus-picocli
- JUnit 5, AssertJ, Mockito, `@QuarkusTest` / `@QuarkusMainTest`
- SQLite via Quarkus Agroal + JDBC SQLite
