package com.example.silkroad.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.silkroad.data.search.SearchEngine
import com.example.silkroad.domain.ShopRules
import com.example.silkroad.domain.model.Product
import com.example.silkroad.presentation.viewmodel.ShopState
import com.example.silkroad.presentation.viewmodel.ShopViewModel
import java.text.NumberFormat
import java.text.DateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

import android.net.Uri
import com.example.silkroad.presentation.ui.pr7.ChatScreen
import com.example.silkroad.presentation.ui.pr7.ReportScreen
import androidx.compose.ui.unit.sp

private fun money(value: Number): String = NumberFormat.getCurrencyInstance(Locale.US).apply {
    currency = Currency.getInstance("USD")
}.format(value) + " USD"

@Composable
fun SilkRoadApp(viewModel: ShopViewModel = viewModel(), deepLinkUri: Uri? = null) {
    val state by viewModel.state.collectAsState()
    var pr7DirectScreen by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(deepLinkUri) {
        if (deepLinkUri != null) {
            pr7DirectScreen = if (deepLinkUri.host == "reports") 5 else 4
        }
    }

    BackHandler(enabled = pr7DirectScreen != null) {
        pr7DirectScreen = null
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (pr7DirectScreen == 4) {
            ChatScreen()
        } else if (pr7DirectScreen == 5) {
            ReportScreen()
        } else if (state.user == null) {
            LoginScreen(
                state = state,
                login = viewModel::login,
                onOpenChatPr7 = { pr7DirectScreen = 4 },
                onOpenReportsPr7 = { pr7DirectScreen = 5 }
            )
        } else {
            key(state.user!!.username) { MainScreen(state, viewModel) }
        }
    }
}

