import com.google.gson.annotations.SerializedName

data class ContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val size: Int,
    val type: String,      // "file" or "dir"
    @SerializedName("download_url")
    val downloadUrl: String?,
    val content: String?   // only present when ?ref=…&content=1 (base64)
)