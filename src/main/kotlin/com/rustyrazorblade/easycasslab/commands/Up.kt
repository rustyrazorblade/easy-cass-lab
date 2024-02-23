package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.configuration.ServerType
import  com.rustyrazorblade.easycasslab.containers.Terraform
import java.io.File

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

        val stressEnvironmentVars = File("provisioning/stress/environment.sh").bufferedWriter()
        stressEnvironmentVars.write("#!/usr/bin/env bash")
        stressEnvironmentVars.newLine()

        val host = context.tfstate.getHosts(ServerType.Cassandra).first().private

        stressEnvironmentVars.write("export  EASY_CASS_STRESS_CASSANDRA_HOST=$host")
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.flush()
        stressEnvironmentVars.close()

        WriteConfig(context).execute()

        // once the instances are up we can connect and set up
        // the disks, axonops, system settings, etc
        // we can't set up the configs yet though,
        // because those are dependent on the C* version in use.
        println("Waiting for instances to come up..")
        Thread.sleep(5000)
    }

}