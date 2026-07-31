# Podman usage

The repository's OCI images and Compose file can be used with Podman:

```bash
podman compose up -d --build
podman ps
podman compose down -v
```

For Testcontainers with a rootless Podman socket, export the socket used by your installation, for example:

```bash
export DOCKER_HOST=unix://${XDG_RUNTIME_DIR}/podman/podman.sock
systemctl --user start podman.socket
mvn -pl quality-engineering-tests test
```

The exact socket path differs by operating system and Podman installation.
