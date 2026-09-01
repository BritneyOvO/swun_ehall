import 'package:flutter/material.dart';

import '../api/httpx.dart';
import '../theme.dart';
import 'loader.dart';

class AsyncBody<T> extends StatelessWidget {
  const AsyncBody({super.key, required this.future, required this.builder});

  final Future<T> future;
  final Widget Function(BuildContext, T) builder;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<T>(
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
              child: Text(
                publicError(snap.error!),
                textAlign: TextAlign.center,
                style: const TextStyle(color: kMuted),
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
