param(
    [string]$LocalPropertiesPath = "local.properties"
)

$ErrorActionPreference = "Stop"

function Read-LocalProperties {
    param([string]$Path)

    $properties = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $properties
    }

    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $separatorIndex = $line.IndexOf("=")
            $key = $line.Substring(0, $separatorIndex).Trim()
            $value = $line.Substring($separatorIndex + 1).Trim()
            $properties[$key] = $value
        }
    }

    return $properties
}

function Format-KeyState {
    param(
        [string]$Name,
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return "${Name}: missing"
    }

    return "${Name}: present"
}

function Invoke-ApiCheck {
    param(
        [string]$Name,
        [scriptblock]$Check
    )

    try {
        & $Check
    } catch {
        $message = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($message)) {
            $message = $_.Exception.Message
        }
        Write-Output "${Name}: failed, $message"
    }
}

function Format-ServiceKeyForQuery {
    param([string]$ServiceKey)

    if ($ServiceKey -match "%[0-9A-Fa-f]{2}") {
        return $ServiceKey
    }

    return [uri]::EscapeDataString($ServiceKey)
}

function Invoke-KakaoKeywordSearch {
    param([string]$ApiKey)

    $query = [uri]::EscapeDataString("숭실대학교")
    $uri = "https://dapi.kakao.com/v2/local/search/keyword.json?query=$query&size=1"
    $response = Invoke-RestMethod -Method Get -Uri $uri -Headers @{
        Authorization = "KakaoAK $ApiKey"
    }

    $first = $response.documents | Select-Object -First 1
    if ($null -eq $first) {
        Write-Output "Kakao keyword search: OK, no documents"
        return
    }

    Write-Output "Kakao keyword search: OK, first='$($first.place_name)', lat=$($first.y), lon=$($first.x)"
}

function Invoke-KakaoReverseGeocode {
    param([string]$ApiKey)

    $longitude = "126.9574"
    $latitude = "37.4963"
    $uri = "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$longitude&y=$latitude&input_coord=WGS84"
    $response = Invoke-RestMethod -Method Get -Uri $uri -Headers @{
        Authorization = "KakaoAK $ApiKey"
    }

    $first = $response.documents | Select-Object -First 1
    if ($null -eq $first) {
        Write-Output "Kakao reverse geocode: OK, no documents"
        return
    }

    $address = $null
    if ($null -ne $first.road_address -and -not [string]::IsNullOrWhiteSpace($first.road_address.address_name)) {
        $address = $first.road_address.address_name
    } elseif ($null -ne $first.address -and -not [string]::IsNullOrWhiteSpace($first.address.address_name)) {
        $address = $first.address.address_name
    }

    Write-Output "Kakao reverse geocode: OK, address='$address'"
}

function Invoke-OdsayTransitRoute {
    param([string]$ApiKey)

    $encodedKey = [uri]::EscapeDataString($ApiKey)
    $uri = "https://api.odsay.com/v1/api/searchPubTransPathT?SX=126.9574&SY=37.4963&EX=127.0276&EY=37.4979&apiKey=$encodedKey"
    $response = Invoke-RestMethod -Method Get -Uri $uri

    if ($null -ne $response.error) {
        Write-Output "ODsay transit route: API error code=$($response.error.code), message='$($response.error.msg)'"
        return
    }

    $firstPath = $response.result.path | Select-Object -First 1
    if ($null -eq $firstPath) {
        Write-Output "ODsay transit route: OK, no paths"
        return
    }

    Write-Output "ODsay transit route: OK, totalTime=$($firstPath.info.totalTime) min, payment=$($firstPath.info.payment)"
}

function Invoke-GoogleRoutesDrive {
    param([string]$ApiKey)

    $uri = "https://routes.googleapis.com/directions/v2:computeRoutes"
    $body = @{
        origin = @{
            location = @{
                latLng = @{
                    latitude = 37.4963
                    longitude = 126.9574
                }
            }
        }
        destination = @{
            location = @{
                latLng = @{
                    latitude = 37.4979
                    longitude = 127.0276
                }
            }
        }
        travelMode = "DRIVE"
        routingPreference = "TRAFFIC_AWARE"
        languageCode = "ko-KR"
        units = "METRIC"
    } | ConvertTo-Json -Depth 10

    $response = Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/json" -Body $body -Headers @{
        "X-Goog-Api-Key" = $ApiKey
        "X-Goog-FieldMask" = "routes.duration,routes.description,routes.localizedValues.duration"
    }
    $first = $response.routes | Select-Object -First 1
    if ($null -eq $first) {
        Write-Output "Google Routes drive: OK, no routes"
        return
    }

    Write-Output "Google Routes drive: OK, duration=$($first.duration), localized='$($first.localizedValues.duration.text)'"
}

