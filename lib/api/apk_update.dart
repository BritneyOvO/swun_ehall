import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'httpx.dart';
import 'update.dart';

const _installCh = MethodChannel('cn.edu.swun.swun_ehall/install');
const _minApkBytes = 1024 * 1024;

class ApkInstallException implements Exception {
  ApkInstallException(this.message, {this.needPermission = false});

  final String message;
  final bool needPermission;

  @override
  String toString() => message;
}

Future<Directory> updateApkDir() async {
  final root = await getApplicationSupportDirectory();
  final dir = Directory(p.join(root.path, 'update'));
  if (!await dir.exists()) await dir.create(recursive: true);
  return dir;
}

File updateApkFile(Directory dir, String version) =>
    File(p.join(dir.path, 'swun_ehall-$version.apk'));

Future<File?> cachedReleaseApk(AppRelease rel) async {
  final dir = await updateApkDir();
  final file = updateApkFile(dir, rel.version);
  if (!await file.exists()) return null;
  if (await file.length() < _minApkBytes) return null;
  return file;
}

/// 启动后删掉已经装上（或更旧）的安装包，以及没下完的 .part。
Future<void> purgeStaleUpdateApks() async {
  try {
    final dir = await updateApkDir();
    await for (final e in dir.list()) {
      if (e is! File) continue;
      final name = p.basename(e.path);
      if (name.endsWith('.part')) {
        try {
          await e.delete();
        } catch (_) {}
        continue;
      }
      final m = RegExp(r'swun_ehall-(\d+(?:\.\d+)*)').firstMatch(name);
      if (m == null) continue;
      if (compareVersions(m.group(1)!, kAppVersion) <= 0) {
        try {
          await e.delete();
        } catch (_) {}
      }
    }
  } catch (_) {}
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
  final dir = await updateApkDir();
  final file = updateApkFile(dir, rel.version);
  final cached = await cachedReleaseApk(rel);
  if (cached != null) {
    onProgress?.call(1);
    return cached;
  }
  if (await file.exists()) {
    try {
      await file.delete();
    } catch (_) {}
  }
  final part = File('${file.path}.part');
  if (await part.exists()) {
    try {
      await part.delete();
    } catch (_) {}
  }
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
      part.path,
      cancelToken: cancel,
      onReceiveProgress: (got, total) {
        if (total > 0) onProgress?.call((got / total).clamp(0, 1));
      },
    );
    await part.rename(file.path);
  } on DioException catch (e) {
    if (CancelToken.isCancel(e)) rethrow;
    throw ApkInstallException('下载失败：${publicError(e)}');
  } finally {
    dio.close(force: true);
  }
  if (!await file.exists() || await file.length() < _minApkBytes) {
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
