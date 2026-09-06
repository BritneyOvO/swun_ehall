import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:markdown/markdown.dart' as md;
import 'package:url_launcher/url_launcher.dart';

import '../api/update.dart';
import '../theme.dart';

Future<void> showUpdateDialog(BuildContext context, AppRelease rel) {
  final notes = rel.notes.replaceAll('\r\n', '\n').trim();
  return showDialog<void>(
    context: context,
    builder: (ctx) {
      final theme = Theme.of(ctx);
      final mdStyle = MarkdownStyleSheet.fromTheme(theme).copyWith(
        p: TextStyle(fontSize: 13, color: ctx.muted, height: 1.45),
        listBullet: TextStyle(fontSize: 13, color: ctx.muted),
        a: TextStyle(fontSize: 13, color: ctx.primary, height: 1.45),
        em: TextStyle(fontSize: 13, color: ctx.muted, fontStyle: FontStyle.italic),
        strong: TextStyle(fontSize: 13, color: ctx.ink, fontWeight: FontWeight.w600),
        code: TextStyle(
          fontSize: 12,
          fontFamily: 'monospace',
          color: ctx.ink,
          backgroundColor: ctx.line.withValues(alpha: 0.35),
        ),
        h1: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: ctx.ink, height: 1.35),
        h2: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: ctx.ink, height: 1.35),
        h3: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: ctx.ink, height: 1.35),
        blockSpacing: 8,
        listIndent: 20,
        horizontalRuleDecoration: BoxDecoration(
          border: Border(top: BorderSide(color: ctx.line)),
        ),
      );
      return AlertDialog(
        title: const Text('发现新版本'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480, maxHeight: 360),
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
