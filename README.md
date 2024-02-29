# easy-cass-lab

This is a tool to create lab environments with Apache Cassandra.

## Pre-requisites

The following must be set up before using this project:

* AWS Account - TODO links
* [Install Packer](https://developer.hashicorp.com/packer/install?ajs_aid=dc7c0e66-3245-44af-87cd-e692bd64d1df&product_intent=packer)
* [Install Docker](https://www.docker.com/products/docker-desktop/)

## Usage instructions (wip)

This project uses packer to create a base AMI with all versions of Cassandra, bcc tools, (and soon) async-profiler, easy-cass-stress, and other useful debugging tools.

Grab the repo and do the following to create your AMI.  You need to have an AWS profile set up already (needs verification)

```shell
cd packer
packer init cassandra.pkr.hcl # only needs to be run the first time you setup the project
packer build base.pkr.hcl # build the base image 
packer build cassandra.pkr.hcl # extends the base image
```

You'll get a bunch of output, at the end you'll see something like this:

```text
==> Builds finished. The artifacts of successful builds are:
--> cassandra.amazon-ebs.ubuntu: AMIs were created:
us-west-2: ami-abcdeabcde1231231
```

Grab the AMI and set the following environment variable (or pass it every time with `--ami`),
you'll probably want it in your .bash_profile or .zshrc

```shell
# substitute the AMI created in the above command
export EASY_CASS_LAB_AMI="ami-abcdeabcde1231231" 
```

Now build the project. The following command should be run from the root directory of the project. Docker will need to be running for this step.

```bash
./gradlew shadowJar installdist
```

Run this to start the nodes in the cluster. Once the nodes are online, it will run several post-setup commands, including
whatever is in the disk setup script

```bash
bin/easy-cass-lab init [cluster_name] # optional cluster name, uses "test" if not specified 
bin/easy-cass-lab up 
```

To access the cluster afterards follow the instructions at the end of the output of the `up` command:

```bash
source env.sh # to setup local environment with commands to access the cluster

# ssh to a node
ssh cassandra0
ssh cassandra1 # number corresponds to an instance
```

Choose your cassandra version.  This will 

```shell

bin/easy-cass-lab use 4.0
```

Start the cluster.  It will take about a minute to go through the startup process

```shell
bin/easy-cass-lab start
```

This tool is a work in progress and is intended for developers to use to quickly launch clusters based on arbitrary builds.

## Tools Installed

* bcc-tools
* async-profiler
* AxonOps agent (free monitoring!)

Interested in contributing?  Check out the [good first issue tag](https://github.com/rustyrazorblade/easy-cass-lab/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) first!  Please read the [development documentation](http://rustyrazorblade.com/easy-cass-lab/development) before getting started.

## Development

```shell
docker-compose run ubuntu
```

