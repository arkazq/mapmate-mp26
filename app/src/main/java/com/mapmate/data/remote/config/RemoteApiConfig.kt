package com.mapmate.data.remote.config

data class RemoteApiConfig(
    val kakaoRestApiKey: String,
    val odsayApiKey: String,
    val googleRoutesApiKey: String,
    val seoulOpenApiKey: String,
    val seoulBusServiceKey: String,
    val tagoServiceKey: String,
) {
    val hasKakaoKey: Boolean = kakaoRestApiKey.isNotBlank()
    val hasOdsayKey: Boolean = odsayApiKey.isNotBlank()
    val hasGoogleRoutesKey: Boolean = googleRoutesApiKey.isNotBlank()
    val hasSeoulOpenApiKey: Boolean = seoulOpenApiKey.isNotBlank()
    val hasSeoulBusServiceKey: Boolean = seoulBusServiceKey.isNotBlank()
    val hasTagoServiceKey: Boolean = tagoServiceKey.isNotBlank()
}
