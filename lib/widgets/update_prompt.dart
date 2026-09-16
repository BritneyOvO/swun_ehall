import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:markdown/markdown.dart' as md;
import 'package:url_launcher/url_launcher.dart';

import '../api/apk_update.dart';
import '../api/update.dart';
import '../theme.dart';
import 'toast.dart';

Future<void> showUpdateDialog(BuildContext context, AppRelease rel) {
  return showDialog<void>(
    context: context,
    builder: (ctx) => _UpdateDialog(rel: rel),
  );
}

class _UpdateDialog extends StatefulWidget {
  const _UpdateDialog({required this.rel});

  final AppRelease rel;

  @override
  State<_UpdateDialog> createState() => _UpdateDialogState();
}

class _UpdateDialogState extends State<_UpdateDialog> {
  var _busy = false;
  double? _progress;
  String? _status;
  CancelToken? _cancel;

  @override
  void dispose() {
    _cancel?.cancel();
    super.dispose();
  }

  Future<void> _install() async {
    if (_busy) return;
    final token = CancelToken();
    setState(() {
      _busy = true;
      _progress = 0;
      _status = '正在下载…';
      _cancel = token;
    });
    try {
      final file = await downloadReleaseApk(
        widget.rel,
        cancel: token,
        onProgress: (p) {
          if (!mounted) return;
          setState(() => _progress = p);
        },
      );
      if (!mounted) return;
      setState(() {
        _progress = 1;
        _status = '正在打开安装…';
      });
      await installApkFile(file);
      if (mounted) Navigator.of(context).pop();
    } on DioException catch (e) {
      if (CancelToken.isCancel(e) || !mounted) return;
      setState(() {
        _busy = false;
        _progress = null;
        _status = null;
      });
      showToast(context, '下载失败');
    } on ApkInstallException catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _progress = null;
        _status = null;
      });
      showToast(context, e.message);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _progress = null;
        _status = null;
      });
      showToast(context, e.toString().replaceFirst('Exception: ', ''));
    }
  }

  @override
  Widget build(BuildContext context) {
    final rel = widget.rel;
    final notes = rel.notes.replaceAll('\r\n', '\n').trim();
    final theme = Theme.of(context);
    final mdStyle = MarkdownStyleSheet.fromTheme(theme).copyWith(
      p: TextStyle(fontSize: 13, color: context.muted, height: 1.45),
      listBullet: TextStyle(fontSize: 13, color: context.muted),
      a: TextStyle(fontSize: 13, color: context.primary, height: 1.45),
      em: TextStyle(fontSize: 13, color: context.muted, fontStyle: FontStyle.italic),
      strong: TextStyle(fontSize: 13, color: context.ink, fontWeight: FontWeight.w600),
      code: TextStyle(
        fontSize: 12,
        fontFamily: 'monospace',
        color: context.ink,
        backgroundColor: context.line.withValues(alpha: 0.35),
      ),
      h1: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: context.ink, height: 1.35),
      h2: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: context.ink, height: 1.35),
      h3: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: context.ink, height: 1.35),
      blockSpacing: 8,
      listIndent: 20,
      horizontalRuleDecoration: BoxDecoration(
        border: Border(top: BorderSide(color: context.line)),
      ),
    );
    return AlertDialog(
      title: const Text('发现新版本'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480, maxHeight: 420),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('当前 $kAppVersion，最新 ${rel.version}'),
              if (notes.isNotEmpty) ...[
                const SizedBox(height: 10),
                MarkdownBody(
                  data: notes,
                  selectable: true,
                  extensionSet: md.ExtensionSet.gitHubWeb,
                  styleSheet: mdStyle,
                  onTapLink: (text, href, title) {
                    final url = href?.trim() ?? '';
                    if (url.isEmpty) return;
                    final uri = Uri.tryParse(url);
                    if (uri == null) return;
                    unawaited(
                      launchUrl(uri, mode: LaunchMode.externalApplication),
                    );
                  },
                ),
              ],
              if (_busy) ...[
                const SizedBox(height: 14),
                LinearProgressIndicator(value: _progress),
                const SizedBox(height: 6),
                Text(
                  _status ?? '正在下载…',
                  style: TextStyle(color: context.muted, fontSize: 12),
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _busy
              ? () {
                  _cancel?.cancel();
                  Navigator.of(context).pop();
                }
              : () => Navigator.of(context).pop(),
          child: Text(_busy ? '取消' : '稍后'),
        ),
        FilledButton(
          onPressed: _busy ? null : _install,
          child: const Text('下载并安装'),
        ),
      ],
    );
  }
}

Future<void> checkForUpdate(
  BuildContext context, {
  required bool silent,
}) async {
  try {
    final rel = await loadLatestRelease();
    if (!context.mounted) return;
    if (rel == null || !rel.isNewer) {
      if (!silent) {
        showToast(context, '已是最新版本 $kAppVersion');
      }
      return;
    }
    await showUpdateDialog(context, rel);
  } catch (_) {
    if (!silent && context.mounted) {
      showToast(context, '检查更新失败');
    }
  }
}
