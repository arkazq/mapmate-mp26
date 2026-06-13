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

    return "${Name}: present (length=$($Value.Length))"
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

$properties = Read-LocalProperties -Path $LocalPropertiesPath
$kakaoKey = $properties["KAKAO_REST_API_KEY"]
$odsayKey = $properties["ODSAY_API_KEY"]
$googleRoutesKey = $properties["GOOGLE_ROUTES_API_KEY"]
$seoulOpenApiKey = $properties["SEOUL_OPEN_API_KEY"]
$seoulBusServiceKey = $properties["SEOUL_BUS_SERVICE_KEY"]
$tagoServiceKey = $properties["TAGO_SERVICE_KEY"]

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
