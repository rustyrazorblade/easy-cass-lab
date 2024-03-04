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
  ami_name      = "easy-cass-lab-cassandra-${local.timestamp}"
  instance_type = "c3.xlarge"
  region        = "us-west-2"
  source_ami_filter {
    filters = {
      name                = "easy-cass-lab-base-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    most_recent = true
    owners      = ["self"]
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
      inline = [
        "sudo umount -l -f /mnt", # needed early on before we do anything with /mnt
        "sudo apt update",
        "sudo apt upgrade -y",
        "sudo apt update"
      ]
  }

  # easy-cass-stress gets installed on every node.
  provisioner "shell" {
    inline = [
      "wget https://github.com/rustyrazorblade/easy-cass-stress/releases/download/6.0-preview/easy-cass-stress-6.0.0.zip",
      "unzip easy-cass-stress-6.0.0.zip",
      "sudo mv easy-cass-stress-6.0.0 /usr/local/easy-cass-stress",
    ]
  }

  # the cassandra_versions.yaml file is used to define all the version of cassandra we want
  # and it's matching java version.  The use command will set the symlink of /usr/local/cassandra
  # to point to the version of cassandra we want to use, and set the java version using update-java-alternatives
  provisioner "file" {
    source = "cassandra_versions.yaml"
    destination = "cassandra_versions.yaml"
  }

  provisioner "shell" {
    inline = [
        "sudo mv cassandra_versions.yaml /etc/cassandra_versions.yaml"
    ]
  }

  provisioner "shell" {
    script = "install_cassandra.sh"
  }

  # instal axonops
  provisioner "shell" {
    script = "install_axon.sh"
  }

  provisioner "file" {
    source      = "setup-axonops"
    destination = "setup-axonops"
  }

  provisioner "shell" {
    inline = [
      "sudo mv setup-axonops /usr/local/bin/setup-axonops",
      "sudo chmod +x /usr/local/bin/setup-axonops"
    ]
  }

  # install my extra nice tools, exa, bat, fd, ripgrep
  # wrapper for aprof to output results to a folder content shared by nginx
  # open to what port?

  provisioner "file" {
    source = "use-cassandra"
    destination = "use-cassandra"
  }

  provisioner "shell" {
    inline = [
       "sudo mv use-cassandra /usr/local/bin/use-cassandra",
       "sudo chmod +x /usr/local/bin/use-cassandra"
    ]
  }

  provisioner "file" {
    source = "cassandra.service"
    destination = "cassandra.service"
  }

  provisioner "shell" {
    inline = [
       "sudo mv cassandra.service /etc/systemd/system/cassandra.service",
       "sudo systemctl enable cassandra.service"
    ]
  }

  provisioner "file" {
    source = "patch-config"
    destination = "patch-config"
  }

  provisioner "shell" {
    inline = [
       "sudo mv patch-config /usr/local/bin/patch-config",
       "sudo chmod +x /usr/local/bin/patch-config"
    ]
  }

  provisioner "file" {
    source = "patch-jvm-options.py"
    destination = "patch-jvm-options.py"
  }

  provisioner "shell" {
    inline = [
       "sudo mv patch-jvm-options.py /usr/local/bin/patch-jvm-options.py",
       "sudo chmod +x /usr/local/bin/patch-jvm-options.py"
    ]
  }

}