function Invoke-SeoulSubwayArrival {
    param([string]$ApiKey)

    $station = [uri]::EscapeDataString("숭실대입구")
    $uri = "http://swopenapi.seoul.go.kr/api/subway/$ApiKey/json/realtimeStationArrival/0/5/$station"
    $response = Invoke-RestMethod -Method Get -Uri $uri
    $count = @($response.realtimeArrivalList).Count
    Write-Output "Seoul subway arrival: OK, count=$count"
}

function Invoke-SeoulSubwayPosition {
    param([string]$ApiKey)

    $line = [uri]::EscapeDataString("2호선")
    $uri = "http://swopenapi.seoul.go.kr/api/subway/$ApiKey/json/realtimePosition/0/5/$line"
    $response = Invoke-RestMethod -Method Get -Uri $uri
    $count = @($response.realtimePositionList).Count
    Write-Output "Seoul subway position: OK, count=$count"
}

function Invoke-SeoulBusRouteArrival {
    param(
        [string]$ApiKey,
        [string]$BusRouteId
    )

    if ([string]::IsNullOrWhiteSpace($BusRouteId)) {
        Write-Output "Seoul bus route arrival: skipped because MAPMATE_VERIFY_SEOUL_BUS_ROUTE_ID is missing"
        return
    }

    $encodedKey = Format-ServiceKeyForQuery -ServiceKey $ApiKey
    $uri = "http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRouteAll?serviceKey=$encodedKey&busRouteId=$BusRouteId"
    $response = Invoke-RestMethod -Method Get -Uri $uri
    $count = @($response.ServiceResult.msgBody.itemList).Count
    Write-Output "Seoul bus route arrival: OK, count=$count"
}

function Invoke-SeoulBusRoutePosition {
    param(
        [string]$ApiKey,
        [string]$BusRouteId
    )

    if ([string]::IsNullOrWhiteSpace($BusRouteId)) {
        Write-Output "Seoul bus route position: skipped because MAPMATE_VERIFY_SEOUL_BUS_ROUTE_ID is missing"
        return
    }

    $encodedKey = Format-ServiceKeyForQuery -ServiceKey $ApiKey
    $uri = "http://ws.bus.go.kr/api/rest/buspos/getBusPosByRtid?serviceKey=$encodedKey&busRouteId=$BusRouteId"
    $response = Invoke-RestMethod -Method Get -Uri $uri
    $count = @($response.ServiceResult.msgBody.itemList).Count
    Write-Output "Seoul bus route position: OK, count=$count"
}

function Invoke-TagoNearbyStations {
    param([string]$ApiKey)

    $encodedKey = Format-ServiceKeyForQuery -ServiceKey $ApiKey
    $uri = "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList?serviceKey=$encodedKey&pageNo=1&numOfRows=3&_type=json&gpsLati=36.3&gpsLong=127.3"
    $response = Invoke-RestMethod -Method Get -Uri $uri
    $items = @($response.response.body.items.item)
    $first = $items | Select-Object -First 1
    if ($null -eq $first) {
        Write-Output "TAGO nearby stations: OK, no stations"
        return
    }

    Write-Output "TAGO nearby stations: OK, count=$($items.Count), first='$($first.nodenm)', cityCode=$($first.citycode), nodeId=$($first.nodeid)"
}

function Invoke-TagoStationArrivals {
    param(
        [string]$ApiKey,
        [string]$CityCode,
        [string]$NodeId
    )

    if ([string]::IsNullOrWhiteSpace($CityCode) -or [string]::IsNullOrWhiteSpace($NodeId)) {
        Write-Output "TAGO station arrivals: skipped because MAPMATE_VERIFY_TAGO_CITY_CODE or MAPMATE_VERIFY_TAGO_NODE_ID is missing"
        return
    }

    $encodedKey = Format-ServiceKeyForQuery -ServiceKey $ApiKey
    $uri = "http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList?serviceKey=$encodedKey&pageNo=1&numOfRows=5&_type=json&cityCode=$CityCode&nodeId=$NodeId"
    $response = Invoke-RestMethod -Method Get -Uri $uri
    $items = @($response.response.body.items.item)
    Write-Output "TAGO station arrivals: OK, count=$($items.Count)"
}

