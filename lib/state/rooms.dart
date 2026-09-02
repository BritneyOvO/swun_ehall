import 'dart:convert';
import 'dart:io';

class RoomFix {
  const RoomFix({
    required this.room,
    required this.latitude,
    required this.longitude,
    this.accuracy = 0,
    this.course = '',
    this.at,
  });

  final String room;
  final double latitude;
  final double longitude;
  final double accuracy;
  final String course;
  final DateTime? at;

  String get coordText => '${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)}';

  Map<String, dynamic> toJson() => {
        'room': room,
        'latitude': latitude,
        'longitude': longitude,
        'accuracy': accuracy,
        'course': course,
        'at': at?.toIso8601String() ?? '',
      };

  static RoomFix? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final room = '${raw['room'] ?? ''}'.trim();
    final lat = (raw['latitude'] as num?)?.toDouble();
    final lng = (raw['longitude'] as num?)?.toDouble();
    if (room.isEmpty || lat == null || lng == null) return null;
    final atRaw = '${raw['at'] ?? ''}';
    return RoomFix(
      room: room,
      latitude: lat,
      longitude: lng,
      accuracy: (raw['accuracy'] as num?)?.toDouble() ?? 0,
      course: '${raw['course'] ?? ''}',
      at: atRaw.isEmpty ? null : DateTime.tryParse(atRaw),
    );
  }
}

class RoomStore {
  String? _path;
  List<RoomFix> items = [];

  Future<void> bind(String path) async {
    _path = path;
    await load();
  }

  Future<void> load() async {
    items = [];
    final path = _path;
    if (path == null) return;
    try {
      final f = File(path);
      if (!await f.exists()) return;
      final raw = jsonDecode(await f.readAsString());
      final list = raw is Map ? raw['rooms'] : raw;
      if (list is! List) return;
      final out = <RoomFix>[];
      for (final e in list) {
        final fix = RoomFix.fromJson(e);
        if (fix != null) out.add(fix);
      }
      out.sort((a, b) {
        final at = (b.at ?? DateTime.fromMillisecondsSinceEpoch(0)).compareTo(a.at ?? DateTime.fromMillisecondsSinceEpoch(0));
        return at != 0 ? at : a.room.compareTo(b.room);
      });
      items = out;
    } catch (_) {}
  }

  Future<void> record({
    required String room,
    required double latitude,
    required double longitude,
    double accuracy = 0,
    String course = '',
  }) async {
    final name = room.trim();
    if (name.isEmpty || !latitude.isFinite || !longitude.isFinite) return;
    final next = RoomFix(
      room: name,
      latitude: latitude,
      longitude: longitude,
      accuracy: accuracy,
      course: course.trim(),
      at: DateTime.now(),
    );
    items = [next, for (final e in items) if (e.room != name) e];
    await _save();
  }

  Future<void> remove(String room) async {
    items = [for (final e in items) if (e.room != room) e];
    await _save();
  }

  Future<void> _save() async {
    final path = _path;
    if (path == null) return;
    final f = File(path);
    await f.parent.create(recursive: true);
    await f.writeAsString(
      jsonEncode({
        'rooms': [for (final e in items) e.toJson()],
      }),
    );
  }
}
