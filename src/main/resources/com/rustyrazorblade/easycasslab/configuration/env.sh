#@IgnoreInspection BashAddShebang

YELLOW='\033[0;33m'
YELLOW_BOLD='\033[1;33m'
NC_BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${YELLOW_BOLD}[WARNING]${YELLOW} We are creating aliases which override these commands:${NC}"
echo -e "${NC_BOLD}  ssh\n  sftp\n  scp\n  rsync\n${NC}"
echo "The aliases point the commands they override to your new cluster."
echo -e "To undo these changes exit this terminal.\n"

mkdir -p artifacts

SSH_CONFIG="$(pwd)/sshConfig"
alias ssh="ssh -F $SSH_CONFIG"
alias sftp="sftp -F $SSH_CONFIG"
alias scp="scp -F $SSH_CONFIG"
alias rsync="rsync -ave 'ssh -F $SSH_CONFIG'"

# general purpose function for executing commands on all cassandra nodes
c-all () {
    for i in "${SERVERS[@]}"
    do
        echo "Executing on $i"
        ssh $i $@
    done
}

c-dl () {
    for i in "${SERVERS[@]}"
    do
        ssh $i "sudo chown -R ubuntu /mnt/cassandra/artifacts/"
        rsync $i:/mnt/cassandra/artifacts/ artifacts/$i
    done
}

alias c-restart="c-all /usr/local/bin/restart-cassandra-and-wait"
alias c-status="c0 nodetool status"
alias c-tpstats="c-all nodetool tpstats"

alias c-start="c-all sudo systemctl start cassandra.service"
alias c-df="c-all df -h | grep -E 'cassandra|Filesystem'"


c-flame() {
  HOST=$1
  [ -z "$HOST" ] &&  echo "Host is required"

  if [[ $HOST =~ cassandra[0-9*] ]]; then
    mkdir -p artifacts/$1
    ssh $HOST -C /usr/local/bin/flamegraph "${@:2}"
    c-dl
  else
    echo "Host must be in the format cassandra[0-9*]."
  fi
}

c-flame-wall() {
  HOST=$1
  [ -z "$HOST" ] &&  echo "Host is required"

  if [[ $HOST =~ cassandra[0-9*] ]]; then
    mkdir -p artifacts/$1
    ssh $HOST -C /usr/local/bin/flamegraph -e wall -X '*Unsafe.park*'  -X '*Native.epollWait'  "${@:2}"
    c-dl
  else
    echo "Host must be in the format cassandra[0-9*]."
  fi
}

c-flame-compaction() {
  HOST=$1
  [ -z "$HOST" ] &&  echo "Host is required"

  if [[ $HOST =~ cassandra[0-9*] ]]; then
    mkdir -p artifacts/$1
    ssh $HOST -C /usr/local/bin/flamegraph -e wall -X '*Unsafe.park*' -X '*Native.epollWait' -I '*compaction*' "${@:2}"
    c-dl
  else
    echo "Host must be in the format cassandra[0-9*]."
  fi
}

c-flame-offcpu() {
  HOST=$1
  [ -z "$HOST" ] &&  echo "Host is required"

  if [[ $HOST =~ cassandra[0-9*] ]]; then
    mkdir -p artifacts/$1
    ssh $HOST -C /usr/local/bin/flamegraph -e kprobe:schedule -i 2 --cstack dwarf -X '*Unsafe.park*' "${@:2}"
    c-dl
  else
    echo "Host must be in the format cassandra[0-9*]."
  fi
}

c-flame-sepworker() {
  HOST=$1
  [ -z "$HOST" ] &&  echo "Host is required"

  if [[ $HOST =~ cassandra[0-9*] ]]; then
    mkdir -p artifacts/$1
    ssh $HOST -C /usr/local/bin/flamegraph -I '*SEPWorker*'  "${@:2}"
    c-dl
  else
    echo "Host must be in the format cassandra[0-9*]."
  fi
}

# Port forwarding functions for monitoring services
# Global variable to store port forwarding PIDs
PORT_FORWARD_PIDS=""

