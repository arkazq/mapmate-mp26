package com.mapmate.data.remote.config

data class RemoteApiConfig(
    val kakaoRestApiKey: String,
    val odsayApiKey: String,
    val googleRoutesApiKey: String,
) {
    val hasKakaoKey: Boolean = kakaoRestApiKey.isNotBlank()
    val hasOdsayKey: Boolean = odsayApiKey.isNotBlank()
    val hasGoogleRoutesKey: Boolean = googleRoutesApiKey.isNotBlank()
}
