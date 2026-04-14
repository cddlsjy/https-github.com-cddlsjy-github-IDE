data class CommitBody(
    val message: String,
    val content: String,   // base64 encoded new content
    val sha: String? = null,  // required for update/delete
    val branch: String? = null
)