packer {
  required_plugins {
    amazon = {
      version = ">= 1.2.8"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

variable "arch" {
  type = string
  default = "amd64"
}

variable "region" {
  type = string
  default = "us-west-2"
}

variable "release_version" {
  type    = string
  default = ""
}

locals {
  timestamp = regex_replace(timestamp(), "[- TZ:]", "")
  version = var.release_version != "" ? var.release_version : local.timestamp
  # We need to use a Graviton instance type for arm
  instance_type = var.arch == "amd64" ? "c3.xlarge" : "c8g.2xlarge"
}

source "amazon-ebs" "ubuntu" {
  ami_name      = "rustyrazorblade/images/easy-cass-lab-base-${var.arch}-${local.version}"
  instance_type = local.instance_type
  region        = "${var.region}"
  source_ami_filter {
    filters = {
      name                = "ubuntu/images/*ubuntu-jammy-22.04-${var.arch}-server-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    most_recent = true
    owners      = ["099720109477"]
  }
  ssh_username = "ubuntu"
  launch_block_device_mappings {
    device_name = "/dev/sda1"
    volume_size = 16
    volume_type = "gp2"
    delete_on_termination = true
  }
}

build {
  name    = "easy-cass-lab"
  sources = [
    "source.amazon-ebs.ubuntu"
  ]

  provisioner "shell" {
    script = "install/prepare_instance.sh"
  }

  provisioner "shell" {
    inline = [
      # bpftrace was removed b/c it breaks bcc tools, need to build latest from source
      "sudo wget https://github.com/mikefarah/yq/releases/download/v4.41.1/yq_linux_${var.arch} -O /usr/local/bin/yq",
      "sudo chmod +x /usr/local/bin/yq",
    ]
  }

  # install pyenv and python
  provisioner "shell" {
    script = "install/install_python.sh"
  }

  provisioner "shell" {
    script = "install/install_fio.sh"
  }

  # install async profiler
  provisioner "shell" {
    script = "install/install_async_profiler.sh"
  }


  provisioner "shell" {
    script = "install/install_bcc.sh"
  }

  # install OpenTelemetry Collector
  provisioner "shell" {
    script = "install/install_otel_collector.sh"
  }

  provisioner "shell" {
    inline = [
      "sudo apt install openjdk-8-jdk openjdk-8-dbg openjdk-11-jdk openjdk-11-dbg openjdk-17-jdk openjdk-17-dbg -y",
      "sudo update-java-alternatives -s /usr/lib/jvm/java-1.11.0-openjdk-${var.arch}",
      "sudo sed -i '/hl jexec.*/d' /usr/lib/jvm/.java-1.8.0-openjdk-${var.arch}.jinfo"
    ]
  }

  # install my extra nice tools, exa, bat, fd, ripgrep
  # wrapper for aprof to output results to a folder content shared by nginx
  # open to what port?

  # plop a file in with all the aliases I like
  provisioner "file" {
    source      = "aliases.sh"
    destination = "aliases.sh"
  }

  provisioner "shell" {
    inline = [
      "sudo mv aliases.sh /etc/profile.d/aliases.sh"
    ]
  }

  provisioner "shell" {
    inline = [
      "wget https://training.ragozin.info/sjk.jar",
      "sudo mv sjk.jar /usr/local/lib",
      ""
    ]
  }
}

