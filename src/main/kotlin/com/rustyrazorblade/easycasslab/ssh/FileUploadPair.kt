package com.rustyrazorblade.easycasslab.ssh

import java.io.File

/**
 * Represents a file to be uploaded with its local and remote paths
 *
 * This data class decouples file discovery from upload operations,
 * making the code more testable and maintainable.
 *
 * @property localFile The local file to upload
 * @property remotePath The full remote path where the file should be uploaded
 */
data class FileUploadPair(
    val localFile: File,
    val remotePath: String,
)
