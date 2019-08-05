package com.apptreesoftware.barcodescan

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.Camera
import android.os.*
import androidx.core.app.ActivityCompat
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.yourcompany.barcodescan.R
import io.flutter.plugin.common.MethodChannel
import me.dm7.barcodescanner.core.DisplayUtils
import me.dm7.barcodescanner.core.IViewFinder
import me.dm7.barcodescanner.zxing.ZXingScannerView
import java.util.*
import kotlin.collections.HashMap

class BarcodeScannerStrings
{
	companion object
	{
		var FlashOn = "Flash On"
		var FlashOff = "Flash Off"
		var Close = "Close"
		var Loading = "Loading..."
		var NotFound = "Not Found"
		var Deactivated = "Scanner Deactivated"
	}
}

class BarcodeScannerVibrator(arguments: HashMap<String, Any>?)
{
	private val shouldVibrate : Boolean = arguments?.get("vibrate_on_result") as? Boolean ?: true

	fun vibrate(act:Activity,type:VibrationType)
	{
		if (!shouldVibrate) return

		val vibrator = act.getSystemService(Activity.VIBRATOR_SERVICE) as Vibrator

		if (Build.VERSION.SDK_INT >= 26)
		{
			vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
		}
		else
		{
			vibrator.vibrate(150)
		}
	}
}

enum class VibrationType
{
	success,
	warning,
	error
}

class BarcodeScannerActivity : Activity(), ZXingScannerView.ResultHandler
{
	private val TAG = "BarcodeScannerActivity"

    private lateinit var scannerView : BarcodeScannerLayout

	private lateinit var camera : LinearLayout
	private lateinit var closeButton : ImageButton
	private lateinit var flashButton : Button
	private lateinit var status : TextView

    companion object
    {
        const val REQUEST_TAKE_PHOTO_CAMERA_PERMISSION = 100
		var channel : MethodChannel.Result? = null
		var opened : Boolean = false
    }

	private var dismissAutomaticallyOnResult = true

	private lateinit var vibrator : BarcodeScannerVibrator
	private val ignores : MutableSet<String> = mutableSetOf()

	private val overlay : BarcodeScannerViewFinder?
	get()
	{
		return scannerView.viewFinder
	}

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        title = ""

        setContentView(R.layout.activity_barcodescanner)

		val arguments : HashMap<String,Any>? = intent.getSerializableExtra("arguments") as? HashMap<String, Any>
		val strings = arguments?.get("strings") as? HashMap<String,String>
		if (strings!=null)
		{
			val flashOn = strings["btn_flash_on"]
			if (flashOn != null)
				BarcodeScannerStrings.FlashOn = flashOn
			val flashOff = strings["btn_flash_off"]
			if (flashOff != null)
				BarcodeScannerStrings.FlashOff = flashOff
			val loading = strings["loading"]
			if (loading != null)
				BarcodeScannerStrings.Loading = loading
			val notFound = strings["not_found"]
			if (notFound != null)
				BarcodeScannerStrings.NotFound = notFound
			val deactivated = strings["deactivated"]
			if (deactivated != null)
				BarcodeScannerStrings.Deactivated = deactivated
		}
		val dismiss = arguments?.get("dismiss_automatically") as? Boolean
		if (dismiss != null)
		{
			dismissAutomaticallyOnResult = dismiss
		}

		vibrator = BarcodeScannerVibrator(arguments)

		status = findViewById(R.id.status)

        scannerView = BarcodeScannerLayout(this)
		scannerView.viewFinder?.status = status
		camera = findViewById(R.id.camera)
		camera.addView(scannerView)

		closeButton = findViewById(R.id.close_button)
		flashButton = findViewById(R.id.flash_button)

