# Accepts 3 arguments
# Path to base JVM options file
# Path to patch JVM options file
# Path to output JVM options file
#
# Example: python patch-jvm-options.py \
#               /usr/local/cassandra/current/conf/jvm.orig.options \
#               /home/ubuntu/jvm.options \
#               /usr/local/cassandra/current/conf/jvm.options

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


# iterate over the patch file line by line and index the lines
patch = {}
with open(sys.argv[2], "r") as patch_file:
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
