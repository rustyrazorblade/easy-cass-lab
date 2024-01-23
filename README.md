# tlp-cluster

Usage instructions (wip)

This project uses packer to create a base AMI with all versions of Cassandra, bcc tools, (and soon) async-profiler, tlp-stress, and other useful debugging tools.

```shell
cd packer
packer build cassandra.pkr.hcl
```

Grab the AMI and set the following environment variable (or pass it every time with `--ami`)

```shell
# substitute the AMI created in the above command
export TLP_CLUSTER_AMI="ami-abcdefg" 
```

I'm currently moving the project in a new direction and doing so without regard for breaking old features. 


TODO:

* Remove the old ubuntu ami code
* Remove all the old install code
* Start the right version on `start` command
* populate the config and push it up on the install command (maybe rename to load-config?)
* Add option to pass AxonOps account information for monitoring instead of prometheus and grafana

Grab the source and build locally:

```bash
./gradlew assemble installdist
```

[![CircleCI](https://circleci.com/gh/thelastpickle/tlp-cluster.svg?style=svg)](https://circleci.com/gh/thelastpickle/tlp-cluster)

This is a tool to aid in [Apache Cassandra](https://cassandra.apache.org/) development.  

Please see our [Google Group](https://groups.google.com/d/forum/tlp-dev-tools) for discussion.

This tool is a work in progress and is intended for developers to use to quickly launch clusters based on arbitrary builds.

If you aren't comfortable digging into code, this tool probably isn't for you, as you're very likely going to need to do some customizations.

Please refer to the project [documentation](http://thelastpickle.com/tlp-cluster/) for usage instructions. 

Interested in contributing?  Check out the [good first issue tag](https://github.com/thelastpickle/tlp-cluster/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) first!  Please read our [development documentation](http://thelastpickle.com/tlp-cluster/development) before getting started.


