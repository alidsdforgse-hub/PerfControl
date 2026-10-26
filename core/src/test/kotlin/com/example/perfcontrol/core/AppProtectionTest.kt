package com.example.perfcontrol.core

import org.junit.Assert.*
import org.junit.Test

class AppProtectionTest {
    private val p = AppProtection(ProtectionContext(setOf("com.kb"), setOf("com.chat")))

    @Test fun fixedPackagesAreProtected() = assertTrue(p.isProtected("com.android.systemui"))
    @Test fun dynamicPackagesAreProtected() = assertTrue(p.isProtected("com.kb"))
    @Test fun userExclusionsAreProtected() = assertTrue(p.isProtected("com.chat"))
    @Test fun ordinaryAppIsNotProtected() = assertFalse(p.isProtected("com.game"))
    @Test fun suffixedProcessResolvesToBasePackage() = assertTrue(p.isProtected("com.miui.home:x"))
}
