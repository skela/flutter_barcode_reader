package com.apptreesoftware.barcodescan

import android.app.Activity
import android.content.Intent
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar

class BarcodeScannerPlugin(private val activity: Activity): MethodCallHandler, PluginRegistry.ActivityResultListener
{
  val requestCode = 12345678

  var result : Result? = null
  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar): Unit {
      val channel = MethodChannel(registrar.messenger(), "com.apptreesoftware.barcode_scan")
      val plugin = BarcodeScannerPlugin(registrar.activity())
      channel.setMethodCallHandler(plugin)
      registrar.addActivityResultListener(plugin)
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result): Unit
  {
    when
    {
        call.method == "scan" ->
        {
          this.result = result
          showBarcodeView(call.arguments as? HashMap<String,Any>)
        }
        call.method == "close" ->
        {
          activity.finishActivity(requestCode)
          result.success(true)
        }
        else -> result.notImplemented()
    }
  }

  private fun showBarcodeView(arguments:HashMap<String,Any>?)
  {
    val intent = Intent(activity, BarcodeScannerActivity::class.java)
    if (arguments != null)
      intent.putExtra("arguments",arguments)
    activity.startActivityForResult(intent, requestCode)
  }

  override fun onActivityResult(code: Int, resultCode: Int, data: Intent?): Boolean
  {
    if (code == 100)
    {
      if (resultCode == Activity.RESULT_OK)
	  {
        val barcode = data?.getStringExtra("SCAN_RESULT")
        barcode?.let { this.result?.success(barcode) }
      }
	  else
	  {
        val errorCode = data?.getStringExtra("ERROR_CODE")
        this.result?.error(errorCode, null, null)
      }
      return true
    }
    return false
  }
}
