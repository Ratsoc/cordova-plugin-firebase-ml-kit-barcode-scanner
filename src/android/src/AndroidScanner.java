package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

// ----------------------------------------------------------------------------
// |  Cordova Imports
// ----------------------------------------------------------------------------
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

// ----------------------------------------------------------------------------
// |  JSON Imports
// ----------------------------------------------------------------------------
import org.json.JSONArray;
import org.json.JSONException;

// ----------------------------------------------------------------------------
// |  Our Imports
// ----------------------------------------------------------------------------

public class AndroidScanner extends CordovaPlugin {
  // ----------------------------------------------------------------------------
  // | Protected Properties
  // ----------------------------------------------------------------------------
  protected CallbackContext CallbackContext;

  // ----------------------------------------------------------------------------
  // | Private Properties
  // ----------------------------------------------------------------------------
  private static final int RC_BARCODE_CAPTURE = 9001;

  // ----------------------------------------------------------------------------
  // | Public Functions
  // ----------------------------------------------------------------------------
  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
  }

  @Override
  public boolean execute(String p_Action, JSONArray p_Args, CallbackContext p_CallbackContext)
      throws JSONException {
    Context context = cordova.getActivity().getApplicationContext();
    CallbackContext = p_CallbackContext;

    if ("startScan".equals(p_Action)) {
      new Thread(new OneShotTask(context, p_Args)).start();
      return true;
    }
    return false;
  }

  @Override
  public void onActivityResult(int p_RequestCode, int p_ResultCode, Intent p_Data) {
    super.onActivityResult(p_RequestCode, p_ResultCode, p_Data);

    if (p_RequestCode == RC_BARCODE_CAPTURE) {
      if (p_ResultCode == Activity.RESULT_OK) {
        if (p_Data != null) {
          String barcode = p_Data.getStringExtra(BarcodeCaptureActivity.BarcodeValue);
          JSONArray result = new JSONArray();
          result.put(barcode);
          result.put("");
          result.put("");
          CallbackContext.sendPluginResult(
              new PluginResult(PluginResult.Status.OK, result));
          Log.d("AndroidScanner", "Barcode read: " + barcode);
        }
      } else {
        String err = (p_Data != null)
            ? p_Data.getStringExtra("err")
            : "UNKNOWN_ERROR";
        JSONArray result = new JSONArray();
        result.put(err);
        result.put("");
        result.put("");
        CallbackContext.sendPluginResult(
            new PluginResult(PluginResult.Status.ERROR, result));
      }
    }
  }

  @Override
  public void onRestoreStateForActivityResult(
      Bundle state, CallbackContext callbackContext) {
    CallbackContext = callbackContext;
  }

  // ----------------------------------------------------------------------------
  // | Private Functions
  // ----------------------------------------------------------------------------
  private void openNewActivity(Context context, JSONArray args) {
    Intent intent = new Intent(context, SecondaryActivity.class);
    intent.putExtra("DetectionTypes", args.optInt(0, 1234));
    intent.putExtra("ViewFinderWidth", args.optDouble(1, .5));
    intent.putExtra("ViewFinderHeight", args.optDouble(1, .7));

    this.cordova.setActivityResultCallback(this);
    this.cordova.startActivityForResult(this, intent, RC_BARCODE_CAPTURE);
  }

  // ----------------------------------------------------------------------------
  // | Helper classes
  // ----------------------------------------------------------------------------
  private class OneShotTask implements Runnable {
    private final Context _Context;
    private final JSONArray _Args;

    OneShotTask(Context p_Context, JSONArray p_TaskArgs) {
      _Context = p_Context;
      _Args = p_TaskArgs;
    }

    @Override
    public void run() {
      openNewActivity(_Context, _Args);
    }
  }
}