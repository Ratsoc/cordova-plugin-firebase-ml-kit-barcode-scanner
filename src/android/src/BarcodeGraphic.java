package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.mlkit.vision.barcode.Barcode;

import tl.cordova.plugin.firebase.mlkit.barcode.scanner.camera.GraphicOverlay;

public class BarcodeGraphic extends GraphicOverlay.Graphic {
  private static final int COLOR_CHOICES[] = {
    Color.BLUE, Color.CYAN, Color.GREEN
  };
  private static int CURRENT_COLOR_INDEX = 0;

  private int _Id;
  private Paint _RectPaint;
  private Paint _TextPaint;
  private volatile Barcode _Barcode;

  public BarcodeGraphic(GraphicOverlay overlay) {
    super(overlay);
    CURRENT_COLOR_INDEX = (CURRENT_COLOR_INDEX + 1) % COLOR_CHOICES.length;
    int selectedColor = COLOR_CHOICES[CURRENT_COLOR_INDEX];

    _RectPaint = new Paint();
    _RectPaint.setColor(selectedColor);
    _RectPaint.setStyle(Paint.Style.STROKE);
    _RectPaint.setStrokeWidth(4.0f);

    _TextPaint = new Paint();
    _TextPaint.setColor(selectedColor);
    _TextPaint.setTextSize(36.0f);
  }

  public int getId() {
    return _Id;
  }

  public void setId(int id) {
    _Id = id;
  }

  public Barcode getBarcode() {
    return _Barcode;
  }

  public void updateItem(Barcode barcode) {
    _Barcode = barcode;
    postInvalidate();
  }

  @Override
  public void draw(Canvas canvas) {
    Barcode barcode = _Barcode;
    if (barcode == null) {
      return;
    }

    RectF rect = new RectF(barcode.getBoundingBox());
    rect.left = translateX(rect.left);
    rect.top = translateY(rect.top);
    rect.right = translateX(rect.right);
    rect.bottom = translateY(rect.bottom);

    canvas.drawRect(rect, _RectPaint);
    canvas.drawText(barcode.getRawValue(), rect.left, rect.bottom, _TextPaint);
  }
}