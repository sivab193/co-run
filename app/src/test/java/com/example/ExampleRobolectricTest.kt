package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.RoomCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun readAppNameAndParseDeepLinks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Run Together", context.getString(R.string.app_name))

        assertEquals(
            "K7M4P9",
            RoomCodeGenerator.extractRoomCodeFromUri(Uri.parse("runtogether://join/k7m4p9"))
        )
        assertEquals(
            "K7M4P9",
            RoomCodeGenerator.extractRoomCodeFromUri(Uri.parse("https://runtogether.app/join/K7M4P9"))
        )
    }
}
