# Prerequisites

Before using easy-db-lab, ensure you have the following:

## AWS Credentials

- An AWS access key and secret. easy-db-lab creates and destroys infrastructure.
- You will be prompted for these the first time you start easy-db-lab.
- The access key needs permissions to:
  - Create an S3 bucket
  - Create SSH keys
  - Manage EC2 instances

!!! note
    Separate keys are used by default for security reasons.

## System Requirements

- **Operating System**: Mac or Linux (shell script dependency)
- **Java**: JDK 21 or later (for running the application)

## Recommended Setup

We **highly** recommend setting up proper AWS credential management. See the [AWS CLI documentation](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html) for best practices.
