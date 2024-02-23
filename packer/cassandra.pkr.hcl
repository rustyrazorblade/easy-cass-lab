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
  instance_type = "c3.xlarge"
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
  launch_block_device_mappings {
    device_name = "/dev/sda1"
    volume_size = 16
    volume_type = "gp2"
    delete_on_termination = true
  }
}

build {
  name    = "cassandra"
  sources = [
    "source.amazon-ebs.ubuntu"
  ]
  provisioner "shell" {
      inline = [
        "sudo umount -l -f /mnt", # needed early on before we do anything with /mnt
        "sudo apt update",
        "sudo apt upgrade -y",
        "sudo apt update",
        "sudo apt install -y wget sysstat unzip ripgrep", # bpftrace was removed b/c it breaks bcc tools, need to build latest from source
        "sudo wget https://github.com/mikefarah/yq/releases/download/v4.41.1/yq_linux_amd64 -O /usr/local/bin/yq",
        "sudo chmod +x /usr/local/bin/yq",
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


  # install pyenv and python
  provisioner "shell" {
    inline = [
      "sudo apt update -y",
      "sudo apt install -y make build-essential libssl-dev zlib1g-dev libbz2-dev libreadline-dev libsqlite3-dev wget curl llvm libncursesw5-dev xz-utils tk-dev libxml2-dev libxmlsec1-dev libffi-dev liblzma-dev",
      "curl https://pyenv.run | bash",
      # add to ~/.bash_profile for use on instance
      "echo 'export PATH=\"$HOME/.pyenv/bin:$PATH\"' >> ~/.bash_profile",
      "echo 'eval \"$(pyenv init --path)\"' >> ~/.bash_profile",
      "echo 'eval \"$(pyenv virtualenv-init -)\"' >> ~/.bash_profile",
      # now load it in for Packer build
      "export PATH=\"$HOME/.pyenv/bin:$PATH\"",
      "eval \"$(pyenv init --path)\"",
      "eval \"$(pyenv virtualenv-init -)\"",
      # now install python
      "pyenv install 3.10.6",
      "pyenv install 2.7.18"
    ]
  }

  provisioner "shell" {
    inline = [
      "mkdir fio",
      "cd fio",
      "wget https://github.com/axboe/fio/archive/refs/tags/fio-3.36.zip",
      "unzip fio-*.zip",
      "cd fio-fio*",
      "./configure",
      "make",
      "sudo make install",
      "cd ..",
      "rm -rf fio"
    ]
  }

  # install async profiler
  provisioner "shell" {
    inline = [
        "sudo sysctl kernel.perf_event_paranoid=1",
        "sudo sysctl kernel.kptr_restrict=0",
        "wget https://github.com/async-profiler/async-profiler/releases/download/v3.0/async-profiler-3.0-linux-x64.tar.gz",
        "tar zxvf async-profiler-3.0-linux-x64.tar.gz",
        "sudo mv async-profiler-3.0-linux-x64 /usr/local/async-profiler"
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

  provisioner "shell" {
    script = "install_bcc.sh"
  }

  provisioner "shell" {
    inline = [
        "sudo apt install openjdk-8-jdk openjdk-8-dbg openjdk-11-jdk openjdk-11-dbg openjdk-17-jdk openjdk-17-dbg -y",
        "sudo update-java-alternatives -s /usr/lib/jvm/java-1.11.0-openjdk-amd64"
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




}


