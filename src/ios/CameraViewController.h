// View controller demonstraing how to use the barcode detector with the AVFoundation
// video pipeline.
#import <Cordova/CDVScreenOrientationDelegate.h>

@protocol senddataProtocol <NSObject>

-(void)closeScanner;
-(void)sendResult:(NSString *)result;

@end

@interface CameraViewController : UIViewController<CDVScreenOrientationDelegate>

@property (nonatomic, weak) id <CDVScreenOrientationDelegate> orientationDelegate;
@property(nonatomic,assign)id delegate;
@property(nonatomic,assign) NSNumber *barcodeFormats;
@property(nonatomic,assign) CGFloat scanAreaWidth;
@property(nonatomic,assign) CGFloat scanAreaHeight;

@end