@Composable
fun LoginScreen(
    state: ShopState,
    login: (String, String, Boolean) -> Unit,
    onOpenChatPr7: () -> Unit = {},
    onOpenReportsPr7: () -> Unit = {}
) {
    var username by rememberSaveable { mutableStateOf("") }
    // Password is not saved in instance state or preferences.
    var password by remember { mutableStateOf("") }
    var demo by rememberSaveable { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(Icons.Default.ShoppingBag, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Silk Road", style = MaterialTheme.typography.headlineLarge)
        Text("Tu próxima ruta empieza aquí.", style = MaterialTheme.typography.titleMedium)
        Text("Busca productos, guarda tus favoritos y prepara tu carrito.")
        Card {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(demo, { demo = it }, enabled = !state.loggingIn)
                    Text("Cuenta de demostración")
                }
                Text(if (demo) "Usuario: demo\nContraseña: SilkRoad2026\nAcceso local para evaluar la app."
                    else "Usa una cuenta de prueba de FakeStore API. El acceso requiere Internet.")
                if (demo) TextButton(onClick = { username = "demo"; password = "SilkRoad2026" }, enabled = !state.loggingIn) {
                    Text("Rellenar cuenta demo")
                }
            }
        }
        OutlinedTextField(username, { username = it }, label = { Text("Usuario") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), enabled = !state.loggingIn)
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), enabled = !state.loggingIn,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) {
                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    if (showPassword) "Ocultar contraseña" else "Mostrar contraseña")
            } })
        state.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { login(username, password, demo) }, enabled = !state.loggingIn,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (state.loggingIn) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Iniciar sesión")
        }

        // [PRÁCTICA 7]: Acceso directo para evaluación docente
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reportes y prácticas 8 a 10", fontWeight = FontWeight.Bold)
                Text("Pruebas de calidad, conexión compartida y acceso privado con Firebase. El chat conserva la demostración de la práctica 7.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenChatPr7, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chat PR7", fontSize = 12.sp)
                    }
                    Button(onClick = onOpenReportsPr7, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Assignment, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reportes", fontSize = 12.sp)
                    }
                }
            }
        }

        Text("Proyecto académico · Android\nCarrito simulado, sin cobros ni compras reales.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: ShopState, vm: ShopViewModel) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmCheckout by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<String?>(null) }
    var configureSearch by remember { mutableStateOf(false) }
    var searchServer by remember { mutableStateOf("") }
    var serverError by remember { mutableStateOf<String?>(null) }
    val product = detailId?.let { id -> state.products.firstOrNull { it.id == id }
        ?: state.cart.firstOrNull { it.product.id == id }?.product }
    val labels = listOf("Catálogo", "Buscar", "Favoritos", "Carrito", "Chat PR7", "Reportes")
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Favorite, Icons.Default.ShoppingCart, Icons.Default.Chat, Icons.Default.Assignment)
    BackHandler(enabled = detailId != null || tab != 0) { if (detailId != null) detailId = null else tab = 0 }
    LaunchedEffect(query, category) { vm.search(query, category) }
    Scaffold(modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(if (detailId != null) "Detalle del producto" else labels[tab])
                    if (detailId == null) Text("Hola, ${state.user?.username}", style = MaterialTheme.typography.bodySmall)
                }
            }, navigationIcon = { if (detailId != null) IconButton(onClick = { detailId = null }) {
                Icon(Icons.Default.ArrowBack, "Volver")
            } }, actions = {
                if (tab == 1) IconButton(onClick = {
                    searchServer = state.searchServerUrl; serverError = null; configureSearch = true
                }) { Icon(Icons.Default.Settings, "Configurar buscador") }
                if (tab < 4) {
                    IconButton(onClick = vm::refresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Actualizar catálogo") }
                }
                IconButton(onClick = { confirmLogout = true }) { Icon(Icons.Default.Logout, "Cerrar sesión") }
            })
        },
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index && detailId == null,
                        onClick = { focus.clearFocus(); keyboard?.hide(); tab = index; detailId = null }, icon = { Icon(icons[index], label) },
                        label = { Text(if (index == 3 && state.cart.isNotEmpty())
                            "$label (${state.cart.sumOf { it.quantity }})" else label) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if ((state.cached || state.message != null) && tab < 4) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp)) {
                    Text(state.message ?: "Consultando copia local mientras se actualiza el catálogo.",
                        style = MaterialTheme.typography.bodySmall)
                    if (state.updated > 0) Text("Guardado: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.updated))}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            when {
                detailId != null && product != null -> DetailScreen(product, state.favorites.contains(product.id),
                    { vm.toggleFavorite(product) }, { vm.changeQuantity(product, 1) })
                detailId != null -> EmptyState("El producto ya no está disponible.")
                tab == 3 -> CartScreen(state, vm, { detailId = it.id }, { confirmCheckout = true })
                tab == 4 -> ChatScreen()
                tab == 5 -> ReportScreen()
                else -> {
                    if (tab != 2) {
                        OutlinedTextField(query, { query = it }, label = { Text("Buscar por nombre o categoría") },
                            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
                            leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpiar búsqueda") }
                            })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Todos") }) }
                            items(state.products.map { it.category }.distinct().sorted()) { value ->
                                FilterChip(selected = category == value, onClick = { category = value },
                                    label = { Text(ShopRules.categoryLabel(value)) })
                            }
                        }
                    }
                    val search = state.search?.takeIf { it.query == query && it.category == category }
                    val visible = if (tab == 2) state.products.filter { it.id in state.favorites }
                        else search?.products ?: ShopRules.filter(state.products, query, category)
                    Text(when {
                        tab != 2 && search?.engine == SearchEngine.ELASTICSEARCH -> "${visible.size} productos · Elasticsearch"
                        tab != 2 && query.isNotBlank() && search?.engine == SearchEngine.LOCAL -> "${visible.size} productos · Búsqueda local"
                        else -> "${visible.size} productos"
                    }, Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
                    if (tab != 2) search?.notice?.let {
                        Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    if (visible.isEmpty()) EmptyState(if (tab == 2) "Aún no tienes favoritos. Toca el corazón de un producto."
                        else if (state.loading) "Cargando catálogo…" else "No hay productos para mostrar. Cambia la búsqueda o actualiza el catálogo.")
                    else ProductGrid(visible, state.favorites, { focus.clearFocus(); keyboard?.hide(); detailId = it.id }, vm::toggleFavorite)
                }
            }
        }
    }
    if (configureSearch) AlertDialog(onDismissRequest = { configureSearch = false }, title = { Text("Configurar buscador") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Usa la dirección de la computadora donde corre Elasticsearch. El teléfono y la computadora deben estar en la misma red.")
            OutlinedTextField(searchServer, { searchServer = it }, label = { Text("Dirección del servidor") }, singleLine = true)
            Text("En un emulador de esta computadora puedes usar http://10.0.2.2:9200", style = MaterialTheme.typography.bodySmall)
            serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            serverError = vm.configureSearchServer(searchServer)
            if (serverError == null) configureSearch = false
        }) { Text("Guardar dirección") } },
        dismissButton = { TextButton(onClick = { configureSearch = false }) { Text("Cancelar") } })
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Cerrar sesión") },
        text = { Text("Tus favoritos y carrito quedan guardados para tu siguiente acceso.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } })
    if (confirmCheckout) AlertDialog(onDismissRequest = { confirmCheckout = false }, title = { Text("Finalizar simulación") },
        text = { Text("Total: ${money(ShopRules.total(state.cart))}. No se hará ningún cobro. Se vaciará el carrito.") },
        confirmButton = { TextButton(onClick = {
            receipt = "Simulación completada: ${state.cart.sumOf { it.quantity }} artículos por ${money(ShopRules.total(state.cart))}. No se realizó ningún cobro."
            confirmCheckout = false; vm.checkout()
        }) { Text("Confirmar simulación") } },
        dismissButton = { TextButton(onClick = { confirmCheckout = false }) { Text("Cancelar") } })
    receipt?.let { message -> AlertDialog(onDismissRequest = { receipt = null }, title = { Text("Gracias por explorar Silk Road") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { receipt = null }) { Text("Entendido") } }) }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(message) }
}

