# Developer Notes

This doc is a catch all for anything that doesn't belong in the README.

## Finding Updated AMIs

```shell
aws ec2 describe-images \
--owners 099720109477 \
--filters \
"Name=name,Values=ubuntu/images/*ubuntu-jammy-22.04-amd64-server-*" \
"Name=state,Values=available" \
--query 'Images | sort_by(@, &CreationDate) | [0:10].[Name,ImageId,CreationDate]' \
--output table
```

Latest images:

```shell
aws ec2 describe-images \
--owners 099720109477 \
--filters \
"Name=name,Values=ubuntu/images/*jammy*" \
"Name=state,Values=available" \
--query 'Images | sort_by(@, &CreationDate) | reverse(@) [0:100].[Name,ImageId,CreationDate]' \
--output table
```