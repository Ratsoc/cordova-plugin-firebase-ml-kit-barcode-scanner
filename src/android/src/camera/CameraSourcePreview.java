package tl.cordova.plugin.firebase.mlkit.barcode.scanner.camera;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Button;
import androidx.annotation.RequiresPermission;

// ----------------------------------------------------------------------------
// |  Google Imports
// ----------------------------------------------------------------------------
import com.google.android.gms.common.images.Size;

// ----------------------------------------------------------------------------
// |  Java Imports
// ----------------------------------------------------------------------------
import java.io.IOException;

public class CameraSourcePreview extends ViewGroup {
  // ----------------------------------------------------------------------------
  // | Public Properties
  // ----------------------------------------------------------------------------
  public double ViewFinderWidth  = .5;
  public double ViewFinderHeight = .7;

  // ----------------------------------------------------------------------------
  // | Private Properties
  // ----------------------------------------------------------------------------
  private static final String TAG = "CameraSourcePreview";

  private Context        _Context                 ;
  private SurfaceView    _SurfaceView             ;
  private View           _ViewFinderView          ;
  private View           _VerticalLine            ;
  private View           _HorizontalLine          ;
  private Button         _TorchButton             ;
  private boolean        _StartRequested          ;
  private boolean        _SurfaceAvailable        ;
  private CameraSource2  _CameraSource            ;
  private boolean        _FlashState       = false;
  private GraphicOverlay _Overlay                 ;

