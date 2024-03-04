#!/usr/bin/env python3


######
# This is a work in progress. I don't know if we're going to use it.
# The goal is to be able to patch fragments of the JVM config,
# but I honestly don't know if there's a point.
# I started this with the assumption that it's similar to the C* config
# However looking at it now I realize I'm going to need to edit the file rather than patch it.
# I might come back to this later
######

# Examples:
# python patch-jvm-options.py -v 3.0 -i jvm.patch.options

# read the patch file line by line and index the lines

# read the base file line by line

# iterate over the base file.  if we see an option that's patched, use the patch version
# there's some extra logic we need to handle, like conflicting GC options
# some things are also exclusive, like -Xmn and -XX:MaxNewSize

# examples of valid JVM options

# -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1414
# -XX:+FlightRecorder
# -Xms4G
# -Dcassandra.force_default_indexing_page_size=true

import sys
import re
import argparse
import yaml

class JVMOption:
    key = None
    value = None
    sep = ""

    # regex to split on =

    def __init__(self, line):
        if "=" in line:
            self.sep = "="
            self.key, self.value = line.split("=")
        elif line.startswith("-Xm"):
            tmp = re.match("(-Xm[a-z])(.*)", line)
            self.sep = ""
            self.key = tmp[1]
            self.value = tmp[2]
        else:
            self.key = line
            self.value = ""
    # print representation of key and value
    def __repr__(self):
        return self.key + self.sep + self.value


#######################################################

# time to do the thing
parser = argparse.ArgumentParser(
    description="Patch JVM options file with a patch file"
)

group = parser.add_mutually_exclusive_group()

# either specify the output file or the version to patch
# when specifying a version, the output file is determined by cassandra_versions.yaml
group.add_argument("-o", "--output", help="Output file.", required=False)
group.add_argument("-v", "--version", help="Version of Cassandra to patch", required=False)

# we don't need the base
parser.add_argument("-b", "--base", help="Base config", required=False)
parser.add_argument("-p", "--patch", help="Patch file", required=True)
parser.add_argument("-c", help="cassandra_versions.yaml", default="/etc/cassandra_versions.yaml")

args = parser.parse_args()

## extracting all the variables here
patch = args.patch
base = args.base

## if we have a version, we can figure out the base file and the output file
if args.version:
    print("Version: " + args.version)
    with open(args.c, "r") as versions:
        data = yaml.safe_load(versions)
        if args.version in data:
            base = data[args.version]["jvm"]
            output = data[args.version]["jvm"]
        else:
            print("Version not found in cassandra_versions.yaml")
            sys.exit(1)

print(args)
sys.exit(1)



# iterate over the patch file line by line and index the lines
patch = {}
with open(args.patch, "r") as patch_file:
    for line in patch_file:
        if line.startswith("#") or line.strip() == "":
            continue
        option = JVMOption(line.strip())
        patch[option.key] = option

# some arguments are mutually exclusive


output = open(sys.argv[3], "w")

with open(sys.argv[1], "r") as base_file:
    for line in base_file:
        if line.startswith("#") or line.strip() == "":
            output.write(line)
            continue
        option = JVMOption(line.strip())
        if option.key in patch:
            print("Patching " + option.key + " with " + patch[option.key].value)
            print(patch[option.key])
            output.write(str(patch[option.key]) + "\n")
            # remove the patch entry
            del patch[option.key]
        else:
            print("Keeping " + option.key + " with " + option.value)
            print(option)
            output.write(str(option) + "\n")




# add any remaining patch entries
for key in patch:
    print("Adding " + key + " with " + patch[key].value)
    print(patch[key])
    output.write(str(patch[key]) + "\n")

output.close()
