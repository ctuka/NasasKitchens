package com.nanaskitchens.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Orange = Color(0xFFE8720C)
private val OrangeDark = Color(0xFFC95F05)
private val Green = Color(0xFF2E4A2E)
private val Cream = Color(0xFFFAF3E4)
private val Ink = Color(0xFF24281F)
private val Muted = Color(0xFF6B7280)
private val Border = Color(0xFFE5DECB)

private enum class Tab(val label: String, val icon: String) { Home("Home", "⌂"), Orders("Orders", "▤"), Add("", "+"), Chat("Chat", "✦"), Profile("Profile", "●") }
private sealed interface Route { data object Tabs : Route; data class Kitchen(val id: String) : Route; data object Checkout : Route }

@Composable
fun App(repository: NanasRepository = remember { NanasRepository() }) {
    var route by remember { mutableStateOf<Route>(Route.Tabs) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var cart by remember { mutableStateOf<List<CartLine>>(emptyList()) }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Orange, secondary = Green, background = Cream, surface = Color.White, onBackground = Ink),
        typography = Typography(titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 25.sp, fontWeight = FontWeight.ExtraBold))
    ) {
        Surface(Modifier.fillMaxSize(), color = Cream) {
            when (val r = route) {
                Route.Tabs -> Scaffold(
                    containerColor = Cream,
                    bottomBar = { BottomBar(tab) { tab = it } }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            Tab.Home -> HomeScreen(repository) { route = Route.Kitchen(it) }
                            Tab.Orders -> OrdersScreen()
                            Tab.Add -> SellerInviteScreen()
                            Tab.Chat -> ChatScreen()
                            Tab.Profile -> ProfileScreen()
                        }
                    }
                }
                is Route.Kitchen -> KitchenScreen(repository, r.id, cart,
                    onBack = { route = Route.Tabs },
                    onAdd = { profile, item ->
                        val old = cart.firstOrNull { it.menuItem.id == item.id }
                        cart = if (old == null) cart + CartLine(profile.id, profile.name, item, 1)
                        else cart.map { if (it.menuItem.id == item.id) it.copy(quantity = it.quantity + 1) else it }
                    },
                    onCheckout = { route = Route.Checkout })
                Route.Checkout -> CheckoutScreen(cart, onBack = { route = Route.Tabs }, onQty = { id, qty ->
                    cart = if (qty <= 0) cart.filterNot { it.menuItem.id == id }
                    else cart.map { if (it.menuItem.id == id) it.copy(quantity = qty) else it }
                })
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Nanas’", color = Green, fontWeight = FontWeight.Black, fontSize = 23.sp)
        Text(" Kitchens", color = Orange, fontWeight = FontWeight.Black, fontSize = 23.sp)
        Spacer(Modifier.weight(1f)); Text("♡", color = Orange, fontSize = 24.sp)
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 10.dp) {
        Tab.entries.forEach { item ->
            if (item == Tab.Add) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Button(onClick = { onSelect(item) }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(18.dp)) { Text("+", fontSize = 26.sp) }
                }
            } else NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Text(item.icon, fontSize = 20.sp) },
                label = { Text(item.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Orange, selectedTextColor = Green, indicatorColor = Color(0xFFFDF0E3))
            )
        }
    }
}

@Composable
private fun HomeScreen(repository: NanasRepository, openKitchen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<String?>(null) }
    var kitchens by remember { mutableStateOf<List<KitchenSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    fun load(tag: String?) { selected = tag; loading = true; scope.launch { kitchens = repository.nearby(tag); loading = false } }
    LaunchedEffect(Unit) { load(null) }

    Column(Modifier.fillMaxSize()) {
        BrandHeader()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Hungry for something delicious?", style = MaterialTheme.typography.titleLarge, color = Green)
            Spacer(Modifier.height(5.dp))
            Text("Real food made with ♥ near you", color = Muted)
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0xFFFDF0E3), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("⌖", color = Orange, fontSize = 24.sp) }
                    Spacer(Modifier.width(12.dp)); Column { Text("Made with ♥ near you", fontWeight = FontWeight.Bold, color = Green); Text("Within 10 miles · San Francisco (demo)", color = Muted, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text("⌕", color = Muted, fontSize = 22.sp); Spacer(Modifier.width(10.dp)); Text("Search meals, cuisines or cooks", color = Muted) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                cuisines.forEach { c ->
                    val active = selected == c.tag
                    FilterChip(selected = active, onClick = { load(c.tag) }, label = { Text("${c.icon} ${c.label}") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Green, selectedLabelColor = Color.White))
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Top Picks Near You", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Green)
            Text("Small batches from neighborhood cooks", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            if (loading) repeat(3) { SkeletonCard(); Spacer(Modifier.height(12.dp)) }
            else kitchens.forEach { KitchenCard(it) { openKitchen(it.id) }; Spacer(Modifier.height(13.dp)) }
            Card(Modifier.fillMaxWidth().clickable { }, colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✦", fontSize = 30.sp, color = Color.White); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("AI Chat Assistant", color = Color.White, fontWeight = FontWeight.Bold); Text("Tell me what you like—I’ll find the best meal.", color = Color.White.copy(alpha = .8f), fontSize = 13.sp) }; Text("›", color = Color.White, fontSize = 26.sp)
                }
            }
        }
    }
}

