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
  ami_name      = "easy-cass-lab-base-${local.timestamp}"
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
  name    = "easy-cass-lab"
  sources = [
    "source.amazon-ebs.ubuntu"
  ]
  provisioner "shell" {
    inline = [
      "sudo umount -l -f /mnt", # needed early on before we do anything with /mnt
      "sudo apt update",
      "sudo apt upgrade -y",
      "sudo apt update",
      "sudo apt install -y wget sysstat unzip ripgrep ant ant-optional", # bpftrace was removed b/c it breaks bcc tools, need to build latest from source
      "sudo wget https://github.com/mikefarah/yq/releases/download/v4.41.1/yq_linux_amd64 -O /usr/local/bin/yq",
      "sudo chmod +x /usr/local/bin/yq",
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
      "pyenv install 2.7.18",
      "pyenv install 3.10.6"
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



  provisioner "shell" {
    inline = [
      "wget https://training.ragozin.info/sjk.jar",
      "sudo mv sjk.jar /usr/local/lib",
      ""
    ]
  }
}


