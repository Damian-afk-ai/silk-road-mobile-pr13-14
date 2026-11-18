<#
.SYNOPSIS
  Crea el índice de productos en Elasticsearch, lo llena con el catálogo de DummyJSON
  y genera una API key de solo lectura para la app Android.

.EXAMPLE
  docker compose -f elasticsearch/docker-compose.yml up -d
  powershell -ExecutionPolicy Bypass -File elasticsearch/index-products.ps1
#>
param(
    [string]$EsUrl = "http://localhost:9200",
    [string]$Password = $(if ($env:ELASTIC_PASSWORD) { $env:ELASTIC_PASSWORD } else { "SilkRoadElastic2026" }),
    [string]$Index = "silkroad-products",
    # URL que usará la app. 10.0.2.2 es el host visto desde el emulador de Android.
    [string]$AppEsUrl = "http://10.0.2.2:9200",
    [switch]$SkipLocalProperties
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
$basic = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("elastic:$Password"))
$headers = @{ Authorization = $basic }

function Invoke-Es([string]$Method, [string]$Path, [string]$Body = $null, [string]$ContentType = "application/json") {
    $params = @{ Method = $Method; Uri = "$EsUrl/$Path"; Headers = $headers; UseBasicParsing = $true }
    if ($Body) { $params.Body = $utf8.GetBytes($Body); $params.ContentType = "$ContentType; charset=utf-8" }
    Invoke-RestMethod @params
}

# Debe coincidir con ShopRules.categoryLabel en la app.
$labels = @{
    "electronics" = "Electrónica"; "jewelery" = "Joyería"; "men's clothing" = "Ropa de hombre"
    "women's clothing" = "Ropa de mujer"; "beauty" = "Belleza"; "fragrances" = "Fragancias"
    "furniture" = "Muebles"; "groceries" = "Alimentos"; "home-decoration" = "Decoración"
    "kitchen-accessories" = "Cocina"; "laptops" = "Laptops"; "mens-shirts" = "Camisas de hombre"
    "mens-shoes" = "Calzado de hombre"; "mens-watches" = "Relojes de hombre"
    "mobile-accessories" = "Accesorios móviles"; "motorcycle" = "Motocicletas"
    "skin-care" = "Cuidado de la piel"; "smartphones" = "Teléfonos"; "sports-accessories" = "Deportes"
    "sunglasses" = "Lentes de sol"; "tablets" = "Tabletas"; "tops" = "Blusas"; "vehicle" = "Vehículos"
    "womens-bags" = "Bolsos"; "womens-dresses" = "Vestidos"; "womens-jewellery" = "Joyería"
    "womens-shoes" = "Calzado de mujer"; "womens-watches" = "Relojes de mujer"
}

Write-Host "Esperando a Elasticsearch en $EsUrl ..."
$ready = $false
for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
    try { Invoke-Es GET "_cluster/health?wait_for_status=yellow&timeout=5s" | Out-Null; $ready = $true }
    catch { Start-Sleep -Seconds 3 }
}
if (-not $ready) { throw "Elasticsearch no respondió. ¿Ejecutaste 'docker compose -f elasticsearch/docker-compose.yml up -d'?" }

Write-Host "Recreando índice '$Index' ..."
try { Invoke-Es DELETE $Index | Out-Null } catch { }
$mapping = [IO.File]::ReadAllText((Join-Path $PSScriptRoot "products-index.json"), $utf8)
Invoke-Es PUT $Index $mapping | Out-Null

Write-Host "Descargando catálogo de DummyJSON ..."
$raw = (Invoke-WebRequest -Uri "https://dummyjson.com/products?limit=0" -UseBasicParsing).RawContentStream.ToArray()
$catalog = $utf8.GetString($raw) | ConvertFrom-Json
$lines = New-Object System.Text.StringBuilder
foreach ($p in $catalog.products) {
    $label = if ($labels.ContainsKey($p.category)) { $labels[$p.category] } else { $p.category }
    $doc = [ordered]@{
        id = $p.id; title = $p.title; description = $p.description; category = $p.category
        categoryLabel = $label; price = $p.price; image = $p.thumbnail
    }
    [void]$lines.AppendLine((@{ index = @{ _index = $Index; _id = "$($p.id)" } } | ConvertTo-Json -Compress))
    [void]$lines.AppendLine(($doc | ConvertTo-Json -Compress))
}
$bulk = Invoke-Es POST "_bulk?refresh=true" $lines.ToString() "application/x-ndjson"
if ($bulk.errors) { throw "Algunos documentos no se indexaron. Revisa la respuesta de _bulk." }
Write-Host "Indexados $($catalog.products.Count) productos."

Write-Host "Creando API key de solo lectura ..."
$keyBody = @{
    name = "silkroad-mobile-search"
    role_descriptors = @{ silkroad_search = @{ indices = @(@{ names = @($Index); privileges = @("read") }) } }
} | ConvertTo-Json -Depth 6
$key = Invoke-Es POST "_security/api_key" $keyBody

if (-not $SkipLocalProperties) {
    $propsPath = Join-Path $root "local.properties"
    $existing = if (Test-Path $propsPath) { [IO.File]::ReadAllLines($propsPath) | Where-Object { $_ -notmatch '^elasticsearch\.' } } else { @() }
    $new = @($existing) + @("elasticsearch.url=$AppEsUrl", "elasticsearch.apiKey=$($key.encoded)", "elasticsearch.index=$Index")
    [IO.File]::WriteAllLines($propsPath, $new, $utf8)
    Write-Host "local.properties actualizado. Sincroniza Gradle y recompila la app."
} else {
    Write-Host "elasticsearch.url=$AppEsUrl"
    Write-Host "elasticsearch.apiKey=$($key.encoded)"
    Write-Host "elasticsearch.index=$Index"
}
