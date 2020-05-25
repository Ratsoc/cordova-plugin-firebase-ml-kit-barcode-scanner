
cordova-plugin-firebase-ml-kit-barcode-scanner
===========================

Purpose of this Project
-----------------------
Enable cordova / phonegap to scan barcodes using Firebase ML Kit

Installation
------------
You will need to place your GoogleService-Info.plist and google-services.json files in the root directory of your cordova project.

````
cordova plugin add cordova-plugin-firebase-ml-kit-barcode-scanner
````

Usage
-----
To call the plugin first get default scan settings from cordova.plugins.scanner.getDefaultSettings(). Then call cordova.plugins.scanner.startScanning().
````javascript
var settings = cordova.plugins.scanner.getDefaultSettings();

cordova.plugins.scanner.startScanning(
  p_Result => {
    alert(p_Result);
  }, 
  p_Error => {
    throw p_Error
  }, 
  settings
);
````

### Output
startScanning() returns a string with the scan result.
