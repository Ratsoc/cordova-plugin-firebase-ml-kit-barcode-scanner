package tl.cordova.plugin.firebase.mlkit.barcode.scanner.camera;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.StringDef;

// ----------------------------------------------------------------------------
// |  Google Imports
// ----------------------------------------------------------------------------
import com.google.android.gms.common.images.Size;

// ----------------------------------------------------------------------------
// |  Java Imports
// ----------------------------------------------------------------------------
import java.io.IOException;
import java.lang.Thread.State;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ----------------------------------------------------------------------------
// |  Our Imports
// ----------------------------------------------------------------------------
import tl.cordova.plugin.firebase.mlkit.barcode.scanner.BarcodeScanningProcessor;

// The CameraSource send the preview frames to the barcode detector.
@SuppressWarnings("deprecation")
public class CameraSource2 {
  // ----------------------------------------------------------------------------
  // | Public Properties
  // ----------------------------------------------------------------------------
  @SuppressLint("InlinedApi")
  public static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;
  @SuppressLint("InlinedApi")
  public static final int CAMERA_FACING_FRONT = CameraInfo.CAMERA_FACING_FRONT;

  // ----------------------------------------------------------------------------
  // | Private Properties
  // ----------------------------------------------------------------------------
  private static final String TAG = "OpenCameraSource";

  /**
   * The dummy surface texture must be assigned a chosen name. Since we never use
   * an OpenGL context, we can choose any ID we want here.
   */
  private static final int DUMMY_TEXTURE_NAME = 100;

  /**
   * If the absolute difference between a preview size aspect ratio and a picture
   * size aspect ratio is less than this tolerance, they are considered to be the
   * same aspect ratio.
   */
  private static final float ASPECT_RATIO_TOLERANCE = 0.01f;

  private Context                  _Context                                    ;
  private final Object             _CameraLock             = new Object()      ;
  private Camera                   _Camera                                     ;
  private int                      _Facing                 = CAMERA_FACING_BACK;
  private int                      _Rotation                                   ;
  private Size                     _PreviewSize                                ;
  private float                    _RequestedFps           = 30.0f             ;
  private int                      _RequestedPreviewWidth  = 1024              ;
  private int                      _RequestedPreviewHeight = 768               ;
  private String                   _FocusMode              = null              ;
  private String                   _FlashMode              = null              ;
  private SurfaceView              _DummySurfaceView                           ;
  private SurfaceTexture           _DummySurfaceTexture                        ;
  private Thread                   _ProcessingThread                           ;
  private FrameProcessingRunnable  _FrameProcessor                             ;
  private Map<byte[], ByteBuffer>  _BytesToByteBuffer      = new HashMap<>()   ;
  private BarcodeScanningProcessor _ScanningProcessor                          ;

  // ----------------------------------------------------------------------------
  // | Helpers
  // ----------------------------------------------------------------------------
  @StringDef({ Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE, Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
      Camera.Parameters.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_EDOF, Camera.Parameters.FOCUS_MODE_FIXED,
      Camera.Parameters.FOCUS_MODE_INFINITY, Camera.Parameters.FOCUS_MODE_MACRO })
  @Retention(RetentionPolicy.SOURCE)
  private @interface FocusMode {
  };

  @StringDef({ Camera.Parameters.FLASH_MODE_ON, Camera.Parameters.FLASH_MODE_OFF, Camera.Parameters.FLASH_MODE_AUTO,
      Camera.Parameters.FLASH_MODE_RED_EYE, Camera.Parameters.FLASH_MODE_TORCH })
  @Retention(RetentionPolicy.SOURCE)
  private @interface FlashMode {
  };

  public interface ShutterCallback {
    void onShutter();
  }

  public interface PictureCallback {
    void onPictureTaken(byte[] data);
  }

  public interface AutoFocusCallback {
    void onAutoFocus(boolean success);
  }

  public interface AutoFocusMoveCallback {
    void onAutoFocusMoving(boolean start);
  }

  // ----------------------------------------------------------------------------
  // | Builder
  // ----------------------------------------------------------------------------
  public static class Builder {
    private CameraSource2 _CameraSource = new CameraSource2();

    public Builder(Context context, BarcodeScanningProcessor scanningProcessor) {
      if (context == null) {
        throw new IllegalArgumentException("No context supplied.");
      }
      if (scanningProcessor == null) {
        throw new IllegalArgumentException("No processor supplied.");
      }

      _CameraSource._ScanningProcessor = scanningProcessor;
      _CameraSource._Context = context;
    }

