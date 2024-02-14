# easy-cass-lab

This is a tool to create lab environments with Apache Cassandra.

Usage instructions (wip)

This project uses packer to create a base AMI with all versions of Cassandra, bcc tools, (and soon) async-profiler, easy-cass-stress, and other useful debugging tools.

Grab the repo and do the following to create your AMI.  You need to have an AWS profile set up already (needs verification)

```shell
cd packer
packer build cassandra.pkr.hcl
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

Now build the project:

```bash
./gradlew shadowJar installdist
```

Run this to provision the cluster:

```bash
bin/easy-cass-lab init  test test test # see https://github.com/rustyrazorblade/easy-cass-lab/issues/1
bin/easy-cass-lab up 
```

This is currently all that works as the rewrite is in progress.

This tool is a work in progress and is intended for developers to use to quickly launch clusters based on arbitrary builds.

If you aren't comfortable digging into code, this tool probably isn't for you, as you're very likely going to need to do some customizations.

Please refer to the project [documentation](http://rustyrazorblade.com/easy-cass-lab/) for usage instructions. 

Interested in contributing?  Check out the [good first issue tag](https://github.com/rustyrazorblade/easy-cass-lab/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) first!  Please read the [development documentation](http://rustyrazorblade.com/easy-cass-lab/development) before getting started.


