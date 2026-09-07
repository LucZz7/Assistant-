package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.AndroidActionBridge
import com.example.service.AndroidActionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Necxa", appName)
  }

  @Test
  fun `test action bridge initialization and functions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val actionManager = AndroidActionManager(context)
    val bridge = AndroidActionBridge(actionManager)

    assertTrue(bridge.isAvailable())

    // Test call contact for Mom
    val momCallResult = bridge.callContact("Mom")
    assertNotNull(momCallResult)
    assertTrue(momCallResult.contains("Calling Mom") || momCallResult.contains("Mom"))

    // Test multiple contacts for Rahul (found 2 Rahuls)
    val rahulCallResult = bridge.callContact("Rahul")
    assertNotNull(rahulCallResult)
    assertTrue(rahulCallResult.contains("Rahul"))

    // Test make call with phone number
    val callResult = bridge.makeCall("9876543210")
    assertNotNull(callResult)
    assertTrue(callResult.contains("9876543210"))
  }
}