    public Builder setRequestedFps(float fps) {
      if (fps <= 0) {
        throw new IllegalArgumentException("Invalid fps: " + fps);
      }
      _CameraSource._RequestedFps = fps;
      return this;
    }

    public Builder setFocusMode(@FocusMode String mode) {
      _CameraSource._FocusMode = mode;
      return this;
    }

    public Builder setFlashMode(@FlashMode String mode) {
      _CameraSource._FlashMode = mode;
      return this;
    }

    public Builder setRequestedPreviewSize(int width, int height) {
      final int MAX = 1000000;
      if ((width <= 0) || (width > MAX) || (height <= 0) || (height > MAX)) {
        throw new IllegalArgumentException("Invalid preview size: " + width + "x" + height);
      }
      _CameraSource._RequestedPreviewWidth = width;
      _CameraSource._RequestedPreviewHeight = height;
      return this;
    }

    public Builder setFacing(int facing) {
      if ((facing != CAMERA_FACING_BACK) && (facing != CAMERA_FACING_FRONT)) {
        throw new IllegalArgumentException("Invalid camera: " + facing);
      }
      _CameraSource._Facing = facing;
      return this;
    }
    
    public CameraSource2 build() {
      _CameraSource._FrameProcessor = _CameraSource.new FrameProcessingRunnable();
      return _CameraSource;
    }
  }

  // ----------------------------------------------------------------------------
  // | Constructor
  // ---------------------------------------------------------------------------- 
  private CameraSource2() { // Constructor is private to force creation using the builder class.
  }

