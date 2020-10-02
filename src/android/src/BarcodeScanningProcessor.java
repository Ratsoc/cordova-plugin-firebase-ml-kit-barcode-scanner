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
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

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
  public BarcodeScanningProcessor(FirebaseVisionBarcodeDetector p_BarcodeDetector, Context p_Context) {
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
  private final FirebaseVisionBarcodeDetector _Detector;
  private BarcodeUpdateListener _BarcodeUpdateListener;

  // To keep the latest images and its metadata.
  @GuardedBy("this")
  private ByteBuffer _LatestImage;

  @GuardedBy("this")
  private FirebaseVisionImageMetadata _LatestImageMetaData;

  // To keep the images and metadata in process.
  @GuardedBy("this")
  private ByteBuffer _ProcessingImage;

  @GuardedBy("this")
  private FirebaseVisionImageMetadata _ProcessingMetaData;

  // ----------------------------------------------------------------------------
  // |  Public Functions
  // ----------------------------------------------------------------------------
  public synchronized void Process(ByteBuffer p_Data, FirebaseVisionImageMetadata p_FrameMetadata) {
    _LatestImage = p_Data;
    _LatestImageMetaData = p_FrameMetadata;
    if (_ProcessingImage == null && _ProcessingMetaData == null) {
      ProcessLatestImage();
    }
  }

  public void Stop() {
    try {
      _Detector.close();
    } catch(IOException e) {
      Log.e(TAG, "Error on FirebaseVisionBarcodeDetector close.", e);
    }
  }


  // ----------------------------------------------------------------------------
  // |  Protected Functions
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // |  Private Functions
  // ----------------------------------------------------------------------------
  private synchronized void ProcessLatestImage() {
    _ProcessingImage = _LatestImage;
    _ProcessingMetaData = _LatestImageMetaData;
    _LatestImage = null;
    _LatestImageMetaData = null;
    if (_ProcessingImage != null && _ProcessingMetaData != null) {
        ProcessImage(_ProcessingImage, _ProcessingMetaData);
    }
  }

  private void ProcessImage(ByteBuffer p_Data, final FirebaseVisionImageMetadata p_FrameMetadata) {
    FirebaseVisionImage image = FirebaseVisionImage.fromByteBuffer(p_Data, p_FrameMetadata);
    DetectInVisionImage(image);
  }

  private void DetectInVisionImage(FirebaseVisionImage p_Image) {
    _Detector.detectInImage(p_Image).addOnSuccessListener(
            new OnSuccessListener<List<FirebaseVisionBarcode>>() {
              @Override
              public void onSuccess(List<FirebaseVisionBarcode> results) {
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

  private void OnSuccess(List<FirebaseVisionBarcode> p_Barcodes) {
    for(FirebaseVisionBarcode barcode: p_Barcodes) {
      _BarcodeUpdateListener.onBarcodeDetected(barcode.getDisplayValue());
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