		flashButton.text = BarcodeScannerStrings.FlashOn
    }

    override fun onResume()
	{
        super.onResume()

		opened = true

        scannerView.setResultHandler(this)
        // start camera immediately if permission is already given
        if (!requestCameraAccessIfNecessary()) {
            scannerView.startCamera()
        }

		LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(BarcodeScannerPlugin.kShowFailureIdentifier))
    }

    override fun onPause()
	{
        super.onPause()
        scannerView.stopCamera()

		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

	override fun onStop()
	{
		super.onStop()
		opened = false
	}

	fun clickedToggleFlash(v: View)
	{
		scannerView.flash = !scannerView.flash
		if (scannerView.flash)
			flashButton.text = BarcodeScannerStrings.FlashOff
		else
			flashButton.text = BarcodeScannerStrings.FlashOn
	}

	fun clickedClose(v:View?)
	{
		scannerView.setResultHandler(null)
		finishWithError("")
	}

	override fun onBackPressed()
	{
		clickedClose(null)
	}

	private fun vibrate(type:VibrationType)
	{
		vibrator.vibrate(this,type)
	}

	private fun loading()
	{
		overlay?.loading(BarcodeScannerStrings.Loading)
	}

	private fun showFailure(failure:HashMap<String,Any>?)
	{
		vibrate(VibrationType.error)

		if (failure == null)
		{
			overlay?.showFailure()
			return
		}

		val barcode = failure["barcode"] as? String
		if (barcode != null)
		{
			ignores.add(barcode)
		}

		val msg = failure["msg"] as? String
		val delay = failure["delay"] as? Double ?: 0.0

		overlay?.showFailure(msg,barcode,delay)
	}

    override fun handleResult(result: Result?)
	{
		if (channel == null)
			return

		val vf = overlay
		if (vf != null)
		{
			if (vf.isBusy)
			{
				Log.d(TAG,"Scanner is busy")
				return
			}
		}

		val code = result.toString()

		Log.d(TAG,"Scanner found code $code")

		val dismiss = dismissAutomaticallyOnResult

		if (dismiss)
		{
			scannerView.setResultHandler(null)
		}

		if (ignores.contains(code))
		{
			Log.d(TAG,"Scanner ignored $code")
			overlay?.showFailure(BarcodeScannerStrings.NotFound,code)
			return
		}

		if (dismiss)
		{
			vibrate(VibrationType.success)
		}

		Log.d(TAG,"Scanner loading")
		loading()

		channel?.success(code)

		channel = null

		if (dismiss)
        	finish()
    }

	private fun finishWithError(errorCode: String)
	{
		channel?.error(errorCode, null, null)
        finish()
    }

    private fun requestCameraAccessIfNecessary(): Boolean {
        val array = arrayOf(Manifest.permission.CAMERA)
        if (checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, array,
                    REQUEST_TAKE_PHOTO_CAMERA_PERMISSION)
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,grantResults: IntArray) {
        when (requestCode) {
            REQUEST_TAKE_PHOTO_CAMERA_PERMISSION -> {
                if (PermissionUtil.verifyPermissions(grantResults)) {
                    scannerView.startCamera()
                } else {
                    finishWithError("PERMISSION_NOT_GRANTED")
                }
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

	private val receiver = object : BroadcastReceiver()
	{
		override fun onReceive(contxt: Context?, intent: Intent?)
		{
			when (intent?.action)
			{
				BarcodeScannerPlugin.kShowFailureIdentifier ->
				{
					val arguments = intent.getSerializableExtra("arguments") as? HashMap<String,Any>
					showFailure(arguments)
				}
				else -> {}
			}
		}
	}
}

object PermissionUtil {

    /**
     * Check that all given permissions have been granted by verifying that each entry in the
     * given array is of the value [PackageManager.PERMISSION_GRANTED].

     * @see Activity.onRequestPermissionsResult
     */
    fun verifyPermissions(grantResults: IntArray): Boolean {
        // At least one result must be checked.
        if (grantResults.size < 1) {
            return false
        }

        // Verify that each required permission has been granted, otherwise return false.
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
}

class BarcodeScannerLayout(context: Context?) : me.dm7.barcodescanner.zxing.ZXingScannerView(context)
{
	var viewFinder : BarcodeScannerViewFinder? = null

	private var resultHandler : ResultHandler? = null

	private var barcodeDecoder : MultiFormatReader = MultiFormatReader()

	init
	{
		setAutoFocus(true)
		// this paramter will make your HUAWEI phone works great!
		setAspectTolerance(0.5f)

		val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
		hints[DecodeHintType.POSSIBLE_FORMATS] = formats
		barcodeDecoder.setHints(hints)
	}

	override fun createViewFinderView(context: Context): IViewFinder
	{
		val vf = BarcodeScannerViewFinder(context)
		viewFinder = vf
		return vf
	}

	override fun setResultHandler(resultHandler:ResultHandler?)
	{
		super.setResultHandler(resultHandler)
		this.resultHandler = resultHandler
	}

	override fun onPreviewFrame(data: ByteArray, camera: Camera)
	{
		var data = data
		if (resultHandler == null)
		{
			return
		}

		try
		{
			val parameters = camera.parameters
			val size = parameters.previewSize
			var width = size.width
			var height = size.height

			if (DisplayUtils.getScreenOrientation(context) == Configuration.ORIENTATION_PORTRAIT)
			{
				val rotationCount = rotationCount
				if (rotationCount == 1 || rotationCount == 3)
				{
					val tmp = width
					width = height
					height = tmp
				}
				data = getRotatedData(data, camera)
			}

			var rawResult: Result? = null
			val source = buildLuminanceSource(data, width, height)

			if (source != null)
			{
				var bitmap = BinaryBitmap(HybridBinarizer(source))
				try
				{
					rawResult = barcodeDecoder.decodeWithState(bitmap)
				}
				catch (re: ReaderException)
				{
					// continue
				}
				catch (npe: NullPointerException)
				{
					// This is terrible
				}
				catch (aoe: ArrayIndexOutOfBoundsException)
				{

				}
				finally
				{
					barcodeDecoder.reset()
				}

				if (rawResult == null)
				{
					val invertedSource = source.invert()
					bitmap = BinaryBitmap(HybridBinarizer(invertedSource))
					try
					{
						rawResult = barcodeDecoder.decodeWithState(bitmap)
					}
					catch (e: NotFoundException)
					{
						// continue
					}
					finally
					{
						barcodeDecoder.reset()
					}
				}
			}

			val finalRawResult = rawResult

			if (finalRawResult != null)
			{
				val handler = Handler(Looper.getMainLooper())
				handler.post {
					val tmpResultHandler = resultHandler
					tmpResultHandler?.handleResult(finalRawResult)
					camera.setOneShotPreviewCallback(this)
				}
			}
			else
			{
				camera.setOneShotPreviewCallback(this)
			}
		}
		catch (e: RuntimeException)
		{

		}
	}
}

enum class ScannerState
{
	normal,
	loading,
	error,
	delayedError
}

class BarcodeScannerViewFinder : View, IViewFinder, View.OnTouchListener
{
	private var mFramingRect: Rect? = null
	private var scannerAlpha: Int = 0

	private val mDefaultBorderStrokeWidth = resources.getInteger(R.integer.viewfinder_border_width)
	private val mDefaultBorderLineLength = resources.getInteger(R.integer.viewfinder_border_length)

	private var mLaserPaint : Paint
	private var mLaserDisabledPaint : Paint
	private var mFinderMaskPaint : Paint
	private var mClearPaint : Paint
	private var mBorderLineLength: Int = 0
	private var mSquareViewFinder: Boolean = false
	private var mIsLaserEnabled: Boolean = true
	private var mBordersAlpha: Float = 0.toFloat()
	private var mViewFinderOffset = 0

	private val cornerRadius : Float = 30.0f

	var status : TextView? = null

	private var normalBorder : Paint
	private var badBorder : Paint
	private var loadingBorder : Paint

	constructor(context: Context) : super(context)

	constructor(context: Context,attributeSet: AttributeSet) : super(context, attributeSet)

	init
	{
		//set up laser paint
		mLaserPaint = Paint()
		mLaserPaint.color = resources.getColor(R.color.viewfinder_laser)
		mLaserPaint.style = Paint.Style.FILL

		mLaserDisabledPaint = Paint()
		mLaserDisabledPaint.color = resources.getColor(R.color.viewfinder_laser_disabled)
		mLaserDisabledPaint.style = Paint.Style.FILL

		//finder mask paint
		mFinderMaskPaint = Paint()
		mFinderMaskPaint.color = resources.getColor(R.color.viewfinder_mask)

		mClearPaint = Paint()
		mClearPaint.color = resources.getColor(R.color.viewfinder_clear)
		mClearPaint.style = Paint.Style.STROKE
		mClearPaint.strokeWidth = mDefaultBorderStrokeWidth.toFloat()
		mClearPaint.isAntiAlias = true
		mClearPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

		//border paint
		normalBorder = Paint()
		normalBorder.color = resources.getColor(R.color.viewfinder_border)
		normalBorder.style = Paint.Style.STROKE
		normalBorder.strokeWidth = mDefaultBorderStrokeWidth.toFloat()
		normalBorder.isAntiAlias = true

		badBorder = Paint()
		badBorder.color = resources.getColor(R.color.viewfinder_border_bad)
		badBorder.style = Paint.Style.STROKE
		badBorder.strokeWidth = mDefaultBorderStrokeWidth.toFloat()
		badBorder.isAntiAlias = true

		loadingBorder = Paint()
		loadingBorder.color = resources.getColor(R.color.viewfinder_border_loading)
		loadingBorder.style = Paint.Style.STROKE
		loadingBorder.strokeWidth = mDefaultBorderStrokeWidth.toFloat()
		loadingBorder.isAntiAlias = true

		mBorderLineLength = mDefaultBorderLineLength
	}

	override fun onAttachedToWindow()
	{
		super.onAttachedToWindow()
		setOnTouchListener(this)
	}

	override fun onDetachedFromWindow()
	{
		super.onDetachedFromWindow()
		setOnTouchListener(null)
	}

	override fun onTouch(v: View?, event: MotionEvent?) : Boolean
	{
		val ev = event ?: return true
		when(ev.action)
		{
			MotionEvent.ACTION_UP -> isTouching = false
			MotionEvent.ACTION_DOWN -> isTouching = true
			MotionEvent.ACTION_MOVE -> isTouching = true
			MotionEvent.ACTION_CANCEL -> isTouching = false
			MotionEvent.ACTION_OUTSIDE -> isTouching = false
			else -> {}
		}
		return true
	}

	private var state : ScannerState = ScannerState.normal

	private var touching = false
	var isTouching : Boolean
	get()
	{
		return touching
	}
	private set(value)
	{
		val changed = value != touching
		touching = value
		status?.text = if (value) BarcodeScannerStrings.Deactivated else null
		if(changed)
		{
			invalidate()
		}
	}

	val isBusy : Boolean
	get()
	{
		return when(state)
		{
			ScannerState.normal -> touching
			ScannerState.loading -> true
			ScannerState.delayedError -> true
			ScannerState.error -> touching
		}
	}

	fun loading(msg:String)
	{
		status?.text = msg
		state = ScannerState.loading
		stateChanged()
	}

	fun showFailure(msg:String?=null,barcode:String?=null,delay:Double=0.0)
	{
		if (msg != null)
		{
			state = if (delay == 0.0)
			{
				ScannerState.error
			}
			else
			{
				ScannerState.delayedError
			}

			if (barcode != null)
			{
				status?.text = "$msg\n(ID: $barcode)"
			}
			else
			{
				status?.text = msg
			}
		}
		else
		{
			state = ScannerState.normal
			status?.text = null
		}
		stateChanged()

		if (delay > 0 && msg != null)
		{
			Handler().postDelayed({
				state = ScannerState.error
				stateChanged()
			}, (delay * 1000).toLong())
		}
	}

	private fun stateChanged()
	{
		invalidate()
	}

	override fun setLaserColor(laserColor: Int)
	{
		mLaserPaint.color = laserColor
	}

	override fun setMaskColor(maskColor: Int)
	{
		mFinderMaskPaint.color = maskColor
	}

	override fun setBorderColor(borderColor: Int)
	{
		normalBorder.color = borderColor
	}

	override fun setBorderStrokeWidth(borderStrokeWidth: Int)
	{
		normalBorder.strokeWidth = borderStrokeWidth.toFloat()
	}

	override fun setBorderLineLength(borderLineLength: Int)
	{
		mBorderLineLength = borderLineLength
	}

	override fun setLaserEnabled(isLaserEnabled: Boolean)
	{
		mIsLaserEnabled = isLaserEnabled
	}

	override fun setBorderCornerRounded(isBorderCornersRounded: Boolean)
	{
		if (isBorderCornersRounded)
		{
			normalBorder.strokeJoin = Paint.Join.ROUND
		}
		else
		{
			normalBorder.strokeJoin = Paint.Join.BEVEL
		}
	}

	override fun setBorderAlpha(alpha: Float)
	{
		val colorAlpha = (255 * alpha).toInt()
		mBordersAlpha = alpha
		normalBorder.alpha = colorAlpha
	}

	override fun setBorderCornerRadius(borderCornersRadius: Int)
	{
		normalBorder.pathEffect = CornerPathEffect(borderCornersRadius.toFloat())
	}

	override fun setViewFinderOffset(offset: Int)
	{
		mViewFinderOffset = offset
	}

	// TODO: Need a better way to configure this. Revisit when working on 2.0
	override fun setSquareViewFinder(set: Boolean)
	{
		mSquareViewFinder = set
	}

	override fun setupViewFinder()
	{
		updateFramingRect()
		invalidate()
	}

	override fun getFramingRect(): Rect?
	{
		return mFramingRect
	}

	public override fun onDraw(canvas: Canvas)
	{
		if (framingRect == null)
		{
			return
		}

		drawViewFinderMask(canvas)
		drawViewFinderBorder(canvas)

		if (mIsLaserEnabled)
		{
			drawLaser(canvas)
		}
	}

	private fun drawViewFinderMask(canvas: Canvas)
	{
//		val width = canvas.width
//		val height = canvas.height
//		val framingRect = framingRect
//
//		canvas.drawRect(0f, 0f, width.toFloat(), framingRect!!.top.toFloat(), mFinderMaskPaint)
//		canvas.drawRect(0f, framingRect.top.toFloat(), framingRect.left.toFloat(), (framingRect.bottom + 1).toFloat(), mFinderMaskPaint)
//		canvas.drawRect((framingRect.right + 1).toFloat(), framingRect.top.toFloat(), width.toFloat(), (framingRect.bottom + 1).toFloat(), mFinderMaskPaint)
//		canvas.drawRect(0f, (framingRect.bottom + 1).toFloat(), width.toFloat(), height.toFloat(), mFinderMaskPaint)
	}

	private val borderColor : Paint
	get()
	{
		return when(state)
		{
			ScannerState.normal -> if(touching) badBorder else normalBorder
			ScannerState.loading -> loadingBorder
			ScannerState.error -> badBorder
			ScannerState.delayedError -> badBorder
		}
	}

	private fun drawViewFinderBorder(canvas: Canvas)
	{
		val holeRect = framingRect ?: return
		val cornerSize = mBorderLineLength.toFloat()

		val holeRectF = RectF(holeRect)
		val pathBorder = Path()
		pathBorder.addRoundRect(holeRectF,cornerRadius,cornerRadius,Path.Direction.CW)
		canvas.drawPath(pathBorder,borderColor)

		val path = Path()

		val x = holeRect.left.toFloat()
		val y = holeRect.top.toFloat()
		val w = holeRect.width().toFloat()
		val h = holeRect.height().toFloat()

		path.moveTo(x + cornerSize,y)
		path.lineTo(x+w-cornerSize,y)

		path.moveTo(x+cornerSize,y+h)
		path.lineTo(x+w-cornerSize,y+h)

		path.moveTo(x,y+cornerSize)
		path.lineTo(x,y+h-cornerSize)

		path.moveTo(x+w,y+cornerSize)
		path.lineTo(x+w,y+h-cornerSize)

		canvas.drawPath(path, mClearPaint)

//		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
//		{
//			pathBorder.op(path,Path.Op.REVERSE_DIFFERENCE)
//			canvas.drawPath(pathBorder, mBorderPaint)
//		}
	}

	fun drawLaser(canvas: Canvas)
	{
		val framingRect = framingRect ?: return
		val middle = framingRect.height() / 2 + framingRect.top

		if (!touching)
		{
			mLaserPaint.alpha = SCANNER_ALPHA[scannerAlpha]
			scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.size

			canvas.drawRect((framingRect.left + 2).toFloat(), (middle - 1).toFloat(), (framingRect.right - 1).toFloat(), (middle + 2).toFloat(), mLaserPaint)
		}
		else
		{
			canvas.drawRect((framingRect.left + 2).toFloat(), (middle - 1).toFloat(), (framingRect.right - 1).toFloat(), (middle + 2).toFloat(), mLaserDisabledPaint)
		}

		if (!touching)
			postInvalidateDelayed(ANIMATION_DELAY, framingRect.left - POINT_SIZE, framingRect.top - POINT_SIZE, framingRect.right + POINT_SIZE, framingRect.bottom + POINT_SIZE)
	}

	override fun onSizeChanged(xNew: Int, yNew: Int, xOld: Int, yOld: Int)
	{
		updateFramingRect()
	}

	@Synchronized
	fun updateFramingRect()
	{
		val viewResolution = Point(width, height)
		var width: Int
		var height: Int
		val orientation = DisplayUtils.getScreenOrientation(context)

		if (mSquareViewFinder)
		{
			if (orientation != Configuration.ORIENTATION_PORTRAIT)
			{
				height = (getHeight() * DEFAULT_SQUARE_DIMENSION_RATIO).toInt()
				width = height
			}
			else
			{
				width = (getWidth() * DEFAULT_SQUARE_DIMENSION_RATIO).toInt()
				height = width
			}
		}
		else
		{
			if (orientation != Configuration.ORIENTATION_PORTRAIT)
			{
				height = (getHeight() * LANDSCAPE_HEIGHT_RATIO).toInt()
				width = (LANDSCAPE_WIDTH_HEIGHT_RATIO * height).toInt()
			}
			else
			{
				width = (getWidth() * PORTRAIT_WIDTH_RATIO).toInt()
				height = (PORTRAIT_WIDTH_HEIGHT_RATIO * width).toInt()
			}
		}

		if (width > getWidth())
		{
			width = getWidth() - MIN_DIMENSION_DIFF
		}

		if (height > getHeight())
		{
			height = getHeight() - MIN_DIMENSION_DIFF
		}

		val leftOffset = (viewResolution.x - width) / 2
		val topOffset = (viewResolution.y - height) / 2
		val rect = Rect(leftOffset + mViewFinderOffset, topOffset + mViewFinderOffset, leftOffset + width - mViewFinderOffset, topOffset + height - mViewFinderOffset)
		mFramingRect = rect

		val status = status
		if (status != null)
		{
			val params = status.layoutParams as? RelativeLayout.LayoutParams
			if (params != null)
			{
				params.topMargin = rect.top - status.height
			}
			status.layoutParams = params
		}
	}

	companion object
	{
		private val TAG = "ViewFinderView"

		private val PORTRAIT_WIDTH_RATIO = 6f / 8
		private val PORTRAIT_WIDTH_HEIGHT_RATIO = 0.75f

		private val LANDSCAPE_HEIGHT_RATIO = 5f / 8
		private val LANDSCAPE_WIDTH_HEIGHT_RATIO = 1.4f
		private val MIN_DIMENSION_DIFF = 50

		private val DEFAULT_SQUARE_DIMENSION_RATIO = 5f / 8

		private val SCANNER_ALPHA = intArrayOf(0, 64, 128, 192, 255, 192, 128, 64)
		private val POINT_SIZE = 10
		private val ANIMATION_DELAY = 80L
	}
}
