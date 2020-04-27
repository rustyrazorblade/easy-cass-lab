#!/usr/bin/env bash


sudo bash -c "echo 1 > /proc/sys/kernel/perf_event_paranoid"
sudo bash -c "echo 0 > /proc/sys/kernel/kptr_restrict"

sudo mkdir /usr/local/cassandra-profiler

sudo tar -zxvf profiler.tgz -C /usr/local/cassandra-profiler

