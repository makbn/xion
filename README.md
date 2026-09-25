# Xion

Xion is a macOS Apple Silicon container runtime. It isolates host processes with
Seatbelt (`sandbox-exec`), publishes ports through a reverse proxy, applies
memory and CPU limits, and keeps container state in SQLite. The CLI talks to a
background daemon over a Unix domain socket (`~/.xion/xion.sock`).

**Target platform:** native executable for **macOS aarch64** (Mandrel / GraalVM 25).
JVM builds and tests also run on Linux; Darwin-only checks are skipped there.

## Why Xion?

Docker on a Mac does **not** run Linux containers on the Apple chip directly. It
runs them inside a **Linux virtual machine**. That is useful for production
parity with Linux servers, but the VM sits between your app and the hardware.

Because of that, workloads often cannot use the full Apple Silicon stack: Media
Engine, Neural Engine, Metal GPU, and other macOS accelerators stay on the host.
You pay VM overhead and miss the silicon that makes M-series Macs fast at video,
ML, and graphics.

Xion runs **real macOS processes** on the host, sandboxed with Seatbelt, with
port publishing and resource limits. There is no Linux VM in the path — so apps
can use Apple silicon the way a normal Mac process would.

| | Pros | Cons |
| --- | --- | --- |
| Hardware | Direct access to Media Engine, Metal, Neural Engine (when the app supports them) | Not Linux/OCI containers; weaker cloud parity than Docker Desktop |
| Overhead | No Linux VM tax on CPU, memory, or I/O | macOS Apple Silicon focused |
| Isolation | Seatbelt process sandbox (lighter than a guest OS) | Not a VM or full kernel namespace model |
| Workflow | Familiar CLI (`run`, `ps`, `logs`, `network`, `docker` translator) | Smaller ecosystem; Docker flags map only partially |

**Rule of thumb:** use Docker for “same Linux as the cloud.” Use Xion when you
want container-style lifecycle on a Mac and care about Apple Silicon hardware.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 25 LTS |
| Maven | Wrapper included (`./mvnw`) |
| Mandrel / GraalVM | 25 (native image on macOS only) |

## Build

JVM (tests / packaging):

```bash
./mvnw test
./mvnw package
```

Native binary (macOS Apple Silicon) — output is explicitly named **`xion`**:

```bash
export GRAALVM_HOME=/path/to/mandrel-25
export PATH="$GRAALVM_HOME/bin:$PATH"

./mvnw package -Dnative
# → target/xion
```

Install on your `PATH` (pick one):

```bash
cp target/xion /usr/local/bin/xion
# or: ln -s "$(pwd)/target/xion" /usr/local/bin/xion
# or: export PATH="$(pwd)/target:$PATH"
```

Verify:

```bash
xion version
xion --help
```

A Linux CI host cannot produce the macOS binary. Build native locally on Apple
Silicon; use `./mvnw test` on Linux.

## Commands

| Command | Description |
| --- | --- |
| `daemon` | Start/stop the background daemon |
| `run` | Create and start a container |
| `create` | Create a container without starting it |
| `start` | Start a created or stopped container |
| `stop` | Stop a container; tear down proxy, release IP/alias |
| `ps` | List containers (`-a` for all statuses) |
| `logs` | Print captured stdout or stderr |
| `inspect` | Show container JSON (includes allocated IP when set) |
| `update` | Change memory/CPU and/or network |
| `rm` | Remove a stopped container and clean network state |
| `network` | Manage bridges with per-app IPs (`create`, `ls`, `inspect`, `rm`) |
| `docker` | Translate a Docker CLI command or Dockerfile |
| `version` | Print version (daemon not required) |
| `help` | Help for any subcommand |

```bash
xion --help
xion run --help
xion network --help
```

### Stop and remove

`stop` always releases the container’s reverse-proxy mappings, network endpoint,
allocated IP, and loopback alias.

`rm` refuses to delete a **running** container (same idea as Docker). Use either:

```bash
xion stop web
xion rm web
# or in one step:
xion rm --force web
```

Both paths clean process, port proxy, IP/alias, and SQLite state before the
record is gone.

## Networking

Each bridge owns a private subnet (default `10.89.N.0/24`, or `--subnet CIDR`).
On start, Xion assigns a unique loopback IP (`lo0` alias on macOS), sets
`XION_IP`, `XION_NETWORK`, and `PORT`, and reverse-proxies host ports to
`containerIP:containerPort`.

Apps must bind `$XION_IP:$PORT` (not `0.0.0.0`) so two containers can share the
same listen port:

```bash
xion network create frontend
# optional: xion network create frontend --subnet 10.89.5.0/24

xion run --name app1 --network frontend -p 9000:8087 -- /path/to/app1
xion run --name app2 --network frontend -p 9001:8087 -- /path/to/app2
# localhost:9000 → 10.89.0.2:8087
# localhost:9001 → 10.89.0.3:8087

xion network inspect frontend   # subnet, gateway, member IPs
xion rm --force app1            # releases IP + host port mapping
```

Managing `lo0` aliases typically requires elevated privileges for the daemon.

## Docker compatibility

`xion docker` (aliases: `from-docker`, `compat`) maps a Docker CLI line or
Dockerfile to Xion, shows the result, then runs it (use `--yes` to skip the
prompt; `--allow-partial` to drop unsupported flags).

```bash
xion docker --yes --allow-partial -- \
  run -d --name web -p 8080:80 --memory 256m nginx

xion docker -f Dockerfile --name web --publish-expose --yes
```

Dockerfile mode maps `ENTRYPOINT`/`CMD` (and optionally `EXPOSE` / `VOLUME` /
`WORKDIR` / `ENV` → `-e`). Build layers (`FROM`/`RUN`/`COPY`/…) are not executed.

## Docker parity (env, workdir, rm, restart, logs)

Xion supports a subset of Docker run/logs flags natively:

```bash
# Environment (env-file first; -e overrides)
xion run -e FOO=1 --env-file ./app.env -- /usr/bin/myapp

# Working directory
xion run -w /tmp -- /usr/bin/pwd

# Auto-remove on exit
xion run --rm -- /usr/bin/true

# Restart policy (no|on-failure[:N]|always|unless-stopped)
# v1: on restart the container is fully torn down (new IP may be assigned)
xion run --restart on-failure:3 -- /usr/bin/flaky

# Logs: last N lines and client-side follow
xion logs --tail 100 web
xion logs -f web
```

`xion docker` maps `-e` / `--env-file` / `-w` / `--rm` / `--restart` / `-f` / `--tail`
onto these flags.

## Example workflow

```bash
# Terminal 1
xion daemon start

# Terminal 2
xion network create frontend
xion run \
  --name web --network frontend -p 8080:80 --memory 256m --cpus 1 \
  -- /usr/bin/sleep 60
xion ps
xion logs web
xion stop web
xion rm web
```
