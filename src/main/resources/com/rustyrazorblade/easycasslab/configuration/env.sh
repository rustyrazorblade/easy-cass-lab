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
