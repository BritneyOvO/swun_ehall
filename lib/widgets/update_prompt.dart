import 'dart:async';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api/update.dart';
import '../theme.dart';

Future<void> showUpdateDialog(BuildContext context, AppRelease rel) {
  final notes = rel.notes.replaceAll('\r\n', '\n').trim();
  return showDialog<void>(
    context: context,
    builder: (ctx) {
      return AlertDialog(
        title: const Text('发现新版本'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 320),
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('当前 $kAppVersion，最新 ${rel.version}'),
                if (notes.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Text(
                    notes,
                    style: TextStyle(fontSize: 13, color: ctx.muted, height: 1.4),
                  ),
                ],
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('稍后'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              unawaited(
                launchUrl(
                  Uri.parse(rel.openUrl),
                  mode: LaunchMode.externalApplication,
                ),
              );
            },
            child: const Text('去下载'),
          ),
        ],
      );
    },
  );
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已是最新版本 $kAppVersion')),
        );
      }
      return;
    }
    await showUpdateDialog(context, rel);
  } catch (_) {
    if (!silent && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('检查更新失败')),
      );
    }
  }
}
