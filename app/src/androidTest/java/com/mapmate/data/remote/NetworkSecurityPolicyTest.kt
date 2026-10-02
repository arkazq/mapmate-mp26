package com.mapmate.data.remote

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSecurityPolicyTest {
    @Test fun cleartextIsDeniedForTagoAndUnrelatedHosts() {
        val policy = NetworkSecurityPolicy.getInstance()
        for (host in listOf("apis.data.go.kr", "dapi.kakao.com", "api.odsay.com", "routes.googleapis.com", "invalid.example")) {
            assertFalse(host, policy.isCleartextTrafficPermitted(host))
        }
    }

    @Test fun legacySeoulExceptionsDoNotPermitArbitrarySubdomains() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertTrue(policy.isCleartextTrafficPermitted("ws.bus.go.kr"))
        assertTrue(policy.isCleartextTrafficPermitted("swopenapi.seoul.go.kr"))
        assertFalse(policy.isCleartextTrafficPermitted("fixture.ws.bus.go.kr"))
        assertFalse(policy.isCleartextTrafficPermitted("fixture.swopenapi.seoul.go.kr"))
    }
}
