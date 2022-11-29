package model

sealed class OsuMode(val mode:Int) {
    class Standard:OsuMode(0)
    class Taiko:OsuMode(1)
    class Catch:OsuMode(2)
    class Mania:OsuMode(3)
}
