package com.rustyrazorblade.easydblab.configuration

/**
 * Immutable S3 path abstraction following java.nio.file.Path patterns.
 * Provides type-safe S3 path construction with cluster namespacing.
 *
 * All cluster-specific paths are automatically prefixed with "clusters/{clusterId}"
 * to support multiple clusters sharing the same S3 bucket without conflicts.
 *
 * Example usage:
 * ```
 * val s3Path = ClusterS3Path.from("my-bucket", "cluster-abc-123")
 * val jarPath = s3Path.sparkJars().resolve("myapp.jar")
 * println(jarPath) // s3://my-bucket/clusters/cluster-abc-123/spark-jars/myapp.jar
 *
 * // For S3 SDK calls:
 * val putRequest = PutObjectRequest.builder()
 *     .bucket(jarPath.bucket)
 *     .key(jarPath.getKey())
 *     .build()
 * ```
 *
 * @property bucket The S3 bucket name (without s3:// prefix)
 * @property segments The path segments after the bucket (immutable list)
 */
data class ClusterS3Path(
    val bucket: String,
    private val segments: List<String> = emptyList(),
) {
    companion object {
        private const val CLUSTERS_PREFIX = "clusters"
        private const val SPARK_JARS_DIR = "spark-jars"
        private const val BACKUPS_DIR = "backups"
        private const val LOGS_DIR = "logs"
        private const val DATA_DIR = "data"
        private const val EMR_LOGS_DIR = "emr-logs"

        /**
         * Create a cluster-namespaced S3 path.
         * Paths are prefixed with "clusters/{clusterId}" for multi-cluster support.
         *
         * @param bucket The S3 bucket name
         * @param clusterId The cluster identifier (typically from ClusterState.clusterId)
         * @return A new ClusterS3Path rooted at clusters/{clusterId}
         */
        fun from(
            bucket: String,
            clusterId: String,
        ): ClusterS3Path = ClusterS3Path(bucket, listOf(CLUSTERS_PREFIX, clusterId))

        /**
         * Convenience factory from ClusterState and User configuration.
         *
         * @param clusterState The cluster state containing clusterId
         * @param user The user configuration containing s3Bucket
         * @return A new ClusterS3Path for this cluster
         */
        fun from(
            clusterState: ClusterState,
            user: User,
        ): ClusterS3Path = from(user.s3Bucket, clusterState.clusterId)

        /**
         * Create a root S3 path without cluster namespacing.
         * Use sparingly - prefer from() with cluster ID for proper namespacing.
         *
         * @param bucket The S3 bucket name
         * @return A new ClusterS3Path at bucket root
         */
        fun root(bucket: String): ClusterS3Path = ClusterS3Path(bucket)

        /**
         * Create a ClusterS3Path from an S3 object key.
         * Properly handles key strings by filtering empty segments.
         *
         * This should be used when reconstructing paths from S3 list operations
         * where the full key is returned.
         *
         * @param bucket The S3 bucket name
         * @param key The S3 object key (e.g., "clusters/abc123/spark-jars/myapp.jar")
         * @return A new ClusterS3Path representing the key
         */
        fun fromKey(
            bucket: String,
            key: String,
        ): ClusterS3Path = ClusterS3Path(bucket, key.split("/").filter { it.isNotBlank() })
    }

    // Core Path-like methods

    /**
     * Resolve a path segment, returning a new ClusterS3Path.
     * Follows java.nio.file.Path.resolve() semantics.
     *
     * Path segments containing slashes are split into multiple segments.
     * Empty segments are filtered out.
     *
     * Example:
     * ```
     * path.resolve("subdir").resolve("file.jar")
     * path.resolve("subdir/file.jar")  // Equivalent
     * ```
     *
     * @param path The path segment(s) to append
     * @return A new ClusterS3Path with the path appended
     */
    fun resolve(path: String): ClusterS3Path {
        val newSegments = path.split("/").filter { it.isNotBlank() }
        return copy(segments = segments + newSegments)
    }

    /**
     * Get parent path, or null if this is the root.
     * Follows java.nio.file.Path.getParent() semantics.
     *
     * @return The parent path, or null if this path has no parent
     */
    fun getParent(): ClusterS3Path? =
        if (segments.isEmpty()) {
            null
        } else {
            copy(segments = segments.dropLast(1))
        }

    /**
     * Get the last segment (filename), or null if this is the root.
     * Follows java.nio.file.Path.getFileName() semantics.
     *
     * @return The filename, or null if this is the root path
     */
    fun getFileName(): String? = segments.lastOrNull()

    /**
     * Returns the S3 URI as a string: s3://bucket/path/to/file
     *
     * @return The full S3 URI
     */
    override fun toString(): String {
        val pathPart = if (segments.isEmpty()) "" else segments.joinToString("/")
        return if (pathPart.isEmpty()) {
            "s3://$bucket"
        } else {
            "s3://$bucket/$pathPart"
        }
    }

    /**
     * Alias for toString() to match URI patterns.
     *
     * @return The full S3 URI
     */
    fun toUri(): String = toString()

    /**
     * Returns just the path portion without the s3://bucket prefix.
     * Useful for S3 SDK calls that require bucket and key separately.
     *
     * Example:
     * ```
     * val path = ClusterS3Path.from("bucket", "id").resolve("file.txt")
     * putRequest.bucket(path.bucket).key(path.getKey())
     * ```
     *
     * @return The S3 key (path after bucket name)
     */
    fun getKey(): String = segments.joinToString("/")

    // Convenience methods for common directories

    /**
     * Path for Spark JAR uploads.
     *
     * @return Path: s3://bucket/clusters/{clusterId}/spark-jars
     */
    fun sparkJars(): ClusterS3Path = resolve(SPARK_JARS_DIR)

    /**
     * Path for Cassandra backups.
     *
     * @return Path: s3://bucket/clusters/{clusterId}/backups
     */
    fun backups(): ClusterS3Path = resolve(BACKUPS_DIR)

    /**
     * Path for log aggregation.
     *
     * @return Path: s3://bucket/clusters/{clusterId}/logs
     */
    fun logs(): ClusterS3Path = resolve(LOGS_DIR)

    /**
     * Path for general data storage.
     *
     * @return Path: s3://bucket/clusters/{clusterId}/data
     */
    fun data(): ClusterS3Path = resolve(DATA_DIR)

    /**
     * Path for EMR logs.
     *
     * @return Path: s3://bucket/clusters/{clusterId}/emr-logs
     */
    fun emrLogs(): ClusterS3Path = resolve(EMR_LOGS_DIR)
}
