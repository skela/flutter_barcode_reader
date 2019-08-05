import UIKit

fileprivate extension UIColor
{
    static var bad : UIColor { return .red }
    static var line : UIColor { return bad }
    static var disabledLine : UIColor { return UIColor(red: 0, green: 0, blue: 0, alpha: 0.38) }
    
    static var status : UIColor { return UIColor.black.withAlphaComponent(0.6) }
    static var loading : UIColor { return UIColor.black.withAlphaComponent(0.25) }
}

enum ScannerState
{
    case normal
    case loading
    case error
    case delayedError
}

class ScannerOverlay: UIView
{
    let cornerRadius : CGFloat = 30
    let cornerSize : CGFloat = 60
    
    var line = UIView()
    var disabledLine = UIView()
    var status = UILabel()
    
    private var touching = false
    private var state : ScannerState = .normal
    
    var isBusy : Bool
    {
        switch state
        {
        case .normal: return touching
        case .loading: return true
        case .delayedError: return true
        case .error: return touching
        }
    }
    
    var scanLineRect : CGRect
    {
        let scanRect : CGRect = self.scanRect
        let rect : CGRect = frame
        return CGRect(x: scanRect.origin.x, y: rect.size.height / 2, width: scanRect.size.width, height: 1)
    }
    
    var scanRect : CGRect
    {
        let rect: CGRect = frame
        let heightMultiplier: CGFloat = 3.0 / 4.0 // 4:3 aspect ratio
        let w: CGFloat = min(rect.width,rect.height) * 0.8
        let h: CGFloat = w * heightMultiplier
        let x: CGFloat = (rect.width / 2) - (w / 2)
        let y: CGFloat = (rect.height / 2) - (h / 2)
        let ret = CGRect(x:x,y:y,width:w,height:h)
        return ret
    }
    
    func startAnimating()
    {
        let flash = CABasicAnimation(keyPath: "opacity")
        flash.fromValue = NSNumber(value: 0.0)
        flash.toValue = NSNumber(value: 1.0)
        flash.duration = 0.25
        flash.autoreverses = true
        flash.repeatCount = Float.greatestFiniteMagnitude
        line.layer.add(flash, forKey: "flashAnimation")
    }
    
    func stopAnimating()
    {
        layer.removeAnimation(forKey: "flashAnimation")
    }
    
    override init(frame: CGRect)
    {
        super.init(frame: frame)
        setup()
    }
    
    required init?(coder aDecoder: NSCoder)
    {
        super.init(coder:aDecoder)
        setup()
    }
    
    private func setup()
    {
        backgroundColor = .clear
        
        line.backgroundColor = .line
        line.translatesAutoresizingMaskIntoConstraints = false
        addSubview(line)
        
        disabledLine.backgroundColor = .disabledLine
        disabledLine.translatesAutoresizingMaskIntoConstraints = false
        disabledLine.isHidden = true
        addSubview(disabledLine)
        
        if #available(iOS 8.2, *)
        {
            status.font = .systemFont(ofSize:16,weight:.medium)
        }
        else
        {
            status.font = .boldSystemFont(ofSize:16)
        }
        status.textColor = .status
        status.lineBreakMode = .byWordWrapping
        status.numberOfLines = 2
        status.textAlignment = .center
        status.translatesAutoresizingMaskIntoConstraints = false
        addSubview(status)
    }
    
    private(set) var isTouching : Bool
    {
        get
        {
            return touching
        }
        set(value)
        {
            guard touching != value else { return }
            touching = value
            line.isHidden = value
            disabledLine.isHidden = !value
            status.text = value ? BarcodeScannerStrings.Deactivated : nil
            setNeedsDisplay()
        }
    }
    
    func loading(_ msg:String)
    {
        status.text = msg
        state = .loading
        stateChanged()
    }
    
    func showFailure(msg:String?=nil,barcode:String?=nil,delay:Double=0)
    {
        if let msg = msg
        {
            if delay == 0
            {
                state = .error
            }
            else
            {
                state = .delayedError
            }

            if let barcode = barcode
            {
                let paragraphStyle = NSMutableParagraphStyle()
                paragraphStyle.lineSpacing = 0
                paragraphStyle.alignment = .center
                
                status.attributedText = NSAttributedString(string:"\(msg)\n(ID: \(barcode))",attributes: [.paragraphStyle:paragraphStyle])
            }
            else
            {
                status.text = msg
            }
        }
        else
        {
            state = .normal
            status.text = nil
        }
        stateChanged()
        
        if delay > 0 && msg != nil
        {
            DispatchQueue.main.asyncAfter(deadline:.now() + delay)
            {
                self.state = .error
                self.stateChanged()
            }
        }
    }
    
    private func stateChanged()
    {
        setNeedsDisplay()
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?)
    {
        super.touchesBegan(touches,with:event)
        isTouching = true
    }
    
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?)
    {
        super.touchesMoved(touches,with:event)
        isTouching = true
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?)
    {
        super.touchesEnded(touches,with:event)
        isTouching = false
    }
    
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?)
    {
        super.touchesCancelled(touches,with:event)
        isTouching = false
    }
    
    var borderColor : UIColor
    {
        switch state
        {
        case .normal: return touching ? .bad : .white
        case .loading: return .loading
        case .error: return .bad
        case .delayedError: return .bad
        }
    }
    
    override func draw(_ rect: CGRect)
    {
        let holeRect: CGRect = scanRect
        let holeRectIntersection: CGRect = holeRect.intersection(rect)
        
        let lineRect: CGRect = scanLineRect
        line.frame = lineRect
        disabledLine.frame = lineRect
        status.frame = CGRect(x:holeRect.minX,y:max(10,holeRect.minY-50-24),width:holeRect.width,height:50)
        
        let cornerspath = UIBezierPath(roundedRect: holeRectIntersection, cornerRadius: cornerRadius)
        cornerspath.lineWidth = 3
        borderColor.setStroke()
        cornerspath.stroke()
        
        let path = UIBezierPath()
        
        path.move(to: CGPoint(x: holeRect.origin.x+cornerSize, y: holeRect.origin.y))
        path.addLine(to: CGPoint(x:holeRect.origin.x+holeRect.width-cornerSize, y: holeRect.origin.y))
        
        path.move(to: CGPoint(x: holeRect.origin.x+cornerSize, y: holeRect.origin.y+holeRect.height))
        path.addLine(to: CGPoint(x:holeRect.origin.x+holeRect.width-cornerSize, y: holeRect.origin.y+holeRect.height))
        
        path.move(to: CGPoint(x: holeRect.origin.x, y: holeRect.origin.y+cornerSize))
        path.addLine(to: CGPoint(x:holeRect.origin.x, y: holeRect.origin.y+holeRect.height-cornerSize))
        
        path.move(to: CGPoint(x: holeRect.origin.x+holeRect.width, y: holeRect.origin.y+cornerSize))
        path.addLine(to: CGPoint(x:holeRect.origin.x+holeRect.width, y: holeRect.origin.y+holeRect.height-cornerSize))
        
        path.lineWidth = 3
        path.stroke(with:.clear,alpha:1)
    }
}
