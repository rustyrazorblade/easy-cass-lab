package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.configuration.ServerType
import  com.rustyrazorblade.easycasslab.containers.Terraform
import org.apache.sshd.common.SshException
import java.io.File
import java.nio.file.Path

@Parameters(commandDescription = "Starts instances")
class Up(val context: Context) : ICommand {

    override fun execute() {
        // we have to list both the variable files explicitly here
        // even though we have a terraform.tvars
        // we need the local one to apply at the highest priority
        // specifying the user one makes it take priority over the local one
        // so we have to explicitly specify the local one to ensure it gets
        // priority over user
        val terraform = Terraform(context)

        with(TermColors()) {

            terraform.up().onFailure {
                println(it.message)
                println(it.printStackTrace())
                println("${red("Some resources may have been unsuccessfully provisioned.")}  Rerun ${green("easy-cass-lab up")} to provision the remaining resources.")
            }.onSuccess {

                println("""Instances have been provisioned.

                Use ${green("easy-cass-lab use <version>")} to use a specific version of Cassandra.  
                
                Supported versions are 3.0, 3.11, 4.0, 4.1, """.trimMargin())

                println("Writing ssh config file to sshConfig.")

                println("""The following alias will allow you to easily work with the cluster:
                |
                |${green("source env.sh")}
                |
                |""".trimMargin())
                println("You can edit ${green("cassandra.patch.yaml")} with any changes you'd like to see merge in into the remote cassandra.yaml file.")
            }
        }
        
        val config = File("sshConfig").bufferedWriter()
        context.tfstate.writeSshConfig(config)

        val envFile = File("env.sh").bufferedWriter()
        context.tfstate.writeEnvironmentFile(envFile)

        // sets up any environment variables we need for the stress tool
        val stressEnvironmentVars = File("environment.sh").bufferedWriter()
        stressEnvironmentVars.write("#!/usr/bin/env bash")
        stressEnvironmentVars.newLine()

        val host = context.tfstate.getHosts(ServerType.Cassandra).first().private

        stressEnvironmentVars.write("export  EASY_CASS_STRESS_CASSANDRA_HOST=$host")
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.flush()
        stressEnvironmentVars.close()

        WriteConfig(context).execute()

        this::class.java.getResourceAsStream("disk_setup.sh").use {
            val diskSetup = File("disk_setup.sh").bufferedWriter()
            diskSetup.write(it!!.readBytes().toString(Charsets.US_ASCII))
            diskSetup.flush()
            diskSetup.close()
        }

        // once the instances are up we can connect and set up
        // the disks, axonops, system settings, etc
        // we can't set up the configs yet though,
        // because those are dependent on the C* version in use.
        println("Waiting for SSH to come up..")
        Thread.sleep(5000)

        // probably need to loop and wait
        // write to profile.d/stress.sh
        var done = false
        do {

            try {
                context.tfstate.withHosts(ServerType.Cassandra) {
                    context.executeRemotely(it, "echo 1")
                }
                done = true
            } catch (e: SshException) {
                println("SSH still not up yet, waiting..")
                Thread.sleep(1000)
            }

        } while (!done)

        context.tfstate.withHosts(ServerType.Cassandra) {
            context.upload(it, Path.of("environment.sh"), "environment.sh")
            context.executeRemotely(it, "sudo mv environment.sh /etc/profile.d/stress.sh")
        }
        SetupDisks(context).execute()
    }

}