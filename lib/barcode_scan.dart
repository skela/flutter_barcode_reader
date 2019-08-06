import 'dart:async';

import 'package:flutter/services.dart';

class BarcodeScanner
{
  static const CameraAccessDenied = 'PERMISSION_NOT_GRANTED';
  static const BarcodeNotFound = 'BARCODE_NOT_FOUND';

  static const MethodChannel _channel = const MethodChannel('com.apptreesoftware.barcode_scan');

  static Future<BarcodeScannerResult> scan({BarcodeScannerStrings strings,bool dismissAutomatically=true}) async
  {
    try
    {
      var str = strings ?? BarcodeScannerStrings();
      var arguments = {"strings":str.toDict,"dismiss_automatically":dismissAutomatically};
      var barcode = await _channel.invokeMethod('scan',arguments);
      return Future.value(BarcodeScannerResult(barcode:barcode));
    }
    on PlatformException catch (e)
    {
      if (e.code == BarcodeScanner.CameraAccessDenied)
        return Future.value(BarcodeScannerResult(error:BarcodeScannerError.CameraAccessDenied));
      else if (e.code == BarcodeScanner.BarcodeNotFound)
        return Future.value(BarcodeScannerResult(error:BarcodeScannerError.BarcodeNotFound));
    }
    return Future.value(BarcodeScannerResult(error:BarcodeScannerError.Other));
  }

  static Future<bool> close(bool successfully) async
  {
    var res = await _channel.invokeMethod(successfully ? 'close_successfully' : 'close');
    if (res is bool)
      return Future.value(res);
    return Future.value(false);
  }

  static showFailure(BarcodeFailure failure)
  {
    _channel.invokeMethod('show_failure',failure.toDict);
  }
}

class BarcodeScannerStrings
{
  String flashOnButton = "Flash On";
  String flashOffButton = "Flash Off";

  String loading = "Loading...";
  String notFound = "Not Found";
  String deactivated = "Scanner Deactivated";

  dynamic get toDict
  {
    return <String,String>{'btn_flash_on':flashOnButton,"btn_flash_off":flashOffButton,"loading":loading,"not_found":notFound,"deactivated":deactivated};
  }
}

class BarcodeFailure
{
  final String barcode;
  final String msg;

  final double delay;

  BarcodeFailure({this.barcode,this.msg,this.delay=0});

  dynamic get toDict
  {
    if (barcode != null)
      return <String,dynamic>{"barcode":barcode,"msg":msg,"delay":delay};
    return <String,dynamic>{"msg":msg,"delay":delay};
  }
}

enum BarcodeScannerError
{
  None,
  CameraAccessDenied,
  BarcodeNotFound,
  Other
}

class BarcodeScannerResult
{
  String barcode;
  BarcodeScannerError error;

  BarcodeScannerResult({this.barcode,this.error=BarcodeScannerError.None});
}
