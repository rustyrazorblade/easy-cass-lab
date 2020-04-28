#!/usr/bin/env bash

echo "Installing Grafana"
export APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=true
export DEBIAN_FRONTEND=noninteractive

sudo add-apt-repository "deb https://packages.grafana.com/oss/deb stable main"
sudo wget -q -O - https://packages.grafana.com/gpg.key | apt-key add -
sudo apt-get update
sudo apt-get install -y grafana

sudo cp config/grafana/grafana.ini /etc/grafana/

mkdir -p /etc/grafana/provisioning/datasources/
sudo cp config/grafana/datasource.yaml /etc/grafana/provisioning/datasources/
sudo chown root:grafana /etc/grafana/provisioning/datasources/datasource.yaml

sudo mkdir -p  /var/lib/grafana/dashboards

sudo cp config/grafana/dashboards.yaml /etc/grafana/provisioning/dashboards/
sudo cp dashboards/*.json /var/lib/grafana/dashboards

sudo chown -R grafana /var/lib/grafana/dashboards/

sudo /bin/systemctl daemon-reload
sudo /bin/systemctl enable grafana-server
