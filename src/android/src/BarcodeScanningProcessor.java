package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.content.Context;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.UiThread;

// ----------------------------------------------------------------------------
// |  Google Imports
// ----------------------------------------------------------------------------
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

// ----------------------------------------------------------------------------
// |  Java Imports
// ----------------------------------------------------------------------------
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

// ----------------------------------------------------------------------------
// |  Our Imports
// ----------------------------------------------------------------------------

public class BarcodeScanningProcessor {
  // ----------------------------------------------------------------------------
  // | Public Properties
  // ----------------------------------------------------------------------------
  public BarcodeScanningProcessor(Context p_Context) {
    BarcodeScannerOptions options =
        new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build();
    _Detector = BarcodeScanning.getClient(options);
    if (p_Context instanceof BarcodeUpdateListener) {
      this._BarcodeUpdateListener = (BarcodeUpdateListener) p_Context;
    } else {
      throw new RuntimeException("Hosting activity must implement BarcodeUpdateListener");
    }
  }

  // ----------------------------------------------------------------------------
  // | Protected Properties
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // | Private Properties
  // ----------------------------------------------------------------------------
  private static final String TAG = "Barcode-Processor";
  private final BarcodeScanner _Detector;
  private BarcodeUpdateListener _BarcodeUpdateListener;

  // To keep the latest images and its metadata.
  @GuardedBy("this")
  private ByteBuffer _LatestImage;
  @GuardedBy("this")
  private int _LatestWidth;
  @GuardedBy("this")
  private int _LatestHeight;
  @GuardedBy("this")
  private int _LatestRotation;
  @GuardedBy("this")
  private int _LatestFormat;

  // To keep the images and metadata in process.
  @GuardedBy("this")
  private ByteBuffer _ProcessingImage;
  @GuardedBy("this")
  private int _ProcessingWidth;
  @GuardedBy("this")
  private int _ProcessingHeight;
  @GuardedBy("this")
  private int _ProcessingRotation;
  @GuardedBy("this")
  private int _ProcessingFormat;

  // ----------------------------------------------------------------------------
  // |  Public Functions
  // ----------------------------------------------------------------------------
  // New ML Kit entrypoint used by CameraSource2.
  public synchronized void process(ByteBuffer p_Data, int p_Width, int p_Height, int p_RotationDegrees, int p_ImageFormat) {
    _LatestImage   = p_Data;
    _LatestWidth   = p_Width;
    _LatestHeight  = p_Height;
    _LatestRotation= p_RotationDegrees;
    _LatestFormat  = p_ImageFormat;
    if (_ProcessingImage == null) {
      ProcessLatestImage();
    }
  }

  // Backward-compatible alias (in case other code still calls the old name).
  public void Process(ByteBuffer p_Data, int p_Width, int p_Height, int p_RotationDegrees, int p_ImageFormat) {
    process(p_Data, p_Width, p_Height, p_RotationDegrees, p_ImageFormat);
  }

  public void Stop() {
    try {
      _Detector.close();
    } catch(IOException e) {
      Log.e(TAG, "Error on BarcodeScanner close.", e);
    }
  }

  // ----------------------------------------------------------------------------
  // |  Protected Functions
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // |  Private Functions
  // ----------------------------------------------------------------------------
  private synchronized void ProcessLatestImage() {
    _ProcessingImage    = _LatestImage;
    _ProcessingWidth    = _LatestWidth;
    _ProcessingHeight   = _LatestHeight;
    _ProcessingRotation = _LatestRotation;
    _ProcessingFormat   = _LatestFormat;

    _LatestImage = null;

    if (_ProcessingImage != null) {
      ProcessImage(_ProcessingImage, _ProcessingWidth, _ProcessingHeight, _ProcessingRotation, _ProcessingFormat);
    }
  }

  private void ProcessImage(ByteBuffer p_Data, final int p_Width, final int p_Height, final int p_RotationDegrees, final int p_ImageFormat) {
    InputImage image = InputImage.fromByteBuffer(p_Data, p_Width, p_Height, p_RotationDegrees, p_ImageFormat);
    DetectInVisionImage(image);
  }

  private void DetectInVisionImage(InputImage p_Image) {
    _Detector.process(p_Image).addOnSuccessListener(
            new OnSuccessListener<List<Barcode>>() {
              @Override
              public void onSuccess(List<Barcode> results) {
                OnSuccess(results);
                ProcessLatestImage();
              }
            }).addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(Exception e) {
                OnFailure(e);
              }
            });
  }

  private void OnSuccess(List<Barcode> p_Barcodes) {
    for(Barcode barcode: p_Barcodes) {
      _BarcodeUpdateListener.onBarcodeDetected(barcode.getRawValue());
    }
  }

  private void OnFailure(Exception e) {
    Log.e(TAG, "Barcode detection failed " + e);
  }

  // ----------------------------------------------------------------------------
  // |  Helper classes
  // ----------------------------------------------------------------------------
  public interface BarcodeUpdateListener {
    @UiThread
    void onBarcodeDetected(String p_Barcode);
  }
}