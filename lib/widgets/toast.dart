import 'dart:async';

import 'package:flutter/material.dart';

OverlayEntry? _entry;
Timer? _hide;

/// 短提示。盖在界面上，不占布局、不挡操作。
void showToast(BuildContext context, String message) {
  final text = message.trim();
  if (text.isEmpty) return;
  final overlay = Overlay.maybeOf(context, rootOverlay: true);
  if (overlay == null) return;
  _hide?.cancel();
  _entry?.remove();
  final entry = OverlayEntry(
    builder: (_) => _ToastLayer(message: text),
  );
  _entry = entry;
  overlay.insert(entry);
  _hide = Timer(const Duration(milliseconds: 1800), () {
    entry.remove();
    if (identical(_entry, entry)) _entry = null;
  });
}

class _ToastLayer extends StatelessWidget {
  const _ToastLayer({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return IgnorePointer(
      child: SafeArea(
        child: Align(
          alignment: const Alignment(0, 0.72),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 28),
            child: Material(
              color: dark ? const Color(0xE62A2724) : const Color(0xE61C1917),
              elevation: 0,
              borderRadius: BorderRadius.circular(8),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                child: Text(
                  message,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    height: 1.35,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
