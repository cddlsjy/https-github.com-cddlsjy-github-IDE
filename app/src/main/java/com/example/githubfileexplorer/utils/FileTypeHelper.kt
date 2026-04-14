object FileTypeHelper {
    private val textExtensions = setOf(
        "txt", "md", "kt", "java", "xml", "json", "gradle", "properties",
        "py", "js", "html", "css", "sh", "yaml", "yml", "conf", "ini"
    )

    fun isTextFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in textExtensions
    }
}