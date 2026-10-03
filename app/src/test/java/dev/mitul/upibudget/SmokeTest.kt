package dev.mitul.upibudget

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun toolchainRunsAndOrgJsonIsRealNotStubbed() {
        assertEquals(5, JSONObject("""{"a":5}""").getInt("a"))
    }
}
