import 'package:flutter/material.dart';

import '../api/httpx.dart';
import '../theme.dart';
import 'loader.dart';

class AsyncBody<T> extends StatelessWidget {
  const AsyncBody({
    super.key,
    required this.future,
    required this.builder,
    this.onRetry,
  });

  final Future<T> future;
  final Widget Function(BuildContext, T) builder;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<T>(
      key: ObjectKey(future),
      future: future,
      builder: (context, snap) {
        Widget child;
        if (snap.connectionState != ConnectionState.done) {
          child = const Center(
            key: ValueKey('loading'),
            child: SwunLoader(),
          );
        } else if (snap.hasError) {
          child = Center(
            key: const ValueKey('error'),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    publicError(snap.error!),
                    textAlign: TextAlign.center,
                    style: TextStyle(color: context.muted, fontSize: 14, height: 1.4),
                  ),
                  if (onRetry != null) ...[
                    const SizedBox(height: 16),
                    FilledButton.tonal(
                      onPressed: onRetry,
                      child: const Text('重试'),
                    ),
                  ],
                ],
              ),
            ),
          );
        } else {
          child = KeyedSubtree(key: const ValueKey('body'), child: builder(context, snap.data as T));
        }
        return AnimatedSwitcher(
          duration: const Duration(milliseconds: 280),
          switchInCurve: Curves.easeOutCubic,
          child: child,
        );
      },
    );
  }
}
