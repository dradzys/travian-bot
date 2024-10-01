package lt.dr.travian.bot

enum class Tribe(val id: String) {
    ROMANS("roman"),
    VIKINGS("viking"),
    GAULS("gaul"),
    TEUTONS("teuton"),
    HUNS("hun"),
    EGYPTIANS("egyptian"),
    SPARTANS("spartan");
}

fun String.toTribe(): Tribe? {
    return Tribe.values().firstOrNull { it.id == this }
}