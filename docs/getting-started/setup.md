# Setup

## Initial Configuration

If you've never used the tool before, the first time you run a command you'll be asked to supply some information. This will generate a configuration file placed in your `$HOME/.easy-db-lab/profiles/default/settings.yaml`.

!!! tip
    You can override the default `~/.easy-db-lab` directory by setting the `EASY_DB_LAB_USER_DIR` environment variable to a custom path.

!!! important
    We currently only support the Ubuntu 21 AMI.

## Verify Installation

Running the command without any arguments will print out the usage:

```bash
easy-db-lab
```

You should see the help output with available commands. See the [Commands Reference](../reference/commands.md) for details on each command.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `EASY_DB_LAB_USER_DIR` | Override the default configuration directory | `~/.easy-db-lab` |