@Composable
private fun ProductGrid(products: List<Product>, favorites: Set<Int>, open: (Product) -> Unit, favorite: (Product) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(155.dp), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(products, key = { it.id }) { product -> ProductCard(product, product.id in favorites, { open(product) }, { favorite(product) }) }
    }
}

@Composable
private fun ProductImage(product: Product, modifier: Modifier) {
    var failed by remember(product.image) { mutableStateOf(product.image.isBlank()) }
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        if (product.image.isNotBlank()) AsyncImage(model = product.image, contentDescription = product.title, modifier = Modifier.fillMaxSize().padding(18.dp),
            contentScale = ContentScale.Fit, onError = { failed = true }, onSuccess = { failed = false })
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Image, null, Modifier.size(36.dp))
            Text("Imagen no disponible", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductCard(product: Product, favorite: Boolean, open: () -> Unit, toggle: () -> Unit) {
    Card(onClick = open, shape = RoundedCornerShape(16.dp)) {
        Box {
            ProductImage(product, Modifier.fillMaxWidth().height(160.dp))
            IconButton(onClick = toggle, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (favorite) "Quitar de favoritos" else "Agregar a favoritos", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ShopRules.categoryLabel(product.category), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(product.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(money(product.price), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailScreen(product: Product, favorite: Boolean, toggle: () -> Unit, add: () -> Unit) {
    var added by remember(product.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProductImage(product, Modifier.fillMaxWidth().height(260.dp))
        Text(ShopRules.categoryLabel(product.category), color = MaterialTheme.colorScheme.primary)
        Text(product.title, style = MaterialTheme.typography.headlineMedium)
        Text(money(product.price), style = MaterialTheme.typography.titleLarge)
        Text(product.description, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = toggle, modifier = Modifier.fillMaxWidth()) {
            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
            Spacer(Modifier.width(8.dp)); Text(if (favorite) "Quitar de favoritos" else "Guardar en favoritos")
        }
        Button(onClick = { add(); added = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar al carrito") }
        if (added) Text("Producto agregado. Puedes cambiar la cantidad en Carrito.")
        Text("Precios expresados en USD. Compra académica simulada.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CartScreen(state: ShopState, vm: ShopViewModel, open: (Product) -> Unit, checkout: () -> Unit) {
    if (state.cart.isEmpty()) { EmptyState("Tu carrito está vacío. Agrega productos desde su detalle."); return }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.cart, key = { it.product.id }) { item ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        TextButton(onClick = { open(item.product) }) { Text(item.product.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        Text("${money(item.product.price)} por unidad")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.changeQuantity(item.product, -1) }) { Icon(Icons.Default.Remove, "Restar cantidad") }
                            Text("${item.quantity}")
                            IconButton(onClick = { vm.changeQuantity(item.product, 1) }, enabled = item.quantity < 99) { Icon(Icons.Default.Add, "Sumar cantidad") }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.remove(item.product) }) { Icon(Icons.Default.Delete, "Eliminar del carrito") }
                        }
                        Text("Subtotal: ${money(ShopRules.total(listOf(item)))}")
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total: ${money(ShopRules.total(state.cart))}", style = MaterialTheme.typography.titleLarge)
            Text("Sin cobro, envío ni pago real.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = checkout, modifier = Modifier.fillMaxWidth()) { Text("Finalizar compra simulada") }
        }
    }
}
