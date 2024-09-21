package lt.dr.travian.bot

enum class Tribe(val id: String) {
    ROMANS("roman");
    // TODO: add support for other tribes
}

fun String.toTribe(): Tribe? {
    return Tribe.values().firstOrNull { it.id == this }
}