# Start SSH port forwarding for monitoring services
start-port-forwarding() {
  echo "Starting SSH port forwarding for monitoring services..."

  # Check if control0 exists in SSH config
  if ! grep -q "^Host control0" "$SSH_CONFIG" 2>/dev/null; then
    echo -e "${YELLOW}Warning: control0 not found in SSH config. Monitoring services may not be available.${NC}"
    return 1
  fi

  # Stop any existing port forwarding first
  stop-port-forwarding 2>/dev/null

  # Port forwarding for OpenSearch (9200)
  ssh -F "$SSH_CONFIG" -N -L 9200:localhost:9200 control0 &
  local OS_PID=$!
  PORT_FORWARD_PIDS="$PORT_FORWARD_PIDS $OS_PID"
  echo "  - OpenSearch forwarding started (localhost:9200 -> control0:9200) [PID: $OS_PID]"

  # Port forwarding for OpenSearch Dashboards (5601)
  ssh -F "$SSH_CONFIG" -N -L 5601:localhost:5601 control0 &
  local OSD_PID=$!
  PORT_FORWARD_PIDS="$PORT_FORWARD_PIDS $OSD_PID"
  echo "  - OpenSearch Dashboards forwarding started (localhost:5601 -> control0:5601) [PID: $OSD_PID]"

  # Port forwarding for MCP server (8000)
  ssh -F "$SSH_CONFIG" -N -L 8000:localhost:8000 control0 &
  local MCP_PID=$!
  PORT_FORWARD_PIDS="$PORT_FORWARD_PIDS $MCP_PID"
  echo "  - MCP server forwarding started (localhost:8000 -> control0:8000) [PID: $MCP_PID]"

  # Port forwarding for OTLP gRPC (4317) - for sending traces/metrics/logs
  ssh -F "$SSH_CONFIG" -N -L 4317:localhost:4317 control0 &
  local OTLP_GRPC_PID=$!
  PORT_FORWARD_PIDS="$PORT_FORWARD_PIDS $OTLP_GRPC_PID"
  echo "  - OTLP gRPC forwarding started (localhost:4317 -> control0:4317) [PID: $OTLP_GRPC_PID]"

  # Port forwarding for OTLP HTTP (4318) - for sending traces/metrics/logs via HTTP
  ssh -F "$SSH_CONFIG" -N -L 4318:localhost:4318 control0 &
  local OTLP_HTTP_PID=$!
  PORT_FORWARD_PIDS="$PORT_FORWARD_PIDS $OTLP_HTTP_PID"
  echo "  - OTLP HTTP forwarding started (localhost:4318 -> control0:4318) [PID: $OTLP_HTTP_PID]"

  echo -e "\n${NC_BOLD}Monitoring services are now accessible at:${NC}"
  echo "  - OpenSearch:            http://localhost:9200"
  echo "  - OpenSearch Dashboards: http://localhost:5601"
  echo "  - MCP server:            http://localhost:8000"
  echo "  - OTLP gRPC endpoint:    localhost:4317"
  echo "  - OTLP HTTP endpoint:    http://localhost:4318"
  echo ""
  echo "Use 'stop-port-forwarding' to stop all port forwarding."
}

# Stop SSH port forwarding
stop-port-forwarding() {
  echo "Stopping SSH port forwarding..."

  if [ -z "$PORT_FORWARD_PIDS" ]; then
    # Try to find existing SSH port forwarding processes
    local PIDS=$(ps aux | grep -E "ssh.*-L.*(9200|5601|8000|4317|4318).*control0" | grep -v grep | awk '{print $2}')
    if [ -n "$PIDS" ]; then
      echo "$PIDS" | while read pid; do
        if kill $pid 2>/dev/null; then
          echo "  - Stopped port forwarding process [PID: $pid]"
        fi
      done
    else
      echo "  - No active port forwarding processes found."
    fi
  else
    # Kill the PIDs we tracked
    for pid in $PORT_FORWARD_PIDS; do
      if kill $pid 2>/dev/null; then
        echo "  - Stopped port forwarding process [PID: $pid]"
      fi
    done
    PORT_FORWARD_PIDS=""
  fi

  echo "Port forwarding stopped."
}

# Alias for convenience
alias pf-start="start-port-forwarding"
alias pf-stop="stop-port-forwarding"

# Check port forwarding status
port-forwarding-status() {
  echo "Checking port forwarding status..."

  local PIDS=$(ps aux | grep -E "ssh.*-L.*(9200|5601|8000|4317|4318).*control0" | grep -v grep)
  if [ -n "$PIDS" ]; then
    echo -e "${NC_BOLD}Active port forwarding:${NC}"
    echo "$PIDS" | while read line; do
      local pid=$(echo "$line" | awk '{print $2}')
      if echo "$line" | grep -q "9200"; then
        echo "  - OpenSearch forwarding active [PID: $pid]"
      elif echo "$line" | grep -q "5601"; then
        echo "  - OpenSearch Dashboards forwarding active [PID: $pid]"
      elif echo "$line" | grep -q "8000"; then
        echo "  - MCP server forwarding active [PID: $pid]"
      elif echo "$line" | grep -q "4317"; then
        echo "  - OTLP gRPC forwarding active [PID: $pid]"
      elif echo "$line" | grep -q "4318"; then
        echo "  - OTLP HTTP forwarding active [PID: $pid]"
      fi
    done
  else
    echo "  - No active port forwarding found."
  fi
}

alias pf-status="port-forwarding-status"
