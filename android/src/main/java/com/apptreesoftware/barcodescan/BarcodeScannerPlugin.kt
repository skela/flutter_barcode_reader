package com.apptreesoftware.barcodescan

import android.app.Activity
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar

class BarcodeScannerPlugin(private val activity: Activity): MethodCallHandler, PluginRegistry.ActivityResultListener
{
  private val requestCode = 1234

  private var vibrator : BarcodeScannerVibrator? = null

  companion object
  {
      const val kShowFailureIdentifier = "com.apptreesoftware.barcode_scan.broadcast.show_failure"

    @JvmStatic
    fun registerWith(registrar: Registrar) : Unit
    {
      val channel = MethodChannel(registrar.messenger(), "com.apptreesoftware.barcode_scan")
      val plugin = BarcodeScannerPlugin(registrar.activity())
      channel.setMethodCallHandler(plugin)
      registrar.addActivityResultListener(plugin)
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) : Unit
  {
    when
    {
        call.method == "scan" ->
        {
          val args = call.arguments as? HashMap<String,Any>
          vibrator = BarcodeScannerVibrator(args)
          openScanner(args,result)
        }
        call.method == "close" ->
        {
          closeScanner(result)
        }
        call.method == "close_successfully" ->
        {
          closeScanner(result,true)
        }
        call.method == "show_failure" ->
        {
          showBarcodeFailure(call.arguments as? HashMap<String,Any>)
        }
        else -> result.notImplemented()
    }
  }

  private fun openScanner(arguments:HashMap<String,Any>?,channel:Result)
  {
    BarcodeScannerActivity.channel = channel
    if (BarcodeScannerActivity.opened)
		return
    val intent = Intent(activity, BarcodeScannerActivity::class.java)
    if (arguments != null)
      intent.putExtra("arguments",arguments)
    activity.startActivityForResult(intent, requestCode)
  }

  private fun closeScanner(result: Result,successfully:Boolean=false)
  {
    if (successfully) vibrator?.vibrate(activity,VibrationType.success)
    activity.finishActivity(requestCode)
    result.success(true)
  }

  private fun showBarcodeFailure(arguments:HashMap<String,Any>?)
  {
      val intent = Intent(kShowFailureIdentifier)
      if (arguments != null)
      {
          intent.putExtra("arguments",arguments)
      }
      LocalBroadcastManager.getInstance(activity).sendBroadcast(intent)
  }

  override fun onActivityResult(code: Int, resultCode: Int, data: Intent?): Boolean
  {
    return false
  }
}
