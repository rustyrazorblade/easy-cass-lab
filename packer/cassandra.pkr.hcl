packer {
  required_plugins {
    amazon = {
      version = ">= 1.2.8"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

locals {
  timestamp = regex_replace(timestamp(), "[- TZ:]", "")
}

source "amazon-ebs" "ubuntu" {
  ami_name      = "cassandra-${local.timestamp}"
  instance_type = "t2.micro"
  region        = "us-west-2"
  source_ami_filter {
    filters = {
      name                = "ubuntu/images/*ubuntu-jammy-22.04-amd64-server-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    most_recent = true
    owners      = ["099720109477"]
  }
  ssh_username = "ubuntu"
}

build {
  name    = "cassandra"
  sources = [
    "source.amazon-ebs.ubuntu"
  ]
  provisioner "shell" {
      inline = [
        "sudo apt-get update",
        "sudo apt-get install wget"
      ]
  }

  provisioner "shell" {
    script = "install_cassandra.sh"
  }

  provisioner "shell" {
    script = "install_bcc.sh"
  }
}


