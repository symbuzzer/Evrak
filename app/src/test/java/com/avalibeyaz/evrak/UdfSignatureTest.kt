package com.avalibeyaz.evrak

import com.avalibeyaz.evrak.ui.UdfToHtmlConverter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UdfSignatureTest {

    @Test
    fun testSignSgnParsing() {
        val sampleFile = File("C:\\Users\\Ali Beyaz\\Desktop\\sign.sgn.txt")
        if (sampleFile.exists()) {
            val bytes = sampleFile.readBytes()
            val parseMethod = UdfToHtmlConverter::class.java.getDeclaredMethod("parseSignersFromBytes", ByteArray::class.java)
            parseMethod.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val signers = parseMethod.invoke(UdfToHtmlConverter, bytes) as List<String>
            assertTrue("Signers list should not be empty", signers.isNotEmpty())
            assertTrue("Signers should contain ALİ BEYAZ", signers.contains("ALİ BEYAZ"))
        }
    }
}
