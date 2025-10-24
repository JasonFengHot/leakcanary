package leakcanary

import android.app.AlertDialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.squareup.leakcanary.core.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import leakcanary.EventListener.Event
import leakcanary.EventListener.Event.DumpingHeap
import leakcanary.EventListener.Event.HeapAnalysisProgress
import leakcanary.EventListener.Event.HeapDump
import leakcanary.EventListener.Event.HeapDumpFailed
import leakcanary.internal.InternalLeakCanary
import leakcanary.internal.activity.LeakActivity
import leakcanary.internal.friendly.mainHandler

object ToastEventListener : EventListener {

  // Only accessed from the main thread
  private var toastCurrentlyShown: AlertDialog? = null
  private var toastTextView: TextView? = null

  override fun onEvent(event: Event) {
    when (event) {
      is DumpingHeap -> {
        showToastBlocking()
      }

      is HeapDump -> {
        mainHandler.post {
          toastTextView?.setText("HeapDump ...")
        }
      }

      is HeapDumpFailed -> {
        mainHandler.post {
          toastTextView?.setText("HeapDumpFailed ...")
        }
      }

      is HeapAnalysisProgress -> {
        mainHandler.post {
          toastTextView?.setText("Analyzing heap dump... ${event.progressPercent * 100}%")
        }
      }

      is Event.HeapAnalysisDone<*> -> {
        mainHandler.post {
          toastTextView?.setText("Analyzing heap dump... 100%")
          toastCurrentlyShown?.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = View.VISIBLE
        }
      }
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

      val inflater = LayoutInflater.from(resumedActivity)
      val dialogView = inflater.inflate(R.layout.leak_canary_heap_dump_toast, null)

      // Store reference to the TextView for progress updates
      toastTextView = dialogView.findViewById(R.id.leak_canary_toast_text)

      val dialog = AlertDialog.Builder(resumedActivity)
        .setTitle(resumedActivity.packageName)
        .setIcon(R.drawable.leak_canary_icon)
        .setView(dialogView)
        .setCancelable(false)
        .setPositiveButton(
          "View Details"
        ) { dialog, which ->
          waitingForToast.countDown()
          val intent = Intent()
          intent.setClass(resumedActivity.application, LeakActivity::class.java)
          resumedActivity.startActivity(intent)

          toastCurrentlyShown?.cancel()
          toastCurrentlyShown = null
          toastTextView = null
        }
        .show()
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.INVISIBLE
      toastCurrentlyShown = dialog
    })
    waitingForToast.await(5, SECONDS)
  }
}
