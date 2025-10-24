package leakcanary

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import com.squareup.leakcanary.core.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import leakcanary.EventListener.Event
import leakcanary.EventListener.Event.DumpingHeap
import leakcanary.EventListener.Event.HeapAnalysisProgress
import leakcanary.EventListener.Event.HeapDump
import leakcanary.EventListener.Event.HeapAnalysisDone
import leakcanary.EventListener.Event.HeapDumpFailed
import leakcanary.internal.InternalLeakCanary
import leakcanary.internal.activity.LeakActivity
import leakcanary.internal.friendly.mainHandler

object ToastEventListener : EventListener {

  // Only accessed from the main thread
  private var toastCurrentlyShown: AlertDialog? = null

  override fun onEvent(event: Event) {
    when (event) {
      is DumpingHeap -> {
        showToastBlocking()
      }
      is HeapDumpFailed, is HeapAnalysisDone<*> -> {
        // Close any dialog once analysis cannot continue or is finished
        mainHandler.post {
          toastCurrentlyShown?.cancel()
          toastCurrentlyShown = null
        }
      }
      is HeapAnalysisProgress -> {
        val percent = (event.progressPercent * 100).toInt()
        val stepName = event.step.humanReadableName
        mainHandler.post {
          val appContext = InternalLeakCanary.application
          toastCurrentlyShown?.setMessage(
            appContext.getString(R.string.leak_canary_notification_analysing) +
              "\n$percent% - $stepName"
          )
        }
      }
      else -> {}
    }
  }

  @Suppress("DEPRECATION")
  private fun showToastBlocking() {
    val appContext = InternalLeakCanary.application
    val waitingForToast = CountDownLatch(1)
    mainHandler.post(Runnable {
      val resumedActivity = InternalLeakCanary.resumedActivity
      if (resumedActivity == null || toastCurrentlyShown != null) {
        waitingForToast.countDown()
        return@Runnable
      }

      val initialMessage = appContext.getString(
        R.string.leak_canary_toast_heap_dump,
        resumedActivity.packageName
      )

      val dialog = AlertDialog.Builder(resumedActivity)
        .setTitle(resumedActivity.packageName)
        .setIcon(R.drawable.leak_canary_icon)
        .setMessage(initialMessage)
        .setPositiveButton(
          "View Details"
        ) { dialogInterface, which ->
          // Keep reference to the dialog so we can update or close it later
          toastCurrentlyShown = dialog
          waitingForToast.countDown()
          val intent = Intent()
          intent.setClass(resumedActivity.application, LeakActivity::class.java)
          resumedActivity.startActivity(intent)
        }
        .create()

      toastCurrentlyShown = dialog
      dialog.show()
    })
    waitingForToast.await(5, SECONDS)
  }
}
