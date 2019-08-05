import Flutter
import UIKit

public class SwiftBarcodeScannerPlugin : NSObject,FlutterPlugin,BarcodeScannerViewControllerDelegate
{
    @objc public static func register(with registrar:FlutterPluginRegistrar)
    {
        let channel = FlutterMethodChannel(name:"com.apptreesoftware.barcode_scan",binaryMessenger:registrar.messenger())
        let instance = SwiftBarcodeScannerPlugin()
        instance.hostViewController = UIApplication.shared.delegate?.window??.rootViewController
        registrar.addMethodCallDelegate(instance,channel:channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult)
    {
        switch call.method
        {
            case "scan": self.openScanner(call.arguments as? [String:Any],result)
            case "close": self.closeScanner(result)
            case "close_successfully": self.closeScanner(result,successfully:true)
            case "show_failure": self.showBarcodeFailure(call.arguments as? [String:Any])
            default: result(FlutterMethodNotImplemented)
        }
    }
    
    var hostViewController : UIViewController?
    var callback : ((Any?)->())?

    func openScanner(_ arguments:[String:Any]?,_ result:@escaping FlutterResult)
    {
        callback = result
        
        let navVC = hostViewController?.presentedViewController as? BarcodeScannerNavigationController
        let scanner = navVC?.topViewController as? BarcodeScannerViewController
        
        guard scanner == nil else { return }
        
        let scannerViewController = BarcodeScannerViewController(arguments:arguments)
        let nav = BarcodeScannerNavigationController(rootViewController:scannerViewController)        
        scannerViewController.delegate = self
        hostViewController?.present(nav,animated:false,completion:nil)
    }
    
    func closeScanner(_ result:FlutterResult,successfully:Bool=false)
    {
        guard let nav = hostViewController?.presentedViewController as? BarcodeScannerNavigationController else { result(false); return }
        guard let scanner = nav.topViewController as? BarcodeScannerViewController else { result(false); return }
        if successfully
        {
            scanner.closeSuccessfully()
        }
        else
        {
            scanner.cancel()
        }
        result(true)
    }
    
    func showBarcodeFailure(_ failure:[String:Any]?)
    {
        guard let nav = hostViewController?.presentedViewController as? BarcodeScannerNavigationController else { return }
        guard let scanner = nav.topViewController as? BarcodeScannerViewController else { return }
        scanner.showFailure(failure)
    }
    
    func barcodeScannerViewController(_ controller: BarcodeScannerViewController?, didScanBarcodeWithResult result: String?)
    {        
        callback?(result)
    }

    func barcodeScannerViewController(_ controller: BarcodeScannerViewController?, didFailWithErrorCode errorCode: String)
    {
        callback?(FlutterError(code:errorCode,message:nil,details:nil))
    }
}
