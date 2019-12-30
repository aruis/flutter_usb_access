import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/services.dart';

class FlutterUsbAccess {
  static const MethodChannel _channel =
      const MethodChannel('com.aruistar.flutter_usb_access');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<bool> check() async {
    return await _channel.invokeMethod('check');
  }

  static Future<Uint8List> read() async {
    return await _channel.invokeMethod('read');
  }

  static Future<int> write(Uint8List param) async {
    return await _channel.invokeMethod('write', [param]);
  }

  static Future<Uint8List> command(Uint8List param) async {
    return await _channel.invokeMethod('command', [param]);
  }
}
