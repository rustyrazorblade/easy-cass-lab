#!/usr/bin/env bash

echo "Enabling sysstat collection"
sudo sed -i 's/^ENABLED.*/ENABLED="true"/g' /etc/default/sysstat

sudo cp sysstat.cron /etc/cron.d/sysstat
sudo systemctl restart sysstat.service

