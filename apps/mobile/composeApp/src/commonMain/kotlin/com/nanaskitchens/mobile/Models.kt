package com.nanaskitchens.mobile

import kotlinx.serialization.Serializable

@Serializable
data class KitchenSummary(
    val id: String,
    val name: String,
    val cuisineTag: String,
    val distanceMiles: Double,
    val ratingAvg: Double? = null,
    val hygieneScore: Int? = null,
    val portionsLeftToday: Int,
    val photo: String? = null,
)

@Serializable
data class KitchenProfile(
    val id: String,
    val name: String,
    val cuisineTag: String,
    val description: String? = null,
    val photos: List<String> = emptyList(),
    val ratingAvg: Double? = null,
    val ratingCount: Int = 0,
    val hygieneScoreTotal: Int? = null,
    val hygieneScoredAt: String? = null,
)

@Serializable data class Dish(val id: String, val name: String, val description: String? = null, val photo: String? = null, val priceCents: Int, val dietaryTags: List<String> = emptyList())
@Serializable data class MenuItem(val id: String, val portionsTotal: Int, val portionsRemaining: Int, val dish: Dish)
@Serializable data class ReadyWindow(val start: String, val end: String, val slotMinutes: Int = 30)
@Serializable data class MenuDay(val id: String, val date: String, val status: String, val readyWindows: List<ReadyWindow> = emptyList(), val items: List<MenuItem> = emptyList())

data class Cuisine(val tag: String?, val label: String, val icon: String)
data class CartLine(val kitchenId: String, val kitchenName: String, val menuItem: MenuItem, val quantity: Int)
data class OrderCard(val id: String, val kitchen: String, val summary: String, val readyTime: String, val status: String, val totalCents: Int)

val cuisines = listOf(
    Cuisine(null, "All", "✨"), Cuisine("turkish", "Turkish", "🇹🇷"), Cuisine("chinese", "Chinese", "🇨🇳"),
    Cuisine("mexican", "Mexican", "🇲🇽"), Cuisine("indian", "Indian", "🇮🇳"), Cuisine("italian", "Italian", "🇮🇹"),
    Cuisine("japanese", "Japanese", "🇯🇵"), Cuisine("korean", "Korean", "🇰🇷"), Cuisine("lebanese", "Lebanese", "🇱🇧")
)

fun cuisineIcon(tag: String) = cuisines.firstOrNull { it.tag == tag }?.icon ?: "🍽️"
fun money(cents: Int) = "$" + "%.2f".format(cents / 100.0)