$properties = Read-LocalProperties -Path $LocalPropertiesPath
$kakaoKey = $properties["KAKAO_REST_API_KEY"]
$odsayKey = $properties["ODSAY_API_KEY"]
$googleRoutesKey = $properties["GOOGLE_ROUTES_API_KEY"]
$seoulOpenApiKey = $properties["SEOUL_OPEN_API_KEY"]
$seoulBusServiceKey = $properties["SEOUL_BUS_SERVICE_KEY"]
$tagoServiceKey = $properties["TAGO_SERVICE_KEY"]
$seoulBusRouteId = $properties["MAPMATE_VERIFY_SEOUL_BUS_ROUTE_ID"]
$tagoCityCode = $properties["MAPMATE_VERIFY_TAGO_CITY_CODE"]
$tagoNodeId = $properties["MAPMATE_VERIFY_TAGO_NODE_ID"]

Write-Output (Format-KeyState -Name "KAKAO_REST_API_KEY" -Value $kakaoKey)
Write-Output (Format-KeyState -Name "ODSAY_API_KEY" -Value $odsayKey)
Write-Output (Format-KeyState -Name "GOOGLE_ROUTES_API_KEY" -Value $googleRoutesKey)
Write-Output (Format-KeyState -Name "SEOUL_OPEN_API_KEY" -Value $seoulOpenApiKey)
Write-Output (Format-KeyState -Name "SEOUL_BUS_SERVICE_KEY" -Value $seoulBusServiceKey)
Write-Output (Format-KeyState -Name "TAGO_SERVICE_KEY" -Value $tagoServiceKey)

if (-not [string]::IsNullOrWhiteSpace($kakaoKey)) {
    Invoke-ApiCheck -Name "Kakao keyword search" -Check {
        Invoke-KakaoKeywordSearch -ApiKey $kakaoKey
    }
    Invoke-ApiCheck -Name "Kakao reverse geocode" -Check {
        Invoke-KakaoReverseGeocode -ApiKey $kakaoKey
    }
} else {
    Write-Output "Kakao API validation: skipped because KAKAO_REST_API_KEY is missing"
}

if (-not [string]::IsNullOrWhiteSpace($odsayKey)) {
    Invoke-ApiCheck -Name "ODsay transit route" -Check {
        Invoke-OdsayTransitRoute -ApiKey $odsayKey
    }
} else {
    Write-Output "ODsay API validation: skipped because ODSAY_API_KEY is missing"
}

if (-not [string]::IsNullOrWhiteSpace($googleRoutesKey)) {
    Invoke-ApiCheck -Name "Google Routes drive" -Check {
        Invoke-GoogleRoutesDrive -ApiKey $googleRoutesKey
    }
} else {
    Write-Output "Google Routes validation: skipped because GOOGLE_ROUTES_API_KEY is missing"
}

if (-not [string]::IsNullOrWhiteSpace($seoulOpenApiKey)) {
    Invoke-ApiCheck -Name "Seoul subway arrival" -Check {
        Invoke-SeoulSubwayArrival -ApiKey $seoulOpenApiKey
    }
    Invoke-ApiCheck -Name "Seoul subway position" -Check {
        Invoke-SeoulSubwayPosition -ApiKey $seoulOpenApiKey
    }
} else {
    Write-Output "Seoul subway API validation: skipped because SEOUL_OPEN_API_KEY is missing"
}

if (-not [string]::IsNullOrWhiteSpace($seoulBusServiceKey)) {
    Invoke-ApiCheck -Name "Seoul bus route arrival" -Check {
        Invoke-SeoulBusRouteArrival -ApiKey $seoulBusServiceKey -BusRouteId $seoulBusRouteId
    }
    Invoke-ApiCheck -Name "Seoul bus route position" -Check {
        Invoke-SeoulBusRoutePosition -ApiKey $seoulBusServiceKey -BusRouteId $seoulBusRouteId
    }
} else {
    Write-Output "Seoul bus API validation: skipped because SEOUL_BUS_SERVICE_KEY is missing"
}

if (-not [string]::IsNullOrWhiteSpace($tagoServiceKey)) {
    Invoke-ApiCheck -Name "TAGO nearby stations" -Check {
        Invoke-TagoNearbyStations -ApiKey $tagoServiceKey
    }
    Invoke-ApiCheck -Name "TAGO station arrivals" -Check {
        Invoke-TagoStationArrivals -ApiKey $tagoServiceKey -CityCode $tagoCityCode -NodeId $tagoNodeId
    }
} else {
    Write-Output "TAGO API validation: skipped because TAGO_SERVICE_KEY is missing"
}
