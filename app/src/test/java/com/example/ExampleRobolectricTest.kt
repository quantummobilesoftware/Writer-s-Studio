package com.example
 
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WriterDatabase
import com.example.data.repository.WriterRepository
import com.example.ui.screens.WriterAppMainLayout
import com.example.ui.viewmodel.WriterViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Writer's Studio", appName)
  }

  @Test
  fun `render main layout crash test`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = WriterDatabase.getDatabase(context)
    val repository = WriterRepository(database)
    val viewModel = WriterViewModel(repository)

    composeTestRule.setContent {
      WriterAppMainLayout(viewModel)
    }
  }
}

