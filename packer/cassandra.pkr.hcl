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
  ami_name      = "rustyrazorblade/images/easy-cass-lab-cassandra-${local.timestamp}"
  instance_type = "c3.xlarge"
  region        = "us-west-2"
  source_ami_filter {
    filters = {
      name                = "rustyrazorblade/images/easy-cass-lab-base-*"
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

  # set up environment with PATH and aliases
  provisioner "file" {
    source = "environment"
    destination = "environment"
  }

  provisioner "shell" {
    inline = ["sudo mv environment /etc/environment"]
  }

  # catch all bin upload
  # just drop stuff you need in bin and the next 2 provisioners will take care of it
  provisioner "file" {
    source = "bin-cassandra"
    destination = "/home/ubuntu/"
  }
  provisioner "shell" {
    inline = [
      "sudo mv -v bin-cassandra/* /usr/local/bin/",
      "sudo chmod +x /usr/local/bin/*",
      "ls /usr/local/bin",
      "rmdir bin-cassandra"
    ]
  }


  provisioner "shell" {
    script = "install_easy_cass_stress.sh"
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
    source = "axonops-sudoers"
    destination = "axonops-sudoers"
  }

  provisioner "shell" {
    inline = [
      "sudo chown root:root axonops-sudoers",
      "sudo mv axonops-sudoers /etc/sudoers.d/axonops",
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
    source = "patch-jvm-options.py"
    destination = "patch-jvm-options.py"
  }

  provisioner "shell" {
    inline = [
       "sudo mv patch-jvm-options.py /usr/local/bin/patch-jvm-options.py",
       "sudo chmod +x /usr/local/bin/patch-jvm-options.py"
    ]
  }

  provisioner "shell" {
    inline = [
      "mkdir -p /home/ubuntu/.config/htop/"
    ]
  }

  provisioner "file" {
    source = "htoprc"
    destination = "/home/ubuntu/.config/htop/htoprc"
  }
}


