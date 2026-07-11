package com.nanaskitchens.mobile

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class NanasRepository(private val baseUrl: String = "http://localhost:8080") {
    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun nearby(cuisine: String?): List<KitchenSummary> = runCatching {
        client.get("$baseUrl/kitchens/search") {
            parameter("lat", 37.788); parameter("lng", -122.4075)
            cuisine?.let { parameter("cuisine", it) }
        }.body<List<KitchenSummary>>()
    }.getOrElse { demoKitchens.filter { cuisine == null || it.cuisineTag == cuisine } }

    suspend fun kitchen(id: String): Pair<KitchenProfile, MenuDay> = runCatching {
        val profile = client.get("$baseUrl/kitchens/$id").body<KitchenProfile>()
        val menu = client.get("$baseUrl/kitchens/$id/menu").body<MenuDay>()
        profile to menu
    }.getOrElse {
        val p = demoProfiles[id] ?: demoProfiles.getValue("ayse")
        p to demoMenus.getValue(p.id)
    }
}

private val demoKitchens = listOf(
    KitchenSummary("ayse", "Ayşe’s Anatolian Kitchen", "turkish", 0.8, 4.9, 96, 8),
    KitchenSummary("mei", "Auntie Mei’s Table", "chinese", 1.4, 4.8, 94, 5),
    KitchenSummary("lupita", "Lupita’s Cocina", "mexican", 2.1, 4.7, 92, 11),
    KitchenSummary("ananya", "Ananya’s Spice Home", "indian", 2.6, 4.9, 97, 3),
)

private val demoProfiles = demoKitchens.associate { k ->
    k.id to KitchenProfile(k.id, k.name, k.cuisineTag,
        "Family recipes made in a licensed neighborhood kitchen, prepared fresh in small batches.",
        ratingAvg = k.ratingAvg, ratingCount = 42, hygieneScoreTotal = k.hygieneScore, hygieneScoredAt = "2026-06-18")
}

private fun menu(id: String, cuisine: String) = MenuDay("menu-$id", "2026-07-10", "published",
    listOf(ReadyWindow("17:00", "19:30", 30)),
    when (cuisine) {
        "turkish" -> listOf(
            MenuItem("m1", 12, 5, Dish("d1", "Mantı", "Hand-folded dumplings, garlic yogurt, paprika butter", priceCents = 1650, dietaryTags = listOf("Homemade"))),
            MenuItem("m2", 10, 3, Dish("d2", "İmam Bayıldı", "Slow-roasted eggplant with tomato and herbs", priceCents = 1450, dietaryTags = listOf("Vegan"))))
        "chinese" -> listOf(MenuItem("m3", 10, 5, Dish("d3", "Red-Braised Pork", "Shanghai-style pork with rice and greens", priceCents = 1750)))
        "mexican" -> listOf(MenuItem("m4", 14, 11, Dish("d4", "Chicken Mole", "Rich mole poblano with handmade tortillas", priceCents = 1600)))
        else -> listOf(MenuItem("m5", 8, 3, Dish("d5", "Home Thali", "Dal, seasonal sabzi, rice and fresh roti", priceCents = 1550, dietaryTags = listOf("Vegetarian"))))
    })

private val demoMenus = demoProfiles.mapValues { (id, p) -> menu(id, p.cuisineTag) }
