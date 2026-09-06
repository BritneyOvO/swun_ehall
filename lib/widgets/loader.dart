import 'dart:math' as math;

import 'package:flutter/material.dart';

class RefreshBusyButton extends StatelessWidget {
  const RefreshBusyButton({
    super.key,
    required this.busy,
    this.onPressed,
    this.tooltip = '刷新',
  });

  final bool busy;
  final VoidCallback? onPressed;
  final String tooltip;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: tooltip,
      onPressed: busy ? null : onPressed,
      icon: busy
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.refresh_rounded),
    );
  }
}

class SwunLoader extends StatelessWidget {
  const SwunLoader({super.key, this.compact = false});

  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '加载中',
      child: SwunBusyDots(
        color: Theme.of(context).colorScheme.primary,
        size: compact ? 7 : 9,
      ),
    );
  }
}

class SwunBusyDots extends StatefulWidget {
  const SwunBusyDots({super.key, this.color, this.size = 6});

  final Color? color;
  final double size;

  @override
  State<SwunBusyDots> createState() => _SwunBusyDotsState();
}

class _SwunBusyDotsState extends State<SwunBusyDots> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  )..repeat();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = widget.color ?? Theme.of(context).colorScheme.primary;
    final d = widget.size;
    return AnimatedBuilder(
      animation: _c,
      builder: (context, _) {
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < 3; i++) ...[
              if (i > 0) SizedBox(width: d * 0.7),
              _dot(color, d, i),
            ],
          ],
        );
      },
    );
  }

  Widget _dot(Color color, double d, int i) {
    final phase = (_c.value - i / 3) % 1;
    final lift = math.sin(phase * math.pi).clamp(0.0, 1.0);
    final scale = 0.78 + 0.22 * lift;
    return Transform.translate(
      offset: Offset(0, -d * 0.85 * lift),
      child: Transform.scale(
        scale: scale,
        child: Container(
          width: d,
          height: d,
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.40 + 0.60 * lift),
            shape: BoxShape.circle,
          ),
        ),
      ),
    );
  }
}
