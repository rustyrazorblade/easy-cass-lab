#!/usr/bin/env bash

# anything that should be executed after Cassandra was started

cassandra_running() {
  nc -zv $(hostname) 9042 > /dev/null 2>&1
}

if [[ "$1" == "" ]]; then
echo "Pass a provisioning argument please"
exit 1
fi

if [[ "$1" == "cassandra" ]]; then
    # Wait for Cassandra to be fully started
    for i in $(seq 90); do
      if cassandra_running; then
            break
      fi
      echo "Cassandra is not running yet..."
      sleep 1s
    done

    echo "Running all shell scripts"
    # subshell
    (
    cd "$1/post_start"
    for f in $(ls [0-9]*.sh)
    do
        bash ${f}
    done

    echo "Done with shell scripts"
    )
fi

