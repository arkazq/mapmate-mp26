package com.mapmate.data.remote.config

data class RemoteApiConfig(
    val kakaoRestApiKey: String,
    val odsayApiKey: String,
    val googleRoutesApiKey: String,
    val origin: RemoteCoordinate?,
) {
    val hasKakaoKey: Boolean = kakaoRestApiKey.isNotBlank()
    val hasOdsayKey: Boolean = odsayApiKey.isNotBlank()
    val hasGoogleRoutesKey: Boolean = googleRoutesApiKey.isNotBlank()
}

data class RemoteCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            (latitude != 0.0 || longitude != 0.0)
    }
}
