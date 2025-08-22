package com.rustyrazorblade.easycasslab.configuration

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.Utils
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.aws.EC2
import io.github.oshai.kotlinlogging.KotlinLogging
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.model.CreateKeyPairRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.HashSet

data class User(
    @ConfigField(order = 1, prompt = "What's your email?", default = "")
    var email: String,
    
    @ConfigField(order = 2, prompt = "What AWS region do you use?", default = "us-west-2")
    var region: String,
    
    @ConfigField(order = 10, prompt = "EC2 Key Name", default = "", skippable = true)
    var keyName: String,
    
    @ConfigField(order = 11, prompt = "SSH Key Path", default = "", skippable = true)
    var sshKeyPath: String,
    
    // if true we'll load the profile from the AWS credentials rather than this file
    // can over
    @ConfigField(order = 12, prompt = "AWS Profile", default = "", skippable = true)
    var awsProfile: String,
    
    // fallback for people who haven't set up the aws cli
    @ConfigField(order = 3, prompt = "Please enter your AWS Access Key", default = "")
    var awsAccessKey: String,
    
    @ConfigField(order = 4, prompt = "Please enter your AWS Secret Access Key", default = "", secret = true)
    var awsSecret: String,
    
    @ConfigField(order = 5, prompt = "AxonOps Org", default = "")
    var axonOpsOrg: String = "",
    
    @ConfigField(order = 6, prompt = "AxonOps Key", default = "")
    var axonOpsKey: String = "",
) {
    companion object {
        val log = KotlinLogging.logger {}

        /**
         * Gets all ConfigField-annotated properties from User class in order
         */
        private fun getConfigFields(): List<kotlin.reflect.KProperty1<User, *>> {
            return User::class.members
                .filterIsInstance<kotlin.reflect.KProperty1<User, *>>()
                .filter { it.annotations.any { annotation -> annotation is ConfigField } }
                .sortedBy { property ->
                    (property.annotations.find { it is ConfigField } as ConfigField).order
                }
        }

        /**
         * Loads existing YAML as Map or returns empty map if file doesn't exist
         */
        private fun loadExistingConfig(context: Context, location: File): Map<String, Any> {
            return if (location.exists()) {
                @Suppress("UNCHECKED_CAST")
                context.yaml.readValue(location, Map::class.java) as Map<String, Any>
            } else {
                emptyMap()
            }
        }

        /**
         * Asks a bunch of questions and generates the user file
         * Now supports both initial creation and updating existing files with missing fields
         */
        fun createInteractively(
            context: Context,
            location: File,
            outputHandler: OutputHandler,
        ) {
            outputHandler.handleMessage("Welcome to the easy-cass-lab interactive setup.")
            outputHandler.handleMessage("We just need to know a few things before we get started.")

            // Load existing config values if file exists
            val existingConfig = loadExistingConfig(context, location)
            val configFields = getConfigFields()
            val fieldValues = mutableMapOf<String, Any>()
            
            // Copy existing values first
            fieldValues.putAll(existingConfig)
            
            // Process fields in order, only prompting for missing ones
            var region: Region? = null
            var awsAccessKey = ""
            var awsSecret = ""
            
            for (field in configFields) {
                val configField = field.annotations.find { it is ConfigField } as ConfigField
                val fieldName = field.name
                
                // Skip if field already exists in config
                if (existingConfig.containsKey(fieldName)) {
                    // Capture needed values for AWS operations
                    when (fieldName) {
                        "region" -> region = Region.of(existingConfig[fieldName] as String)
                        "awsAccessKey" -> awsAccessKey = existingConfig[fieldName] as String
                        "awsSecret" -> awsSecret = existingConfig[fieldName] as String
                    }
                    continue
                }
                
                // Skip if field is marked as skippable (auto-generated)
                if (configField.skippable) {
                    continue
                }
                
                // Prompt for missing field
                val value = Utils.prompt(configField.prompt, configField.default, configField.secret)
                fieldValues[fieldName] = value
                
                // Capture needed values for AWS operations
                when (fieldName) {
                    "region" -> region = Region.of(value)
                    "awsAccessKey" -> awsAccessKey = value
                    "awsSecret" -> awsSecret = value
                }
            }
            
            // Generate AWS keys only when both keyName and sshKeyPath are missing
            // This preserves the original behavior where keys are auto-generated
            if (!existingConfig.containsKey("keyName") || !existingConfig.containsKey("sshKeyPath")) {
                outputHandler.handleMessage("Generating AWS key pair and SSH credentials...")
                
                val ec2 = EC2(awsAccessKey, awsSecret, region!!)
                val ec2Client = ec2.client

                val keyName = "easy-cass-lab-${UUID.randomUUID()}"
                val request = CreateKeyPairRequest.builder()
                    .keyName(keyName).build()

                val response = ec2Client.createKeyPair(request)

                // write the private key into the ~/.easy-cass-lab/profiles/<profile>/ dir
                val secretFile = File(context.profileDir, "secret.pem")
                secretFile.writeText(response.keyMaterial())

                // set permissions
                val perms = HashSet<PosixFilePermission>()
                perms.add(PosixFilePermission.OWNER_READ)
                perms.add(PosixFilePermission.OWNER_WRITE)

                log.info { "Setting secret file permissions $perms" }
                Files.setPosixFilePermissions(secretFile.toPath(), perms)
                
                fieldValues["keyName"] = keyName
                fieldValues["sshKeyPath"] = secretFile.absolutePath
            }
            
            // Set awsProfile to empty string if not already present (preserving original behavior)
            if (!existingConfig.containsKey("awsProfile")) {
                fieldValues["awsProfile"] = ""
            }

            // Handle AxonOps prompting if not already configured
            if (!existingConfig.containsKey("axonOpsOrg") || !existingConfig.containsKey("axonOpsKey")) {
                val axonOpsChoice = Utils.prompt("Use AxonOps (https://axonops.com/) for monitoring. Requires an account. [y/N]", default = "N")
                val useAxonOps = axonOpsChoice.equals("y", true)
                
                if (useAxonOps) {
                    if (!existingConfig.containsKey("axonOpsOrg")) {
                        fieldValues["axonOpsOrg"] = Utils.prompt("AxonOps Org: ", "")
                    }
                    if (!existingConfig.containsKey("axonOpsKey")) {
                        fieldValues["axonOpsKey"] = Utils.prompt("AxonOps Key: ", "")
                    }
                } else {
                    fieldValues["axonOpsOrg"] = ""
                    fieldValues["axonOpsKey"] = ""
                }
            }

            // Create User object from collected values
            val user = User(
                fieldValues["email"] as String,
                fieldValues["region"] as String,
                fieldValues["keyName"] as String,
                fieldValues["sshKeyPath"] as String,
                fieldValues["awsProfile"] as String,
                fieldValues["awsAccessKey"] as String,
                fieldValues["awsSecret"] as String,
                fieldValues["axonOpsOrg"] as String,
                fieldValues["axonOpsKey"] as String,
            )

            context.yaml.writeValue(location, user)
        }
    }
}
