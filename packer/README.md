Build the docker container from scratch:

```shell
docker build --no-cache -t ecl .
```

Build the docker container using cached layers:

```shell
docker build -t ecl . 
```

To start the test env:

```shell
docker compose run --rm ecl
```

Test an install script:

```shell
bash 
```
