import MTBBarcodeScanner
import UIKit
import AVFoundation

protocol BarcodeScannerViewControllerDelegate : NSObjectProtocol 
{
    func barcodeScannerViewController(_ controller: BarcodeScannerViewController?, didScanBarcodeWithResult result: String?)
    func barcodeScannerViewController(_ controller: BarcodeScannerViewController?, didFailWithErrorCode errorCode: String)
}

enum BarcodeScannerImage
{
    case closeButton
}

class BarcodeScannerNavigationController : UINavigationController
{
    func prefersStatusBarHidden() -> Bool {
        return true
    }
    
    func preferredStatusBarStyle() -> UIStatusBarStyle
    {
        return .lightContent
    }
}

class BarcodeScannerStrings
{
    static var FlashOn = "Flash On"
    static var FlashOff = "Flash Off"
    static var Close = "Close"
    static var Loading = "Loading..."
    static var NotFound = "Not Found"
    static var Deactivated = "Scanner Deactivated"
}

class BarcodeScannerViewController : UIViewController
{
    var previewView : UIView?
    var overlay : ScannerOverlay?
    var scanner : MTBBarcodeScanner?    
    weak var delegate : BarcodeScannerViewControllerDelegate?
    
    private var closeButton : UIBarButtonItem?
    private var flashButton : UIBarButtonItem?
    
    private var wasScanning : Bool = false
    private var shouldVibrate : Bool = true
    
    private var dismissAutomaticallyOnResult = true
    private var ignores = Set<String>()
    
    convenience init(arguments:[String:Any]?)
    {
        self.init()
        if let str = arguments?["strings"] as? [String:String]
        {
            if let flashOn = str["btn_flash_on"]
            {
                BarcodeScannerStrings.FlashOn = flashOn
            }
            if let flashOff = str["btn_flash_off"]
            {
                BarcodeScannerStrings.FlashOff = flashOff
            }
            if let close = str["btn_close"]
            {
                BarcodeScannerStrings.Close = close
            }
            if let loading = str["loading"]
            {
                BarcodeScannerStrings.Loading = loading
            }
            if let notFound = str["not_found"]
            {
                BarcodeScannerStrings.NotFound = notFound
            }
            if let deactivated = str["deactivated"]
            {
                BarcodeScannerStrings.Deactivated = deactivated
            }
        }
        if let dismiss = arguments?["dismiss_automatically"] as? Bool
        {
            dismissAutomaticallyOnResult = dismiss
        }
        if let vibrate = arguments?["vibrate_on_result"] as? Bool
        {
            shouldVibrate = vibrate
        }
    }
    
    func prefersStatusBarHidden() -> Bool
    {
        return true
    }
    
    func preferredStatusBarStyle() -> UIStatusBarStyle
    {
        return .lightContent
    }

