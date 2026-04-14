data class Repository(
    val id: Long,
    val name: String,
    val full_name: String,
    val owner: Owner
) {
    data class Owner(val login: String)
}