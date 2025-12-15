# Installation

## Tarball Install

You can grab a tarball from the [releases page](https://github.com/rustyrazorblade/easy-db-lab/releases).

To get started, add the bin directory of easy-db-lab to your `$PATH`. For example:

```bash
export PATH="$PATH:/path/to/easy-db-lab/bin"
cd /path/to/easy-db-lab
./gradlew assemble
```

## Building from Source

If you prefer to build from source:

```bash
git clone https://github.com/rustyrazorblade/easy-db-lab.git
cd easy-db-lab
./gradlew assemble
```

The built distribution will be in `build/distributions/`.
