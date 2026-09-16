import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../theme.dart';

/// 圆形裁剪。确认后返回 PNG 字节。
class AvatarCropPage extends StatefulWidget {
  const AvatarCropPage({super.key, required this.bytes});

  final Uint8List bytes;

  @override
  State<AvatarCropPage> createState() => _AvatarCropPageState();
}

class _AvatarCropPageState extends State<AvatarCropPage> {
  final _tx = TransformationController();
  final _previewKey = GlobalKey();
  var _busy = false;

  @override
  void dispose() {
    _tx.dispose();
    super.dispose();
  }

  Future<void> _confirm() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final box =
          _previewKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
      if (box == null) return;
      final raw = await box.toImage(pixelRatio: 512 / box.size.shortestSide);
      final rec = ui.PictureRecorder();
      final canvas = Canvas(rec);
      final side = raw.width.toDouble();
      canvas.clipPath(Path()..addOval(Rect.fromLTWH(0, 0, side, side)));
      canvas.drawImageRect(
        raw,
        Rect.fromLTWH(0, 0, raw.width.toDouble(), raw.height.toDouble()),
        Rect.fromLTWH(0, 0, side, side),
        Paint()..filterQuality = FilterQuality.high,
      );
      final img = await rec.endRecording().toImage(raw.width, raw.height);
      final data = await img.toByteData(format: ui.ImageByteFormat.png);
      if (!mounted) return;
      Navigator.of(context).pop(data?.buffer.asUint8List());
    } catch (_) {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: const Text('裁剪头像'),
      ),
      body: Column(
        children: [
          Expanded(
            child: LayoutBuilder(
              builder: (context, box) {
                final side = box.biggest.shortestSide;
                return Center(
                  child: SizedBox(
                    width: side,
                    height: side,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        ClipRect(
                          child: RepaintBoundary(
                            key: _previewKey,
                            child: InteractiveViewer(
                              transformationController: _tx,
                              minScale: 1,
                              maxScale: 4,
                              child: Image.memory(
                                widget.bytes,
                                fit: BoxFit.cover,
                                width: side,
                                height: side,
                                gaplessPlayback: true,
                              ),
                            ),
                          ),
                        ),
                        IgnorePointer(
                          child: CustomPaint(
                            painter: _HolePainter(
                              color: Colors.black.withValues(alpha: 0.45),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _busy ? null : () => Navigator.pop(context),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.white,
                        side: const BorderSide(color: Colors.white54),
                      ),
                      child: const Text('取消'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: FilledButton(
                      onPressed: _busy ? null : _confirm,
                      style: FilledButton.styleFrom(
                        backgroundColor: context.primary,
                      ),
                      child: Text(_busy ? '处理中' : '完成'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _HolePainter extends CustomPainter {
  _HolePainter({required this.color});

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final r = Rect.fromLTWH(0, 0, size.width, size.height);
    final path = Path()
      ..addRect(r)
      ..addOval(r)
      ..fillType = PathFillType.evenOdd;
    canvas.drawPath(path, Paint()..color = color);
  }

  @override
  bool shouldRepaint(covariant _HolePainter old) => old.color != color;
}