  public CameraSourcePreview(Context p_Context, AttributeSet p_AttributeSet) {
    super(p_Context, p_AttributeSet);
    _Context = p_Context;
    _StartRequested = false;
    _SurfaceAvailable = false;

    _SurfaceView = new SurfaceView(p_Context);
    _SurfaceView.getHolder().addCallback(new SurfaceCallback());
    addView(_SurfaceView);

    _HorizontalLine = new View(_Context);
    _HorizontalLine.setBackgroundResource(getResources().getIdentifier("horizontal_line", "drawable", _Context.getPackageName()));
    addView(_HorizontalLine);

    _VerticalLine = new View(_Context);
    _VerticalLine.setBackgroundResource(getResources().getIdentifier("vertical_line", "drawable", _Context.getPackageName()));
    addView(_VerticalLine);

    _TorchButton = new Button(_Context);
    _TorchButton.setBackgroundResource(getResources().getIdentifier("torch_inactive", "drawable", _Context.getPackageName()));
    _TorchButton.layout(0, 0, dpToPx(45), dpToPx(45));
    _TorchButton.setMaxWidth(50);
    _TorchButton.setRotation(90);

    _TorchButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        try {
          _CameraSource
              .setFlashMode(!_FlashState ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
          _FlashState = !_FlashState;
          _TorchButton.setBackgroundResource(getResources()
              .getIdentifier(_FlashState ? "torch_active" : "torch_inactive", "drawable", _Context.getPackageName()));
        } catch (Exception e) {

        }
      }
    });
    addView(_TorchButton);
  }

  // ----------------------------------------------------------------------------
  // |  Public Functions
  // ----------------------------------------------------------------------------
  public int dpToPx(int p_Dot) {
    float density = _Context.getResources().getDisplayMetrics().density;
    return Math.round((float) p_Dot * density);
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  public void start(CameraSource2 p_CameraSource) throws IOException, SecurityException {
    if (p_CameraSource == null) {
      stop();
    }

    _CameraSource = p_CameraSource;

    if (_CameraSource != null) {
      _StartRequested = true;
      startIfReady();
    }
  }

  @RequiresPermission(Manifest.permission.CAMERA)
  public void start(CameraSource2 p_CameraSource, GraphicOverlay overlay) throws IOException, SecurityException {
    _Overlay = overlay;
    start(p_CameraSource);
  }

  public void stop() {
    if (_CameraSource != null) {
      _CameraSource.stop();
    }
  }

  public void release() {
    if (_CameraSource != null) {
      _CameraSource.release();
      _CameraSource = null;
    }
  }

  // ----------------------------------------------------------------------------
  // |  Protected Functions
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // |  Private Functions
  // ----------------------------------------------------------------------------  
  @RequiresPermission(Manifest.permission.CAMERA)
  private void startIfReady() throws IOException, SecurityException {
    if (_StartRequested && _SurfaceAvailable) {
      _CameraSource.start(_SurfaceView.getHolder());
      if (_Overlay != null) {
        Size size = _CameraSource.getPreviewSize();
        int min = Math.min(size.getWidth(), size.getHeight());
        int max = Math.max(size.getWidth(), size.getHeight());
        if (isPortraitMode()) {
          _Overlay.setCameraInfo(min, max, _CameraSource.getCameraFacing());
        } else {
          _Overlay.setCameraInfo(max, min, _CameraSource.getCameraFacing());
        }
        _Overlay.clear();
      }
      _StartRequested = false;
    }
  }

  @Override
  protected void onLayout(boolean p_Changed, int p_Left, int p_Top, int p_Right, int p_Bottom) {
    int width = 320;
    int height = 240;
    if (_CameraSource != null) {
      Size size = _CameraSource.getPreviewSize();
      if (size != null) {
        width = size.getWidth();
        height = size.getHeight();
      }
    }

    if (isPortraitMode()) {
      int tmp = width;
      width = height;
      height = tmp;
    }

    final int layoutWidth = p_Right - p_Left;
    final int layoutHeight = p_Bottom - p_Top;

    int childHeight = layoutHeight;
    int childWidth = (int) (((float) layoutHeight / (float) height) * width);
    int leftOffset = ((int) ((float) layoutHeight / (float) height) * width - childWidth) / 2;
    int topOffset = 0;

    if (childHeight > layoutHeight) {
      childWidth = layoutWidth;
      childHeight = (int) (((float) layoutWidth / (float) width) * height);

      leftOffset = 0;
      topOffset = ((int) ((float) layoutWidth / (float) width) * height - childHeight) / 2;
    }

    _SurfaceView.layout(leftOffset, topOffset, childWidth, childHeight);

    int actualWidth = (int) (layoutWidth * ViewFinderWidth);
    int actualHeight = (int) (layoutHeight * ViewFinderHeight);

    _HorizontalLine.layout(10, 10, layoutWidth-10, layoutHeight-10);

    
    int xOffset = (layoutHeight - layoutWidth) * -1;

    _VerticalLine.layout(xOffset, 10, layoutHeight, layoutHeight-10);

    int buttonSize = dpToPx(45);
    int torchLeft = (int) layoutWidth / 2 + actualWidth / 2 + (layoutWidth - (layoutWidth / 2 + actualWidth / 2)) / 2 - buttonSize / 2;
    int torchTop = layoutHeight - (layoutWidth - torchLeft);

    _TorchButton.layout(torchLeft, torchTop, torchLeft + buttonSize, torchTop + buttonSize);

    try {
      startIfReady();
    } catch (SecurityException se) {
      Log.e(TAG, "Do not have permission to start the camera", se);
    } catch (IOException e) {
      Log.e(TAG, "Could not start camera source.", e);
    }
  }

  private boolean isPortraitMode() {
    int orientation = _Context.getResources().getConfiguration().orientation;
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      return false;
    }
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return true;
    }

    Log.d(TAG, "isPortraitMode returning false by default");
    return false;
  }

  // ----------------------------------------------------------------------------
  // |  Helper classes
  // ----------------------------------------------------------------------------  
  private class SurfaceCallback implements SurfaceHolder.Callback {
    @Override
    public void surfaceCreated(SurfaceHolder surface) {
      _SurfaceAvailable = true;
      try {
        startIfReady();
      } catch (SecurityException se) {
        Log.e(TAG, "Do not have permission to start the camera", se);
      } catch (IOException e) {
        Log.e(TAG, "Could not start camera source.", e);
      }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surface) {
      _SurfaceAvailable = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }
  }
}
