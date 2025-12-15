# Docker Development

## Building Docker Containers

Each container is versioned and can be built locally using the following:

```bash
./gradlew :PROJECT-NAME:buildDocker
```

Where `PROJECT-NAME` is one of the subproject directories you see in the top level.

## Setup

We recommend updating your local Docker service to use 8GB of memory. This is necessary when running dashboard previews locally. The preview is configured to run multiple Cassandra containers at once.

## Available Docker Projects

Check the root project directory for subprojects prefixed with `docker-` to see available containerized components.

## Local Testing

To test containers locally:

1. Build the container:
   ```bash
   ./gradlew :docker-cassandra:buildDocker
   ```

2. Run the container:
   ```bash
   docker run -it <image-name>
   ```

## Memory Requirements

| Use Case | Recommended Memory |
|----------|-------------------|
| Single container development | 4GB |
| Dashboard preview (multiple containers) | 8GB |
| Full integration testing | 16GB |
