import Flutter
import UIKit

public class SwiftFlutterUsbAccessPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "com.aruistar.flutter_usb_access", binaryMessenger: registrar.messenger())
    let instance = SwiftFlutterUsbAccessPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}
