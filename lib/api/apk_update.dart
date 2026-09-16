import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

import 'httpx.dart';
import 'update.dart';

const _installCh = MethodChannel('cn.edu.swun.swun_ehall/install');

class ApkInstallException implements Exception {
  ApkInstallException(this.message, {this.needPermission = false});

  final String message;
  final bool needPermission;

  @override
  String toString() => message;
}

Future<File> downloadReleaseApk(
  AppRelease rel, {
  void Function(double progress)? onProgress,
  CancelToken? cancel,
}) async {
  final url = rel.apkUrl?.trim() ?? '';
  if (url.isEmpty) {
    throw ApkInstallException('这个版本没有 Android 安装包');
  }
  final dir = await getTemporaryDirectory();
  final file = File('${dir.path}/update/swun_ehall-${rel.version}.apk');
  if (await file.exists()) {
    await file.delete();
  }
  await file.parent.create(recursive: true);
  final dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 12),
      receiveTimeout: const Duration(minutes: 5),
      followRedirects: true,
      maxRedirects: 8,
      headers: {
        'User-Agent': 'swun-ehall',
        'Accept': '*/*',
      },
      validateStatus: (s) => s != null && s >= 200 && s < 400,
    ),
  );
  attachHttpClient(dio);
  try {
    await dio.download(
      url,
      file.path,
      cancelToken: cancel,
      onReceiveProgress: (got, total) {
        if (total > 0) onProgress?.call((got / total).clamp(0, 1));
      },
    );
  } on DioException catch (e) {
    if (CancelToken.isCancel(e)) rethrow;
    throw ApkInstallException('下载失败：${publicError(e)}');
  } finally {
    dio.close(force: true);
  }
  if (!await file.exists() || await file.length() < 1024) {
    throw ApkInstallException('安装包不完整，请稍后重试');
  }
  onProgress?.call(1);
  return file;
}

Future<void> installApkFile(File file) async {
  try {
    await _installCh.invokeMethod('install', file.path);
  } on PlatformException catch (e) {
    throw ApkInstallException(
      e.message?.trim().isNotEmpty == true ? e.message! : '无法打开安装界面',
      needPermission: e.code == 'PERMISSION',
    );
  }
}
