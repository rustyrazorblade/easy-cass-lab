# Publishing

## Pre-Release Checklist

1. First check CI to ensure the build is clean and green
2. Ensure the following environment variables are set:
   - `DOCKER_USERNAME`
   - `DOCKER_PASSWORD`
   - `DOCKER_EMAIL`

## Publishing Steps

### Build and Upload

```bash
./gradlew buildAll uploadAll
```

### Post-Release

After publishing, bump the version in `build.gradle.kts`.

## Container Publishing

Containers are automatically published to GitHub Container Registry (ghcr.io) when:

- A version tag (v*) is pushed
- PR Checks pass on main branch

See `.github/workflows/publish-container.yml` for details.

## Documentation

Documentation is automatically built and deployed via GitHub Actions when changes are pushed to the `docs/` directory on the main branch.