@Composable private fun SkeletonCard() { Box(Modifier.fillMaxWidth().height(185.dp).background(Color(0xFFF1EAD9), RoundedCornerShape(18.dp))) }

@Composable
private fun KitchenCard(k: KitchenSummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp).background(Color(0xFFF6E7CD), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Text(cuisineIcon(k.cuisineTag), fontSize = 44.sp) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                Text(k.name, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${k.cuisineTag.replaceFirstChar { it.uppercase() }} · ${k.distanceMiles} mi · ★ ${k.ratingAvg ?: "New"}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp)); Row { TrustBadge("🛡 Hygiene ${k.hygieneScore ?: "—"}", true); Spacer(Modifier.width(5.dp)); TrustBadge("${k.portionsLeftToday} left", false) }
            }; Text("›", fontSize = 28.sp, color = Orange)
        }
    }
}

@Composable private fun TrustBadge(text: String, green: Boolean) { Text(text, color = if (green) Green else OrangeDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.background(if (green) Color(0xFFE8F1E8) else Color(0xFFFDF0E3), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp)) }

@Composable
private fun KitchenScreen(repository: NanasRepository, id: String, cart: List<CartLine>, onBack: () -> Unit, onAdd: (KitchenProfile, MenuItem) -> Unit, onCheckout: () -> Unit) {
    var data by remember(id) { mutableStateOf<Pair<KitchenProfile, MenuDay>?>(null) }
    LaunchedEffect(id) { data = repository.kitchen(id) }
    val pair = data
    if (pair == null) { Column { BrandHeader(); Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange) } }; return }
    val (profile, menu) = pair
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = if (cart.isEmpty()) 20.dp else 92.dp)) {
            BrandHeader()
            Column(Modifier.padding(20.dp)) {
                TextButton(onClick = onBack) { Text("‹ Back to nearby kitchens", color = Green) }
                Box(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFFF6E7CD), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Text(cuisineIcon(profile.cuisineTag), fontSize = 82.sp) }
                Spacer(Modifier.height(18.dp)); Text(profile.name, style = MaterialTheme.typography.titleLarge, color = Green)
                Text("${cuisineIcon(profile.cuisineTag)} ${profile.cuisineTag.replaceFirstChar { it.uppercase() }} · ★ ${profile.ratingAvg} (${profile.ratingCount})", color = Muted)
                Spacer(Modifier.height(10.dp)); TrustBadge("🛡 Hygiene ${profile.hygieneScoreTotal} · inspected Jun 18", true)
                Spacer(Modifier.height(14.dp)); Text(profile.description.orEmpty(), color = Ink, lineHeight = 22.sp)
                Spacer(Modifier.height(24.dp)); Text("Today’s Menu", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Green)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp)) { menu.readyWindows.forEach { AssistChip(onClick = {}, label = { Text("⏰ ${it.start}–${it.end}") }); Spacer(Modifier.width(8.dp)) } }
                menu.items.forEach { item ->
                    MenuCard(item, cart.firstOrNull { it.menuItem.id == item.id }?.quantity ?: 0) { onAdd(profile, item) }
                    Spacer(Modifier.height(12.dp))
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) { Column(Modifier.padding(16.dp)) { Text("Community", fontWeight = FontWeight.Bold, color = Green); Text("★ ${profile.ratingAvg} from ${profile.ratingCount} neighbors", color = Muted); Text("Vote on next week’s menu · Request a dish", color = Orange, fontSize = 13.sp) } }
            }
        }
        if (cart.isNotEmpty()) Button(onClick = onCheckout, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).height(58.dp), shape = RoundedCornerShape(16.dp)) { Text("View basket · ${cart.sumOf { it.quantity }} items · ${money(cart.sumOf { it.menuItem.dish.priceCents * it.quantity })}", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun MenuCard(item: MenuItem, quantity: Int, add: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(82.dp).background(Color(0xFFFDF0E3), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("🍽️", fontSize = 38.sp) }
            Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(item.dish.name, fontWeight = FontWeight.Bold); Text(item.dish.description.orEmpty(), color = Muted, fontSize = 12.sp, maxLines = 2); Row { item.dish.dietaryTags.forEach { TrustBadge(it, true); Spacer(Modifier.width(4.dp)) } }; Text("${money(item.dish.priceCents)} · ${item.portionsRemaining} left", color = OrangeDark, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            FilledTonalButton(onClick = add, enabled = item.portionsRemaining > quantity, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(if (quantity == 0) "Add" else "+ $quantity") }
        }
    }
}

@Composable
private fun CheckoutScreen(cart: List<CartLine>, onBack: () -> Unit, onQty: (String, Int) -> Unit) {
    var placed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BrandHeader(); Column(Modifier.padding(20.dp)) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(if (placed) "Order confirmed!" else "Cart & Checkout", style = MaterialTheme.typography.titleLarge, color = Green)
            if (placed) {
                Card(Modifier.fillMaxWidth().padding(top = 18.dp), colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(24.dp)) { Text("✓ Your neighbor is cooking", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp); Text("Pickup at 6:00 PM · We’ll notify you when it’s ready.", color = Color.White.copy(alpha = .85f)) } }
            } else {
                Spacer(Modifier.height(16.dp)); cart.forEach { line ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text("🍽️", fontSize = 28.sp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(line.menuItem.dish.name, fontWeight = FontWeight.Bold); Text(line.kitchenName, color = Muted, fontSize = 12.sp); Text(money(line.menuItem.dish.priceCents), color = OrangeDark) }; OutlinedButton(onClick = { onQty(line.menuItem.id, line.quantity - 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) { Text("−") }; Text(" ${line.quantity} "); OutlinedButton(onClick = { onQty(line.menuItem.id, line.quantity + 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) { Text("+") } } }
                    Spacer(Modifier.height(10.dp))
                }
                Text("Ready time", fontWeight = FontWeight.Bold, color = Green); Row { listOf("5:30 PM", "6:00 PM", "6:30 PM").forEachIndexed { i, s -> FilterChip(selected = i == 1, onClick = {}, label = { Text(s) }); Spacer(Modifier.width(6.dp)) } }
                Spacer(Modifier.height(12.dp)); Text("Fulfillment", fontWeight = FontWeight.Bold, color = Green); Row { FilterChip(selected = true, onClick = {}, label = { Text("🛍 Pickup") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = false, onClick = {}, label = { Text("🚗 Delivery") }) }
                HorizontalDivider(Modifier.padding(vertical = 16.dp)); Text("Total · ${money(cart.sumOf { it.menuItem.dish.priceCents * it.quantity })}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Green)
                Spacer(Modifier.height(14.dp)); Button(onClick = { placed = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(15.dp), enabled = cart.isNotEmpty()) { Text("Confirm demo order", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable private fun OrdersScreen() { DemoTabPage("Your Orders", "Track pickup and delivery in one place.") { OrderRow(OrderCard("1", "Ayşe’s Anatolian Kitchen", "Mantı × 2", "Today · 6:00 PM", "Preparing", 3300)); Spacer(Modifier.height(12.dp)); OrderRow(OrderCard("2", "Auntie Mei’s Table", "Red-Braised Pork", "Jun 28", "Completed", 1750)) } }
@Composable private fun OrderRow(o: OrderCard) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) { Column(Modifier.padding(17.dp)) { Row { Text(o.kitchen, Modifier.weight(1f), fontWeight = FontWeight.Bold); TrustBadge(o.status, o.status == "Completed") }; Text(o.summary, color = Muted); Text("${o.readyTime} · ${money(o.totalCents)}", color = OrangeDark, fontWeight = FontWeight.SemiBold) } } }

@Composable private fun ChatScreen() { var text by remember { mutableStateOf("") }; var sent by remember { mutableStateOf(false) }; Column(Modifier.fillMaxSize()) { BrandHeader(); Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) { Text("AI Food Assistant", style = MaterialTheme.typography.titleLarge, color = Green); Text("Order naturally by text or voice", color = Muted); Spacer(Modifier.height(20.dp)); ChatBubble("What are you craving today? I can find homemade meals nearby.", false); if (sent) { ChatBubble(text, true); ChatBubble("I found Ayşe’s Mantı 0.8 miles away—5 portions remain. Would you like pickup around 6:00 PM?", false) } }; Row(Modifier.background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Ask for a meal…") }, shape = RoundedCornerShape(18.dp)); Spacer(Modifier.width(8.dp)); Button(onClick = { if (text.isNotBlank()) sent = true }, modifier = Modifier.size(52.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(18.dp)) { Text("➤") } } } }
@Composable private fun ChatBubble(text: String, mine: Boolean) { Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) { Text(text, color = if (mine) Color.White else Ink, modifier = Modifier.widthIn(max = 300.dp).background(if (mine) Orange else Color.White, RoundedCornerShape(17.dp)).padding(14.dp)) }; Spacer(Modifier.height(10.dp)) }

@Composable private fun ProfileScreen() { DemoTabPage("Profile", "Your Nanas’ Kitchens account") { listOf("♡ Favorite kitchens", "⌖ Delivery addresses", "◉ Notifications", "A  Accessibility & language", "?  Help & safety").forEach { Card(Modifier.fillMaxWidth().padding(bottom = 9.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Text(it, Modifier.padding(17.dp), color = Green, fontWeight = FontWeight.SemiBold) } } } }
@Composable private fun SellerInviteScreen() { DemoTabPage("Cook with Nanas’", "Share your culture, recipes and table with your neighborhood.") { Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(24.dp)) { Text("Start a neighborhood kitchen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp); Text("Publish today’s menu, manage portions and accept local orders from one simple screen.", color = Color.White.copy(alpha = .85f)); Spacer(Modifier.height(14.dp)); Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text("Create seller profile") } } } } }

@Composable private fun DemoTabPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize()) { BrandHeader(); Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, color = Green); Text(subtitle, color = Muted); Spacer(Modifier.height(20.dp)); content() } } }
