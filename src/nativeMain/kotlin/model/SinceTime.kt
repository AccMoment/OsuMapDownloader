package model

data class SinceDate(
    val years: Int,
    val months: Int,
    val days: Int
) {

    override fun toString(): String = buildString {

        append(years)
        append("-")
        val m = when {
            months < 0 -> "01"
            months <10 ->"0$months"
            months <=12->"$months"
            else -> "01"
        }
        val d = when {
            days < 0 -> "01"
            days <10 ->"0$days"
            days <=30->"$days"
            else -> "01"
        }
        append(m)
        append("-")
        append(d)
    }
}