  // ----------------------------------------------------------------------------
  // | Public Functions
  // ---------------------------------------------------------------------------- 
  public void release() {
    synchronized (_CameraLock) {
      stop();
      _FrameProcessor.release();

      if (_ScanningProcessor != null) {
        _ScanningProcessor.Stop();
      }
    }
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  public CameraSource2 start() throws IOException {
    synchronized (_CameraLock) {
      if (_Camera != null) {
        return this;
      }

      _Camera = createCamera();

      // SurfaceTexture was introduced in Honeycomb (11), so if we are running and
      // old version of Android. fall back to use SurfaceView.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        _DummySurfaceTexture = new SurfaceTexture(DUMMY_TEXTURE_NAME);
        _Camera.setPreviewTexture(_DummySurfaceTexture);
      } else {
        _DummySurfaceView = new SurfaceView(_Context);
        _Camera.setPreviewDisplay(_DummySurfaceView.getHolder());
      }
      _Camera.startPreview();

      _ProcessingThread = new Thread(_FrameProcessor);
      _FrameProcessor.setActive(true);
      _ProcessingThread.start();
    }
    return this;
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  public CameraSource2 start(SurfaceHolder p_SurfaceHolder) throws IOException {
    synchronized (_CameraLock) {
      if (_Camera != null) {
        return this;
      }

      _Camera = createCamera();
      _Camera.setPreviewDisplay(p_SurfaceHolder);
      _Camera.startPreview();

      _ProcessingThread = new Thread(_FrameProcessor);
      _FrameProcessor.setActive(true);
      _ProcessingThread.start();
    }
    return this;
  }

  public void stop() {
    synchronized (_CameraLock) {
      _FrameProcessor.setActive(false);
      if (_ProcessingThread != null) {
        try {
          // Wait for the thread to complete to ensure that we can't have multiple threads
          // executing at the same time (i.e., which would happen if we called start too
          // quickly after stop).
          _ProcessingThread.join();
        } catch (InterruptedException e) {
          Log.d(TAG, "Frame processing thread interrupted on release.");
        }
        _ProcessingThread = null;
      }

      // clear the buffer to prevent oom exceptions
      _BytesToByteBuffer.clear();

      if (_Camera != null) {
        _Camera.stopPreview();
        _Camera.setPreviewCallbackWithBuffer(null);
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            _Camera.setPreviewTexture(null);
          } else {
            _Camera.setPreviewDisplay(null);
          }
        } catch (Exception e) {
          Log.e(TAG, "Failed to clear camera preview: " + e);
        }
        _Camera.release();
        _Camera = null;
      }
    }
  }

  public Size getPreviewSize() {
    return _PreviewSize;
  }

  public int getCameraFacing() {
    return _Facing;
  }

  public int doZoom(float p_Scale) {
    synchronized (_CameraLock) {
      if (_Camera == null) {
        return 0;
      }
      int currentZoom = 0;
      int maxZoom;
      Camera.Parameters parameters = _Camera.getParameters();
      if (!parameters.isZoomSupported()) {
        Log.w(TAG, "Zoom is not supported on this device");
        return currentZoom;
      }
      maxZoom = parameters.getMaxZoom();

      currentZoom = parameters.getZoom() + 1;
      float newZoom;
      if (p_Scale > 1) {
        newZoom = currentZoom + p_Scale * (maxZoom / 10);
      } else {
        newZoom = currentZoom * p_Scale;
      }
      currentZoom = Math.round(newZoom) - 1;
      if (currentZoom < 0) {
        currentZoom = 0;
      } else if (currentZoom > maxZoom) {
        currentZoom = maxZoom;
      }
      parameters.setZoom(currentZoom);
      _Camera.setParameters(parameters);
      return currentZoom;
    }
  }

  public void takePicture(ShutterCallback p_Shutter, PictureCallback p_Jpeg) {
    synchronized (_CameraLock) {
      if (_Camera != null) {
        PictureStartCallback startCallback = new PictureStartCallback();
        startCallback._Delegate = p_Shutter;
        PictureDoneCallback doneCallback = new PictureDoneCallback();
        doneCallback._Delegate = p_Jpeg;
        _Camera.takePicture(startCallback, null, null, doneCallback);
      }
    }
  }

  @Nullable
  @FocusMode
  public String getFocusMode() {
    return _FocusMode;
  }

  public boolean setFocusMode(@FocusMode String p_Mode) {
    synchronized (_CameraLock) {
      if (_Camera != null && p_Mode != null) {
        Camera.Parameters parameters = _Camera.getParameters();
        if (parameters.getSupportedFocusModes().contains(p_Mode)) {
          parameters.setFocusMode(p_Mode);
          _Camera.setParameters(parameters);
          _FocusMode = p_Mode;
          return true;
        }
      }

      return false;
    }
  }

  @Nullable
  @FlashMode
  public String getFlashMode() {
    return _FlashMode;
  }

  public boolean setFlashMode(@FlashMode String p_Mode) {
    synchronized (_CameraLock) {
      if (_Camera != null && p_Mode != null) {
        Camera.Parameters parameters = _Camera.getParameters();
        if (parameters.getSupportedFlashModes().contains(p_Mode)) {
          parameters.setFlashMode(p_Mode);
          _Camera.setParameters(parameters);
          _FlashMode = p_Mode;
          return true;
        }
      }

      return false;
    }
  }

  public void autoFocus(@Nullable AutoFocusCallback p_Callback) {
    synchronized (_CameraLock) {
      if (_Camera != null) {
        CameraAutoFocusCallback autoFocusCallback = null;
        if (p_Callback != null) {
          autoFocusCallback = new CameraAutoFocusCallback();
          autoFocusCallback._Delegate = p_Callback;
        }
        _Camera.autoFocus(autoFocusCallback);
      }
    }
  }

  public void cancelAutoFocus() {
    synchronized (_CameraLock) {
      if (_Camera != null) {
        _Camera.cancelAutoFocus();
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  public boolean setAutoFocusMoveCallback(@Nullable AutoFocusMoveCallback p_Callback) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      return false;
    }

    synchronized (_CameraLock) {
      if (_Camera != null) {
        CameraAutoFocusMoveCallback autoFocusMoveCallback = null;
        if (p_Callback != null) {
          autoFocusMoveCallback = new CameraAutoFocusMoveCallback();
          autoFocusMoveCallback._Delegate = p_Callback;
        }
        _Camera.setAutoFocusMoveCallback(autoFocusMoveCallback);
      }
    }

    return true;
  }

  // ----------------------------------------------------------------------------
  // | Private Functions
  // ----------------------------------------------------------------------------   
  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private class CameraAutoFocusMoveCallback implements Camera.AutoFocusMoveCallback {
    private AutoFocusMoveCallback _Delegate;

    @Override
    public void onAutoFocusMoving(boolean start, Camera camera) {
      if (_Delegate != null) {
        _Delegate.onAutoFocusMoving(start);
      }
    }
  }

  @SuppressLint("InlinedApi")
  private Camera createCamera() {
    int requestedCameraId = getIdForRequestedCamera(_Facing);
    if (requestedCameraId == -1) {
      throw new RuntimeException("Could not find requested camera.");
    }
    Camera camera = Camera.open(requestedCameraId);

    SizePair sizePair = selectSizePair(camera, _RequestedPreviewWidth, _RequestedPreviewHeight);
    if (sizePair == null) {
      throw new RuntimeException("Could not find suitable preview size.");
    }
    Size pictureSize = sizePair.pictureSize();
    _PreviewSize = sizePair.previewSize();

    int[] previewFpsRange = selectPreviewFpsRange(camera, _RequestedFps);
    if (previewFpsRange == null) {
      throw new RuntimeException("Could not find suitable preview frames per second range.");
    }

    Camera.Parameters parameters = camera.getParameters();

    if (pictureSize != null) {
      parameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
    }

    parameters.setPreviewSize(_PreviewSize.getWidth(), _PreviewSize.getHeight());
    parameters.setPreviewFpsRange(previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
        previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
    parameters.setPreviewFormat(ImageFormat.NV21);

    setRotation(camera, parameters, requestedCameraId);

    if (_FocusMode != null) {
      if (parameters.getSupportedFocusModes().contains(_FocusMode)) {
        parameters.setFocusMode(_FocusMode);
      } else {
        Log.i(TAG, "Camera focus mode: " + _FocusMode + " is not supported on this device.");
      }
    }

    // setting _FocusMode to the one set in the params
    _FocusMode = parameters.getFocusMode();

    if (_FlashMode != null) {
      if (parameters.getSupportedFlashModes() != null) {
        if (parameters.getSupportedFlashModes().contains(_FlashMode)) {
          parameters.setFlashMode(_FlashMode);
        } else {
          Log.i(TAG, "Camera flash mode: " + _FlashMode + " is not supported on this device.");
        }
      }
    }

    // setting _FlashMode to the one set in the params
    _FlashMode = parameters.getFlashMode();

    camera.setParameters(parameters);

    camera.setPreviewCallbackWithBuffer(new CameraPreviewCallback());

    // Four frame buffers are needed for working with the camera:
    //
    // one for the frame that is currently being executed upon in doing detection
    // one for the next pending frame to process immediately upon completing
    // detection
    // two for the frames that the camera uses to populate future preview images
    camera.addCallbackBuffer(createPreviewBuffer(_PreviewSize));
    camera.addCallbackBuffer(createPreviewBuffer(_PreviewSize));
    camera.addCallbackBuffer(createPreviewBuffer(_PreviewSize));
    camera.addCallbackBuffer(createPreviewBuffer(_PreviewSize));

    return camera;
  }

  private static int getIdForRequestedCamera(int p_Facing) {
    CameraInfo cameraInfo = new CameraInfo();
    for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
      Camera.getCameraInfo(i, cameraInfo);
      if (cameraInfo.facing == p_Facing) {
        return i;
      }
    }
    return -1;
  }

  private static SizePair selectSizePair(Camera p_Camera, int p_DesiredWidth, int p_DesiredHeight) {
    List<SizePair> validPreviewSizes = generateValidPreviewSizeList(p_Camera);

    SizePair selectedPair = null;
    int minDiff = Integer.MAX_VALUE;
    for (SizePair sizePair : validPreviewSizes) {
      Size size = sizePair.previewSize();
      int diff = Math.abs(size.getWidth() - p_DesiredWidth) + Math.abs(size.getHeight() - p_DesiredHeight);
      if (diff < minDiff) {
        selectedPair = sizePair;
        minDiff = diff;
      }
    }

    return selectedPair;
  }

  private static List<SizePair> generateValidPreviewSizeList(Camera p_Camera) {
    Camera.Parameters parameters = p_Camera.getParameters();
    List<android.hardware.Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
    List<android.hardware.Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
    List<SizePair> validPreviewSizes = new ArrayList<>();
    for (android.hardware.Camera.Size previewSize : supportedPreviewSizes) {
      float previewAspectRatio = (float) previewSize.width / (float) previewSize.height;
      for (android.hardware.Camera.Size pictureSize : supportedPictureSizes) {
        float pictureAspectRatio = (float) pictureSize.width / (float) pictureSize.height;
        if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
          validPreviewSizes.add(new SizePair(previewSize, pictureSize));
          break;
        }
      }
    }
    
    if (validPreviewSizes.size() == 0) {
      Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size");
      for (android.hardware.Camera.Size previewSize : supportedPreviewSizes) {
        // The null picture size will let us know that we shouldn't set a picture size.
        validPreviewSizes.add(new SizePair(previewSize, null));
      }
    }

    return validPreviewSizes;
  }

  private int[] selectPreviewFpsRange(Camera p_Camera, float p_DesiredPreviewFps) {
    int desiredPreviewFpsScaled = (int) (p_DesiredPreviewFps * 1000.0f);

    int[] selectedFpsRange = null;
    int minDiff = Integer.MAX_VALUE;
    List<int[]> previewFpsRangeList = p_Camera.getParameters().getSupportedPreviewFpsRange();
    for (int[] range : previewFpsRangeList) {
      int deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
      int deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
      int diff = Math.abs(deltaMin) + Math.abs(deltaMax);
      if (diff < minDiff) {
        selectedFpsRange = range;
        minDiff = diff;
      }
    }
    return selectedFpsRange;
  }

  private void setRotation(Camera p_Camera, Camera.Parameters p_Parameters, int p_CameraId) {
    WindowManager windowManager = (WindowManager) _Context.getSystemService(Context.WINDOW_SERVICE);
    int degrees = 0;
    int rotation = windowManager.getDefaultDisplay().getRotation();
    switch (rotation) {
    case Surface.ROTATION_0:
      degrees = 0;
      break;
    case Surface.ROTATION_90:
      degrees = 90;
      break;
    case Surface.ROTATION_180:
      degrees = 180;
      break;
    case Surface.ROTATION_270:
      degrees = 270;
      break;
    default:
      Log.e(TAG, "Bad rotation value: " + rotation);
    }

    CameraInfo cameraInfo = new CameraInfo();
    Camera.getCameraInfo(p_CameraId, cameraInfo);

    int angle;
    int displayAngle;
    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      angle = (cameraInfo.orientation + degrees) % 360;
      displayAngle = (360 - angle) % 360; // compensate for it being mirrored
    } else { // back-facing
      angle = (cameraInfo.orientation - degrees + 360) % 360;
      displayAngle = angle;
    }

    // This corresponds to the rotation constants in {@link Frame}.
    _Rotation = angle / 90;

    p_Camera.setDisplayOrientation(displayAngle);
    p_Parameters.setRotation(angle);
  }

  private byte[] createPreviewBuffer(Size p_PreviewSize) {
    int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
    long sizeInBits = p_PreviewSize.getHeight() * p_PreviewSize.getWidth() * bitsPerPixel;
    int bufferSize = (int) Math.ceil(sizeInBits / 8.0d) + 1;

    byte[] byteArray = new byte[bufferSize];
    ByteBuffer buffer = ByteBuffer.wrap(byteArray);
    if (!buffer.hasArray() || (buffer.array() != byteArray)) {
      throw new IllegalStateException("Failed to create valid buffer for camera source.");
    }

    _BytesToByteBuffer.put(byteArray, buffer);
    return byteArray;
  }

  // ----------------------------------------------------------------------------
  // | Helper Classes
  // ----------------------------------------------------------------------------
  private class PictureStartCallback implements Camera.ShutterCallback {
    private ShutterCallback _Delegate;

    @Override
    public void onShutter() {
      if (_Delegate != null) {
        _Delegate.onShutter();
      }
    }
  }

  private class PictureDoneCallback implements Camera.PictureCallback {
    private PictureCallback _Delegate;

    @Override
    public void onPictureTaken(byte[] p_Data, Camera p_Camera) {
      if (_Delegate != null) {
        _Delegate.onPictureTaken(p_Data);
      }
      synchronized (_CameraLock) {
        if (_Camera != null) {
          _Camera.startPreview();
        }
      }
    }
  }

  private class CameraAutoFocusCallback implements Camera.AutoFocusCallback {
    private AutoFocusCallback _Delegate;

    @Override
    public void onAutoFocus(boolean p_Success, Camera p_Camera) {
      if (_Delegate != null) {
        _Delegate.onAutoFocus(p_Success);
      }
    }
  }

  private static class SizePair {
    private Size _Preview;
    private Size _Picture;

    public SizePair(android.hardware.Camera.Size p_PreviewSize, android.hardware.Camera.Size p_PictureSize) {
      _Preview = new Size(p_PreviewSize.width, p_PreviewSize.height);
      if (p_PictureSize != null) {
        _Picture = new Size(p_PictureSize.width, p_PictureSize.height);
      }
    }

    public Size previewSize() {
      return _Preview;
    }

    @SuppressWarnings("unused")
    public Size pictureSize() {
      return _Picture;
    }
  }  

  private class CameraPreviewCallback implements Camera.PreviewCallback {
    @Override
    public void onPreviewFrame(byte[] p_Data, Camera p_Camera) {
      _FrameProcessor.setNextFrame(p_Data, p_Camera);
    }
  }

  /**
   * This runnable controls access to the underlying receiver, calling it to
   * process frames when available from the camera. This is designed to run
   * detection on frames as fast as possible (i.e., without unnecessary context
   * switching or waiting on the next frame).
   * <p/>
   * While detection is running on a frame, new frames may be received from the
   * camera. As these frames come in, the most recent frame is held onto as
   * pending. As soon as detection and its associated processing are done for the
   * previous frame, detection on the mostly recently received frame will
   * immediately start on the same thread.
   */
  private class FrameProcessingRunnable implements Runnable {
    // This lock guards all of the member variables below.
    private final Object _Lock = new Object();
    private boolean _Active = true;

    // These pending variables hold the state associated with the new frame awaiting
    // processing.
    private ByteBuffer _PendingFrameData;

    FrameProcessingRunnable() {}

    /**
     * Releases the underlying receiver. This is only safe to do after the
     * associated thread has completed, which is managed in camera source's release
     * method above.
     */
    @SuppressLint("Assert")
    void release() {
    }

    /**
     * Marks the runnable as active/not active. Signals any blocked threads to
     * continue.
     */
    void setActive(boolean p_Active) {
      synchronized (_Lock) {
        _Active = p_Active;
        _Lock.notifyAll();
      }
    }

    /**
     * Sets the frame data received from the camera. This adds the previous unused
     * frame buffer (if present) back to the camera, and keeps a pending reference
     * to the frame data for future use.
     */
    void setNextFrame(byte[] p_Data, Camera p_Camera) {
      synchronized (_Lock) {
        if (_PendingFrameData != null) {
          p_Camera.addCallbackBuffer(_PendingFrameData.array());
          _PendingFrameData = null;
        }

        if (!_BytesToByteBuffer.containsKey(p_Data)) {
          Log.d(TAG, "Skipping frame. Could not find ByteBuffer associated with the image data from the camera.");
          return;
        }

        _PendingFrameData = _BytesToByteBuffer.get(p_Data);

        // Notify the processor thread if it is waiting on the next frame (see below).
        _Lock.notifyAll();
      }
    }

    /**
     * As long as the processing thread is active, this executes detection on frames
     * continuously. The next pending frame is either immediately available or
     * hasn't been received yet. Once it is available, we transfer the frame info to
     * local variables and run detection on that frame. It immediately loops back
     * for the next frame without pausing.
     * <p/>
     * If detection takes longer than the time in between new frames from the
     * camera, this will mean that this loop will run without ever waiting on a
     * frame, avoiding any context switching or frame acquisition time latency.
     * <p/>
     * If you find that this is using more CPU than you'd like, you should probably
     * decrease the FPS setting above to allow for some idle time in between frames.
     */
    @Override
    public void run() {
      ByteBuffer data;

      while (true) {
        synchronized (_Lock) {
          while (_Active && (_PendingFrameData == null)) {
            try {
              // Wait for the next frame to be received from the camera, since we
              // don't have it yet.
              _Lock.wait();
            } catch (InterruptedException e) {
              Log.d(TAG, "Frame processing loop terminated.", e);
              return;
            }
          }

          if (!_Active) {
            // Exit the loop once this camera source is stopped or released. We check
            // this here, immediately after the wait() above, to handle the case where
            // setActive(false) had been called, triggering the termination of this
            // loop.
            return;
          }

          // Hold onto the frame data locally, so that we can use this for detection
          // below. We need to clear _PendingFrameData to ensure that this buffer isn't
          // recycled back to the camera before we are done using that data.
          data = _PendingFrameData;
          _PendingFrameData = null;
        }

        // The code below needs to run outside of synchronization, because this will
        // allow
        // the camera to add pending frame(s) while we are running detection on the
        // current
        // frame.

        try {
          synchronized (_CameraLock) {
            Log.d(TAG, "Process an image");
            _ScanningProcessor.Process(data, _PreviewSize.getWidth(), _PreviewSize.getHeight(), _Rotation);
          }
        } catch (Throwable t) {
          Log.e(TAG, "Exception thrown from receiver.", t);
        } finally {
          _Camera.addCallbackBuffer(data.array());
        }
      }
    }
  }
}
