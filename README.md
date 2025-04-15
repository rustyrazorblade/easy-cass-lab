# easy-cass-lab

This is a tool to create lab environments with Apache Cassandra in AWS.  Using this tool you can:

* Quickly create an environment using any version of Cassandra from 2.2 up to trunk
* Build custom AMIs with your own branches
* Test mixed configurations of Cassandra and java versions
* Run load tests using easy-cass-stress
* Profile Cassandra and generate flame graphs
* Collect kernel metrics with bcc-tools 

We use packer to create a single AMI with the following:

* Multiple versions of Cassandra
* [bcc tools](https://github.com/iovisor/bcc), [learn about these tools here](https://rustyrazorblade.com/post/2023/2023-11-14-bcc-tools/)
* [async-profiler](https://github.com/async-profiler/async-profiler), [learn about it here](https://rustyrazorblade.com/post/2023/2023-11-07-async-profiler/)
* [easy-cass-stress](https://github.com/rustyrazorblade/easy-cass-stress)
* [AxonOps agent](https://axonops.com/) (free monitoring up to six nodes)

## Pre-requisites

The following must be set up before using this project:

* [Setup AWS Account API Credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html)
* [Install Docker](https://www.docker.com/products/docker-desktop/)

## Usage

To install `easy-cass-lab`, you can use Homebrew, download a release, or clone the project and build it.

### Install A Release using Homebrew

```shell
brew tap rustyrazorblade/rustyrazorblade
brew install easy-cass-lab
```

`easy-cass-lab` should now be available for you to use.

Skip ahead to Read The Help.

### Download A Release

Download the latest [release](https://github.com/rustyrazorblade/easy-cass-lab/releases) and add the project's bin directory
to your PATH.

```shell
export PATH="$PATH:/Users/username/path/to/easy-cass-lab/bin"
```

You can skip ahead to Read The Help.

### Build the Project

The following command should be run from the root directory of the project.
Docker will need to be running for this step.

```bash
git clone https://github.com/rustyrazorblade/easy-cass-lab.git
cd easy-cass-lab
./gradlew shadowJar 
```


#### Build the Universal AMI

Using easy-cass-lab requires building an AMI if you're building from source.

You can skip this if you're using us-west-2 
This can be done once, and reused many times.
The AMI should be rebuilt when updating easy-cass-lab.  

The first time you build an image, easy-cass-lab will ask you for your AWS credentials.

```shell
bin/easy-cass-lab build-image
```

At the end, you'll see something like this:

```text
==> Builds finished. The artifacts of successful builds are:
--> cassandra.amazon-ebs.ubuntu: AMIs were created:
us-west-2: ami-abcdeabcde1231231
```

That means you're ready!

### Read the Help

Run `easy-cass-lab` without any parameters to view all the commands and all options.

### Create The Environment 

Note: The first time you create an environment, it'll prompt you for your credentials.

Important: If you've installed the project via homebrew or downloaded a release, 
please use the `us-west-2` region.  This limitation will be lifted soon.

First create a directory for the environment, then initialize it, and start the instances.

This directory is your working space for the cluster.

```bash
mkdir cluster
cd cluster
easy-cass-lab init -c 3 -s 1 myclustername # 3 node cluster with 1 stress instance
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

This will also create a local directory corresponding to the name of the version, and will download most of the files in the conf directory to your local dir.  You can edit them, and upload with:

```shell
easy-cass-lab update-config
```

You can override the java version by passing the `-j` flag:

```shell
easy-cass-lab use 5.0 -j 17
```

Doing this will update each nodes local copy of `/etc/cassandra_versions.yaml`.  

You can switch just one host:

```shell
easy-cass-lab use 5.0 -j 17 --hosts cassandra0
```

Unlike production tools, easy-cass-lab is designed for testing and breaking things, which I find is the best way to learn.

### Modify the YAML Configuration

You'll see a file called `cassandra.patch.yaml` in your directory.  You can add any valid cassandra.yaml parameters,
and the changes will be applied to your cluster.  The `listen_address` is handled for you, 
you do not need to supply it.  The data directories are set up for you. 

You can also edit the JVM options files under the different local version directories. Different versions of Cassandra use different 
names for jvm.options.  Edit the ones in the directory that corresponds to the version you're using.

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

# axonops agents for different versions of Cassandra
/usr/local/share/axonops
```

Multiple cassandra versions are installed at /usr/local/cassandra.

The current version is symlinked as /usr/local/cassandra/current:

```shell
ubuntu@cassandra0:~$ readlink /usr/local/cassandra/current
/usr/local/cassandra/4.1
```

This allows us to support updating, mixed version clusters, A/B version testing, etc.


**Profiling with Flame Graphs**

https://rustyrazorblade.com/post/2023/2023-11-07-async-profiler/

Using easy-cass-lab `env.sh`, you can run a profile and generate a flamegraph, 
which will automatically download after it's complete by doing the following:

```shell
c-flame cassandra0
```

The data will be saved in `artifacts/cassandra0`

Or on a node, generate flame graphs with `flamegraph`.

There are several convenient aliases defined in env.sh. 
You may substitute any cassandra host.  
You may pass extra parameters, they will be passed along automatically.


| Command                 | Description                                                                              |
|-------------------------|------------------------------------------------------------------------------------------|
| `c-flame`      | CPU Flame graph                                                                          |
| `c-flame-wall` | wall clock profiling, picks up I/O, parked threads filtered out                          |
| `c-flame-compaction` | More specific wall clock profiling to compaction                                         |
| `c-flame-offcpu ` | Just tracks time spent when cpu is unscheduled, mostly I/O                               |
| `c-flame-sepworker`       | Request handling, by default this is CPU time. You can add -e wall to make it wall time. |


**Aliases**

On each node there are several aliases for commonly run commands:

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

https://rustyrazorblade.com/post/2023/2023-11-14-bcc-tools/


Interested in contributing?  Check out the [good first issue tag](https://github.com/rustyrazorblade/easy-cass-lab/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) first!  Please read the [development documentation](http://rustyrazorblade.com/easy-cass-lab/development) before getting started.
