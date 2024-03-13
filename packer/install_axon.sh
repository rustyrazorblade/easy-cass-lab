# setup the axon repo
sudo apt-get update
sudo apt-get install -y curl gnupg ca-certificates
curl -L https://packages.axonops.com/apt/repo-signing-key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/axonops.gpg
echo "deb [arch=arm64,amd64 signed-by=/usr/share/keyrings/axonops.gpg] https://packages.axonops.com/apt axonops-apt main" | sudo tee /etc/apt/sources.list.d/axonops-apt.list
sudo apt-get update

sudo apt-get install -y axon-agent


# download each version of the axon agent
# unpack each one into /tmp so the files can be placed in the desired places
for v in $(yq '.[].axonops' /etc/cassandra_versions.yaml);
do
  if [[ $v == "null" ]]; then
    # axonops doesn't publish releases for beta versions
    continue
  fi
  sudo apt install --download-only axon-cassandra$v-agent

  tempdir=/tmp/axonops/$v
  targetdir=/usr/local/cassandra/$v/lib
  jar=axon-cassandra$v-agent.jar
  mkdir -p $tempdir
  # find the file since axon package version is unknown
  file=$(sudo find /var/cache/apt/archives/ -name "axon-cassandra$v*")
  echo "unpacking $file to $tempdir for copy into $targetdir (jar=$jar)"
  dpkg-deb -xv $file $tempdir
  sudo cp $tempdir/usr/share/axonops/$jar $targetdir
  sudo chown cassandra:cassandra $targetdir/$jar

  # create the EnvironmentFile used by the systemd unit to wire up the agent
  envfile=/usr/local/cassandra/$v/conf/axonenv.template
  echo "JVM_EXTRA_OPTS=\"-javaagent:$targetdir/$jar=/etc/axonops/axon-agent.yml\"" | sudo tee $envfile
  sudo chown cassandra:cassandra $envfile
done

# install agent_service file included with every version since its expected
# to be in a specific place
v=$(yq '.[0].version' /etc/cassandra_versions.yaml)
mkdir -p /var/lib/axonops
sudo cp /tmp/axonops/$v/var/lib/axonops/agent_service /var/lib/axonops
sudo chown -R cassandra:cassandra /var/lib/axonops

# permissions setup
sudo chmod 0644 /etc/axonops/axon-agent.yml
sudo usermod -aG cassandra axonops
sudo usermod -aG axonops cassandra




