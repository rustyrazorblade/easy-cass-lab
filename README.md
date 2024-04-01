# easy-cass-lab

This is a tool to create lab environments with Apache Cassandra in AWS.  

We use packer to create a single AMI with the following:

* Multiple versions of Cassandra
* [bcc tools](https://github.com/iovisor/bcc), [learn about these tools here](https://rustyrazorblade.com/post/2023/2023-11-14-bcc-tools/)
* [async-profiler](https://github.com/async-profiler/async-profiler), [learn about it here](https://rustyrazorblade.com/post/2023/2023-11-07-async-profiler/)
* [easy-cass-stress](https://github.com/rustyrazorblade/easy-cass-stress)
* [AxonOps agent](https://axonops.com/) (free monitoring up to six nodes)

easy-cass-lab provides tooling to create the AMI and provision the environments.

## Pre-requisites

The following must be set up before using this project:

* AWS Account with API Credentials - TODO links
* [Install Packer](https://developer.hashicorp.com/packer/install?ajs_aid=dc7c0e66-3245-44af-87cd-e692bd64d1df&product_intent=packer)
* [Install Docker](https://www.docker.com/products/docker-desktop/)

## Usage

Either clone and build the repo or grab a release.


### Download A Release

Download the latest [release](https://github.com/rustyrazorblade/easy-cass-lab/releases) and add the project's bin directory
to your PATH.

```shell
export PATH="$PATH:/Users/username/path/to/easy-cass-lab/bin"
```

You can skip to Create Your AMI.

### Optional: Build the Project

If you've downloaded a pre-built release you can skip this step.

If you're using the project repo you'll need to build the project. Fortunately, it's straightforward.

The following command should be run from the root directory of the project.
Docker will need to be running for this step.

```bash
git clone https://github.com/rustyrazorblade/easy-cass-lab.git
cd easy-cass-lab
./gradlew shadowJar installdist
```

### Build the Universal AMI

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

### Optional: Set the AMI 

When you create a cluster, you can optionally pass an `--ami`, or set `EASY_CASS_LAB_AMI`. 

If you don't specify an AMI, it'll use the latest AMI you've built.


### Create The Environment 

First create a directory for the environment, then initialize it, and start the instances.

```bash
mkdir cluster
cd cluster
easy-cass-lab init 
```

You can start your instances now.

```shell
easy-cass-lab up 
```

To access the cluster, follow the instructions at the end of the output of the `up` command:

```bash
source env.sh # to setup local environment with commands to access the cluster

# ssh to a node
ssh cassandra0
ssh cassandra1 # number corresponds to an instance
c0 # short cut to ssh cassandra0
```

### Select The Cassandra Version

While the nodes in the cluster are up, a version isn't yet selected.  Since the AMI contains multiple versions, you'll
need to pick one.

To see what versions are supported, you can do the following:

```shell
easy-cass-lab list
````

You'll see 3.0, 3.11, 4.0, 4.1, and others.

Choose your cassandra version.  

```shell
easy-cass-lab use 4.1
```

easy-cass-lab will automatically configure the right Python and Java versions on the instances for you.

### Optional: Modify the Configuration

You'll see a file called `cassandra.patch.yaml` in your directory.  You can add any valid cassandra.yaml parameters,
and the changes will be applied to your cluster.  The `listen_address` is handled for you, 
you do not need to supply it.  The data directories are set up for you. 

You can also edit `jvm.options`.  Different versions of Cassandra use different 
names for jvm.options.  `easy-cass-lab` handles this for you as well.

```shell
easy-cass-lab update-config # uc for short
```

### Start The Cluster

Start the cluster.  It will take about a minute to go through the startup process

```shell
easy-cass-lab start
```

### Log In and Have Fun!

**Important Directories:**

```shell
# The ephemeral or EBS disk is automatically formatted as XFS and mounted here.
/mnt/cassandra 

# data files
/mnt/cassandra/data

# hints
/mnt/cassandra/hints

# commitlogs
/mnt/cassandra/commitlog

# flame graphs
/mnt/cassandra/artifacts
```

Multiple cassandra versions are installed at /usr/local/cassandra.

The current version is symlinked as /usr/local/cassandra/current:

```shell
ubuntu@cassandra0:~$ readlink /usr/local/cassandra/current
/usr/local/cassandra/4.1
```

This allows us to support updating, mixed version clusters, A/B version testing, etc.


**Profiling**

Using easy-cass-lab env.sh you can run a profile, which will automatically download after it's complete:

```shell
c-flame cassandra0
```

The data will be saved in `artifacts/cassandra0`

Or on a node, generate flame graphs with `flamegraph`.

**Aliases**

| command | action                                |
| --------|---------------------------------------|
 | c | cqlsh (auto use the correct hostname) |
| ts | tail cassandra system log             |
| nt | nodetool                              | 
| d | cd to /mnt/cassandra/data directory   | 
| l | list /mnt/cassandra/logs directory    | 
| v | ls -lahG (friendly output)            |




### Shut it Down

To tear down the entire environment, simply run the following and confirm:

```shell
easy-cass-lab down
```

## Tools

bcc-tools is a useful package of tools 


Interested in contributing?  Check out the [good first issue tag](https://github.com/rustyrazorblade/easy-cass-lab/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) first!  Please read the [development documentation](http://rustyrazorblade.com/easy-cass-lab/development) before getting started.
