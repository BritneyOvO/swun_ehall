part of '../jwxt.dart';

Map<String, dynamic>? _asMap(Object? data) {
  if (data is Map<String, dynamic>) return data;
  if (data is Map) return Map<String, dynamic>.from(data);
  if (data is String) {
    final s = data.trim();
    if (s.startsWith('{') && s.endsWith('}')) {
      try {
        final d = jsonDecode(s);
        if (d is Map) return Map<String, dynamic>.from(d);
      } catch (_) {}
    }
  }
  return null;
}

Object? _asJson(Object? data) {
  if (data is List || data is Map) return data;
  if (data is String) {
    final s = data.trim();
    if (s.isEmpty) return const [];
    try {
      return jsonDecode(s);
    } catch (_) {}
  }
  return data;
}

class _XyqkNode {
  const _XyqkNode({
    required this.id,
    required this.name,
    required this.jdkcsx,
    required this.sfmjd,
    required this.yxxf,
    required this.yqzdxf,
  });

  final String id;
  final String name;
  final String jdkcsx;
  final String sfmjd;
  final double yxxf;
  final double yqzdxf;
}

List<_XyqkNode> _xyqkNodes(String html) {
  final src = html.replaceAll(r"\'", "'");
  final seen = <String, _XyqkNode>{};
  final metaRe = RegExp(
    r"xfyqjd_id='([^']+)' jdkcsx='([^']*)' leaf='([^']*)' sfmjd='([^']*)'",
  );
  for (final m in metaRe.allMatches(src)) {
    final id = m.group(1)!;
    if (seen.containsKey(id)) continue;
    seen[id] = _XyqkNode(
      id: id,
      name: '',
      jdkcsx: m.group(2) ?? '',
      sfmjd: m.group(4) ?? '',
      yxxf: 0,
      yqzdxf: 0,
    );
  }
  final titleRe = RegExp(
    r"""id='p([^']+)' yxxf='([^']*)' yqzdxf='([^']*)' sftg='([^']*)'>"\s*\+\s*"([^"&<]+)""",
  );
  for (final m in titleRe.allMatches(src)) {
    final id = m.group(1)!;
    final prev = seen[id];
    if (prev == null) continue;
    seen[id] = _XyqkNode(
      id: id,
      name: prev.name.isNotEmpty ? prev.name : (m.group(5) ?? '').trim(),
      jdkcsx: prev.jdkcsx,
      sfmjd: prev.sfmjd,
      yxxf: double.tryParse(m.group(2) ?? '') ?? 0,
      yqzdxf: double.tryParse(m.group(3) ?? '') ?? 0,
    );
  }
  return seen.values.toList();
}

double _xyqkLabelXf(String html, String label) {
  final m = RegExp('$label[\\s\\S]{0,400}?>([0-9.]+)<').firstMatch(html);
  return double.tryParse(m?.group(1) ?? '') ?? 0;
}

double? _xyqkGpa(String html) {
  final i = html.indexOf('GPA');
  if (i < 0) return null;
  final end = i + 500 < html.length ? i + 500 : html.length;
  final m = RegExp(r'([0-9]+\.[0-9]+)').firstMatch(html.substring(i, end));
  return double.tryParse(m?.group(1) ?? '');
}

double _firstXf(List<double> xs) {
  for (final v in xs) {
    if (v > 0) return v;
  }
  return 0;
}


String _namedValue(String html, String name) {
  final n = RegExp.escape(name);
  final patterns = [
    RegExp('(?:name|id)=[\'"]$n[\'"][^>]*value=[\'"]([^\'"]+)[\'"]', caseSensitive: false),
    RegExp('value=[\'"]([^\'"]+)[\'"][^>]*(?:name|id)=[\'"]$n[\'"]', caseSensitive: false),
    RegExp('id=[\'"]col_$n[\'"][^>]*>\\s*([^<]+)', caseSensitive: false),
  ];
  for (final re in patterns) {
    final m = re.firstMatch(html);
    if (m != null) {
      final v = m.group(1)!.trim();
      if (v.isNotEmpty && v != 'null') return v;
    }
  }
  return '';
}

String _clean(String raw) {
  return raw
      .replaceAll('&nbsp;', ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}

bool _plausible(String v) {
  final t = _clean(v);
  if (t.isEmpty || t == 'null' || t == 'undefined') return false;
  if (t.endsWith('：') || t.endsWith(':')) return false;
  if (t.contains('请选择') || t.contains('请填写') || t.contains('请输入')) return false;
  if (RegExp(r'^(姓名|学号|性别|学院|专业|班级|年级|校区|手机|民族|系名称)名称?$').hasMatch(t)) return false;
  return true;
}

String _staticAfter(String html, String label) {
  final re = RegExp(
    '$label\\s*[:：]?\\s*</label>\\s*<div[^>]*>\\s*<p class="form-control-static">\\s*([^<]+)',
    caseSensitive: false,
  );
  final v = _clean(re.firstMatch(html)?.group(1) ?? '');
  return _plausible(v) ? v : '';
}

String _cleanMajor(String v) {
  return v.replaceFirst(RegExp(r'\(\d+\)$'), '').trim();
}

String _firstNonEmpty(List<String> xs) {
  for (final x in xs) {
    final v = x.trim();
    if (v.isNotEmpty) return v;
  }
  return '';
}

String _gender(String v) {
  if (v == '1' || v == '男') return '男';
  if (v == '2' || v == '女') return '女';
  return v;
}