    override func viewDidLoad()
    {
        super.viewDidLoad()
        let pv = UIView(frame:self.view.bounds)
        pv.autoresizingMask = [.flexibleWidth,.flexibleHeight]
        view.addSubview(pv)
        previewView = pv
        
        let sr = ScannerOverlay(frame:self.view.bounds)
        sr.autoresizingMask = [.flexibleWidth,.flexibleHeight]
        sr.backgroundColor = .clear
        view.addSubview(sr)
        sr.startAnimating()
        overlay = sr
        
        scanner = MTBBarcodeScanner(previewView:pv)
        updateFlashButton()

        func createCloseButton() -> UIBarButtonItem
        {
            if let img = UIImage(named:"button_barcodescanner_close")?.withRenderingMode(.alwaysTemplate)
            {
                return UIBarButtonItem(image:img, style:.plain, target:self, action:#selector(cancel))
            }
            return UIBarButtonItem(title:BarcodeScannerStrings.Close, style:.plain, target:self, action:#selector(cancel))
        }

        let close = createCloseButton()
        let flash = UIBarButtonItem(title:BarcodeScannerStrings.FlashOn, style:.plain, target:self, action:#selector(toggle))
        let flex = UIBarButtonItem(barButtonSystemItem:.flexibleSpace, target:nil, action:nil)
        toolbarItems = [close,flex,flash]
        closeButton = close
        flashButton = flash
    }
    
    override func viewWillAppear(_ animated:Bool)
    {
        super.viewWillAppear(animated)
        
        navigationController?.isToolbarHidden = false
        navigationController?.setNavigationBarHidden(true, animated:false)
        
        navigationController?.toolbar.barStyle = .black
        navigationController?.toolbar.tintColor = .white
        navigationController?.toolbar.isTranslucent = true
        navigationController?.toolbar.setShadowImage(UIImage(),forToolbarPosition:.any)
    }
    
    override func viewDidAppear(_ animated:Bool)
    {
        super.viewDidAppear(animated)
        
        if let scanner = scanner
        {
            if scanner.isScanning()
            {
                scanner.stopScanning()
            }
        }
        
        MTBBarcodeScanner.requestCameraPermission()
        { success in
            
            if success {
                self.startScan()
            } else {
                self.delegate?.barcodeScannerViewController(self, didFailWithErrorCode:"PERMISSION_NOT_GRANTED")
                self.dismiss(animated: false, completion:nil)
            }
            
        }
    }
    
    override func viewDidLayoutSubviews()
    {
        super.viewDidLayoutSubviews()
        updateScanRect()
    }
    
    override func viewWillDisappear(_ animated:Bool)
    {
        self.scanner?.stopScanning()
        super.viewWillDisappear(animated)
        if self.isFlashOn()
        {
            self.toggleFlash(false)
        }
    }
    
    override func viewWillTransition(to size:CGSize, with coordinator:UIViewControllerTransitionCoordinator)
    {
        coordinator.animate(alongsideTransition: { (context:UIViewControllerTransitionCoordinatorContext) in
            self.view.setNeedsLayout()
            self.view.layoutSubviews()
            let origin:CGPoint = self.view.frame.origin
            self.scanner?.previewLayer.frame = CGRect(x:origin.x,y:origin.y,width: size.width,height: size.height)
            
            self.overlay?.stopAnimating()
            self.overlay?.layoutSubviews()

            guard let scanner = self.scanner else { return }
            if scanner.isScanning()
            {
                self.wasScanning = true
                scanner.stopScanning()                
            }
        }, completion:{ (context:UIViewControllerTransitionCoordinatorContext!)
            in            
            self.overlay?.setNeedsDisplay()
            self.overlay?.startAnimating()
            if self.wasScanning
            {
                self.startScan()
            }
        })
        
        super.viewWillTransition(to:size, with:coordinator)
    }
    
    func updateScanRect()
    {
        guard let scanner = scanner else { return }
        guard let overlay = overlay else { return }
        guard scanner.isScanning() else { return }
        scanner.scanRect = overlay.scanRect
    }
    
    func startScan()
    {
        guard let scanner = scanner else { return }
        
        do
        {
            scanner.didStartScanningBlock = {
                self.updateScanRect()
            }
            
            try scanner.startScanning()
            { codes in
                
                guard self.overlay?.isBusy == false else { return }
                guard let codes = codes else { return }
                guard let code = codes.first?.stringValue else { return }
                
                let dismiss = self.dismissAutomaticallyOnResult
                
                if dismiss
                {
                    scanner.stopScanning()
                    scanner.didStartScanningBlock = nil
                }
                
                guard self.ignores.contains(code) == false else
                {
                    self.overlay?.showFailure(msg:BarcodeScannerStrings.NotFound,barcode:code)
                    return
                }
                
                if dismiss { self.vibrate(.success) }
                //self.vibrate(dismiss ? .success : .warning)
                self.loading()
                self.delegate?.barcodeScannerViewController(self,didScanBarcodeWithResult:code)
                if dismiss
                {
                    self.dismiss(animated:false, completion:nil)
                }
            }
        }
        catch
        {
            
        }
    }
    
    @objc
    func cancel()
    {
        self.dismiss(animated:false, completion:nil)
    }
    
    func closeSuccessfully()
    {
        vibrate(.success)
        self.dismiss(animated:false, completion:nil)
    }
    
    func updateFlashButton()
    {
        if !self.hasTorch()
        {
            return
        }
        if self.isFlashOn()
        {
            self.flashButton?.title = BarcodeScannerStrings.FlashOff
        }
        else
        {
            self.flashButton?.title = BarcodeScannerStrings.FlashOn
        }
    }
    
    @objc
    func toggle()
    {
        self.toggleFlash(!self.isFlashOn())
        self.updateFlashButton()
    }
    
    func isFlashOn() -> Bool
    {
        guard let device : AVCaptureDevice = AVCaptureDevice.default(for: AVMediaType.video) else { return false }
        return device.torchMode == .on
    }
    
    func hasTorch() -> Bool
    {
        guard let device : AVCaptureDevice = AVCaptureDevice.default(for: AVMediaType.video) else { return false }
        return device.hasTorch
    }
    
    func toggleFlash(_ on:Bool)
    {
        guard let device : AVCaptureDevice = AVCaptureDevice.default(for: AVMediaType.video) else { return }
                
        if device.hasFlash && device.hasTorch
        {
            do
            {
                try device.lockForConfiguration()
                if on
                {
                    device.flashMode = .on
                    device.torchMode = .on
                }
                else
                {
                    device.flashMode = .off
                    device.torchMode = .off
                }
                
                device.unlockForConfiguration()
            }
            catch
            {
                
            }
        }
    }
    
    func vibrate(_ type:VibrationType)
    {
        guard shouldVibrate else { return }
        
        if #available(iOS 10.0, *)
        {
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(type.feedbackType)
        }
        else
        {
            AudioServicesPlayAlertSound(SystemSoundID(kSystemSoundID_Vibrate))
        }
    }
    
    func loading()
    {
        overlay?.loading(BarcodeScannerStrings.Loading)
    }
    
    func showFailure(_ failure:[String:Any]?)
    {
        vibrate(.error)
        
        guard let failure = failure else { overlay?.showFailure(); return }
        
        let barcode = failure["barcode"] as? String
        if let code = barcode
        {
            ignores.insert(code)
        }
        let msg = failure["msg"] as? String
        let delay = failure["delay"] as? Double ?? 0
        
        overlay?.showFailure(msg:msg,barcode:barcode,delay:delay)
    }
}

enum VibrationType
{
    case success
    
    case warning
    
    case error
    
    @available(iOS 10.0, *)
    var feedbackType : UINotificationFeedbackGenerator.FeedbackType
    {
        switch self
        {
        case .success: return .success
        case .warning: return .warning
        case .error: return .error
        }
    }
}
