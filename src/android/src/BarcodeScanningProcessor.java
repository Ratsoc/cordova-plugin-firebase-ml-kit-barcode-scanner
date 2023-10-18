package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.content.Context;
import android.graphics.ImageFormat;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.UiThread;

// ----------------------------------------------------------------------------
// |  Google Imports
// ----------------------------------------------------------------------------
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.common.Barcode;
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
  public BarcodeScanningProcessor(BarcodeScanner p_BarcodeDetector, Context p_Context) {
    _Detector = p_BarcodeDetector;
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

  // To keep the images and metadata in process.
  @GuardedBy("this")
  private ByteBuffer _ProcessingImage;


  // ----------------------------------------------------------------------------
  // |  Public Functions
  // ----------------------------------------------------------------------------
  public synchronized void Process(ByteBuffer p_Data, int width, int height, int rotation) {
    _LatestImage = p_Data;
    if (_ProcessingImage == null) {
      ProcessLatestImage(width, height, rotation);
    }
  }

  public void Stop() {
      _Detector.close();
  }


  // ----------------------------------------------------------------------------
  // |  Protected Functions
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // |  Private Functions
  // ----------------------------------------------------------------------------
  private synchronized void ProcessLatestImage(int width, int height, int rotation) {
    _ProcessingImage = _LatestImage;
    _LatestImage = null;
    if (_ProcessingImage != null) {
      InputImage inputImage = InputImage.fromByteBuffer(this._ProcessingImage, width, height, 0, ImageFormat.NV21);
      ProcessImage(inputImage);
    }
  }

  private void ProcessImage(final InputImage image) {
    DetectInVisionImage(image);
  }

  private void DetectInVisionImage(InputImage p_Image) {
    _Detector.process(p_Image).addOnSuccessListener(
            new OnSuccessListener<List<Barcode>>() {
              @Override
              public void onSuccess(List<Barcode> results) {
                OnSuccess(results);
                ProcessLatestImage(p_Image.getWidth(), p_Image.getHeight(), p_Image.getRotationDegrees());
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
