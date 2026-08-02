# Podman Usage

The standalone pack does not include a root Compose file. To run its optional
observability stack with Podman, use the Compose file explicitly:

On macOS, start the Podman machine first. A `connection refused` error for the
`127.0.0.1` Podman connection means that this VM is stopped.

```bash
podman machine start
```

Then run the stack:

```bash
podman compose -f observability/docker-compose.observability.yml up -d
podman ps
podman compose -f observability/docker-compose.observability.yml down -v
```

After applying this pack to the parent framework, run that framework's Compose
commands from its repository root.

## Testcontainers

The LocalStack integration test uses Testcontainers. Podman must be running and
the Maven process must be pointed at its Docker-compatible API socket.

### macOS

Create and start the Podman VM once:

```bash
podman machine init --now
```

For later sessions, start it before running containers:

```bash
podman machine start
```

Podman 6 rootless machines on macOS can reject Testcontainers' socket bind
mount with an `operation not permitted` error. Use the rootful machine mode for
this repository's Testcontainers tests:

```bash
podman machine stop
podman machine set --rootful
podman machine start
```

Run the test with the dynamically discovered socket path:

```bash
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
mvn -pl quality-engineering-tests -Dtest=LocalStackS3IntegrationTest test
```

The socket path is temporary on macOS, so derive it with `podman machine
inspect` for each terminal session rather than adding a fixed path to shell
configuration. No `podman-mac-helper` installation is required when using this
socket.

### Linux Rootless Podman

Enable the user socket, then point Testcontainers at it:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
mvn -pl quality-engineering-tests -Dtest=LocalStackS3IntegrationTest test
```

`TESTCONTAINERS_RYUK_DISABLED=true` is required for rootless Podman because
Ryuk requires container privileges that rootless Podman does not grant.

## Diagnostics

```bash
podman machine list       # macOS
podman system connection list
podman info
podman ps -a
```

If `podman info` cannot connect on macOS, start the machine. If the
Testcontainers test cannot create a container and reports an SELinux-label or
socket-mount error, switch the machine to rootful mode using the commands
above.
