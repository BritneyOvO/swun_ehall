import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../api/campus_fences.dart';
import '../api/geo.dart';
import '../api/locate.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';
import '../widgets/toast.dart';

class _RoomFix {
  const _RoomFix({
    required this.room,
    required this.course,
    required this.fence,
  });

  final String room;
  final String course;
  final CampusFence fence;

  double get gcjLat => fence.centerLat;
  double get gcjLng => fence.centerLng;
}

const _kRooms = <_RoomFix>[
  _RoomFix(room: 'H-206', course: '无线网络与移动计算', fence: kFenceH),
  _RoomFix(room: 'H-207', course: '网络安全基础及法律法规', fence: kFenceH),
  _RoomFix(room: 'BS-217', course: '学期实训（网络开发）', fence: kFenceBs),
  _RoomFix(room: 'BS-222', course: '学期实训（网络仿真）', fence: kFenceBs),
  _RoomFix(room: 'BS-223', course: '网络攻防', fence: kFenceBs),
  _RoomFix(room: 'BS-224', course: '计算机专业基础及实践', fence: kFenceBs),
  _RoomFix(room: 'BS-241', course: '计算机组成原理实验', fence: kFenceBs),
  _RoomFix(room: 'BS-243', course: '网络安全基础及法律法规(01A)', fence: kFenceBs),
  _RoomFix(room: 'BW-106', course: '数字通信原理及协议', fence: kFenceBw),
  _RoomFix(room: 'BW-107', course: '计算机组成原理', fence: kFenceBw),
  _RoomFix(room: 'BX-317', course: '形势与政策（五）', fence: kFenceBx),
];

class LocateTestPage extends StatefulWidget {
  const LocateTestPage({super.key});

  @override
  State<LocateTestPage> createState() => _LocateTestPageState();
}

class _LocateTestPageState extends State<LocateTestPage> {
  String? _busyRoom;
  final _log = StringBuffer();

  void _append(String line) {
    setState(() {
      _log.writeln(line);
    });
  }

  Future<void> _run(_RoomFix room) async {
    if (_busyRoom != null) return;
    setState(() {
      _busyRoom = room.room;
      _log.clear();
    });
    try {
      await _runBody(room);
    } catch (e) {
      _append('异常: ${e.toString().replaceFirst('Exception: ', '')}');
    } finally {
      if (mounted) setState(() => _busyRoom = null);
    }
  }

  Future<void> _runBody(_RoomFix room) async {
    final f = room.fence;
    _append('教室 ${room.room}  ${room.course}  围栏 ${f.name} 楼');
    _append(
      '中心 ${f.centerLat.toStringAsFixed(6)}, ${f.centerLng.toStringAsFixed(6)}',
    );
    _append(
      '南 ${f.southLat.toStringAsFixed(5)}, ${f.southLng.toStringAsFixed(5)}  '
      '北 ${f.northLat.toStringAsFixed(5)}, ${f.northLng.toStringAsFixed(5)}',
    );
    _append(
      '西 ${f.westLat.toStringAsFixed(5)}, ${f.westLng.toStringAsFixed(5)}  '
      '东 ${f.eastLat.toStringAsFixed(5)}, ${f.eastLng.toStringAsFixed(5)}',
    );
    _append(
      '旧拼接点(北纬+东经) ${f.northLat.toStringAsFixed(6)}, ${f.eastLng.toStringAsFixed(6)}  '
      '距中心 ${geoMeters(f.northLat, f.eastLng, f.centerLat, f.centerLng).toStringAsFixed(0)} m（会出东西围栏）',
    );

    final simWgs = gcj02ToWgs84(room.gcjLat, room.gcjLng);
    _append(
      '模拟 GPS(WGS)  ${simWgs.$1.toStringAsFixed(6)}, ${simWgs.$2.toStringAsFixed(6)}',
    );

    final asGps = campusGcj02(simWgs.$1, simWgs.$2, source: 'gps');
    final asFused = campusGcj02(simWgs.$1, simWgs.$2, source: 'fused');
    final asNet = campusGcj02(simWgs.$1, simWgs.$2, source: 'network');
    final noConvert = campusGcj02(
      simWgs.$1,
      simWgs.$2,
      source: 'gps',
      datum: 'gcj02',
    );

    _append(
      '转换 gps    ${asGps.$1.toStringAsFixed(6)}, ${asGps.$2.toStringAsFixed(6)}  '
      '距教室 ${geoMeters(asGps.$1, asGps.$2, room.gcjLat, room.gcjLng).toStringAsFixed(1)} m',
    );
    _append(
      '转换 fused  ${asFused.$1.toStringAsFixed(6)}, ${asFused.$2.toStringAsFixed(6)}  '
      '距教室 ${geoMeters(asFused.$1, asFused.$2, room.gcjLat, room.gcjLng).toStringAsFixed(1)} m',
    );
    _append(
      '不转(当 GCJ) ${noConvert.$1.toStringAsFixed(6)}, ${noConvert.$2.toStringAsFixed(6)}  '
      '距教室 ${geoMeters(noConvert.$1, noConvert.$2, room.gcjLat, room.gcjLng).toStringAsFixed(0)} m',
    );
    _append(
      'network 不转 ${asNet.$1.toStringAsFixed(6)}, ${asNet.$2.toStringAsFixed(6)}  '
      '距教室 ${geoMeters(asNet.$1, asNet.$2, room.gcjLat, room.gcjLng).toStringAsFixed(0)} m',
    );

    final s = context.read<Session>();
    if (s.demoMode) {
      _append('示例模式：不打官网预检');
      return;
    }
    await s.ensureKtkq().timeout(const Duration(seconds: 50));
    final week = await s.ktkq!.weekCourses(refresh: true);
    final rows = week['data'];
    if (rows is! List) {
      _append('本周课程拉不到，无法预检');
      return;
    }
    final meta = week['_meta'] is Map
        ? Map<String, dynamic>.from(week['_meta'] as Map)
        : <String, dynamic>{};
    final st = meta['schoolTime'] is Map
        ? Map<String, dynamic>.from(meta['schoolTime'] as Map)
        : <String, dynamic>{};
    final weekNum =
        int.tryParse('${meta['skzc'] ?? st['todayWeekNum'] ?? 1}') ?? 1;
    final hits = <({Map<String, dynamic> course, Map<String, dynamic> slot})>[];
    for (final raw in rows) {
      if (raw is! Map) continue;
      final c = Map<String, dynamic>.from(raw);
      final list = (c['list'] as List?) ?? [];
      for (final it in list) {
        if (it is! Map) continue;
        final item = Map<String, dynamic>.from(it);
        final jasmc = '${item['jasmc'] ?? ''}'.trim();
        final kcm = '${item['kcm'] ?? c['kcm'] ?? ''}'.trim();
        if (jasmc == room.room ||
            jasmc.contains(room.room) ||
            kcm.contains(room.course) ||
            room.course.contains(kcm)) {
          hits.add((course: c, slot: item));
        }
      }
    }
    if (hits.isEmpty) {
      _append('本周课表没有教室 ${room.room}，无法预检');
      return;
    }
    _append('课表命中 ${hits.length} 条（围栏绑的是 kbid，会逐条预检）');

    GeoFix? live;
    try {
      live = await AppLocator.current(
        demo: false,
        force: true,
        timeout: const Duration(seconds: 12),
      );
      _append(
        '真机 ${live.sourceLabel} ${live.coordText} 精度 ${live.accuracy.toStringAsFixed(0)} m',
      );
    } catch (e) {
      _append('真机定位失败 $e');
    }

    var anyAllow = false;
    for (var i = 0; i < hits.length; i++) {
      final course = hits[i].course;
      final slot = hits[i].slot;
      final teachClassId = '${slot['jxbid'] ?? course['jxbid'] ?? ''}'.trim();
      final teachClassType = '${slot['jxblx'] ?? course['jxblx'] ?? ''}'.trim();
      final scheduleId = '${slot['kbid'] ?? course['kbid'] ?? ''}'.trim();
      final weekDay = int.tryParse('${slot['skxq'] ?? ''}') ?? 0;
      final startNode = int.tryParse('${slot['ksjc'] ?? ''}') ?? 0;
      final endNode = int.tryParse('${slot['jsjc'] ?? startNode}') ?? startNode;
      final kbShort = scheduleId.length <= 16
          ? scheduleId
          : '${scheduleId.substring(0, 10)}…${scheduleId.substring(scheduleId.length - 6)}';
      _append(
        '—— 第${i + 1}条 ${course['kcm'] ?? room.course}  '
        'jxb=${teachClassId.length > 8 ? '${teachClassId.substring(0, 8)}…' : teachClassId}  '
        'kb=$kbShort  weekDay=$weekDay 节$startNode~$endNode',
      );
      if (teachClassId.isEmpty || scheduleId.isEmpty || weekDay < 1) {
        _append('  缺 jxbid/kbid/星期，跳过');
        continue;
      }
      var activityId = '';
      if (teachClassType.isNotEmpty) {
        try {
          final current = await s.ktkq!.queryCurrentLesson(
            teachClassId: teachClassId,
            teachClassType: teachClassType,
            scheduleId: scheduleId,
            week: weekNum,
            weekDay: weekDay,
            startNode: startNode,
            endNode: endNode,
          );
          final data = current['data'];
          final list = data is Map ? data['activityList'] : null;
          if (list is List) {
            for (final a in list) {
              if (a is! Map) continue;
              final id = '${a['activityId'] ?? ''}'.trim();
              if (id.isNotEmpty) {
                activityId = id;
                break;
              }
            }
          }
          _append('  queryCurrentLesson activityId=${activityId.isEmpty ? '(空)' : activityId}');
        } catch (e) {
          _append('  queryCurrentLesson 失败，activityId 留空');
        }
      }

      Future<String> probe(String label, double lat, double lng) async {
        _append(
          '  预检 $label  ${lat.toStringAsFixed(6)}, ${lng.toStringAsFixed(6)}',
        );
        final r = await s.ktkq!.checkAllowSign(
          teachClassId: teachClassId,
          scheduleId: scheduleId,
          week: weekNum,
          weekDay: weekDay,
          startNode: startNode,
          endNode: endNode,
          activityId: activityId,
          latitude: lat,
          longitude: lng,
          accuracy: 8,
        );
        final data = r['data'];
        final status = data is Map ? '${data['status'] ?? ''}' : '';
        _append('    $status  ${r['msg'] ?? ''}');
        if (status == 'IS_ALLOW') anyAllow = true;
        return status;
      }

      await probe('楼中心(转换后)', asGps.$1, asGps.$2);
      await probe('楼中心不转WGS', simWgs.$1, simWgs.$2);
      await probe('东沿', f.eastLat, f.eastLng);
      await probe('西沿', f.westLat, f.westLng);
      await probe('北纬+东经拼接点', f.northLat, f.eastLng);
      for (final remembered in s.rooms.items) {
        if (remembered.room == room.room) {
          await probe(
            '本机上次 ${remembered.coordText}',
            remembered.latitude,
            remembered.longitude,
          );
          break;
        }
      }
      if (live != null) {
        await probe('真机', live.latitude, live.longitude);
      }
    }

    _append(
      anyAllow
          ? '总结: 转换闭环，楼中心预检是 IS_ALLOW。'
          : '总结: 转换已闭环。若楼中心仍 NOT_IN_SCOPE，把完整日志复制下来。',
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('测试定位')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            '用各楼围栏中心模拟 GPS 再转换。预检打 checkAllowSign，不记签到。中心应 IS_ALLOW；北纬拼东经会出东西围栏。',
            style: TextStyle(color: context.muted, fontSize: 13, height: 1.4),
          ),
          const SizedBox(height: 12),
          for (final room in _kRooms)
            Card(
              margin: const EdgeInsets.only(bottom: 8),
              child: ListTile(
                title: Text('${room.room}  ${room.course}'),
                subtitle: Text(
                  '${room.fence.name} 中心 ${room.gcjLat.toStringAsFixed(5)}, ${room.gcjLng.toStringAsFixed(5)}',
                  style: TextStyle(color: context.muted, fontSize: 12),
                ),
                trailing: _busyRoom == room.room
                    ? const SwunBusyDots(size: 5)
                    : Icon(Icons.play_arrow_rounded, color: context.primary),
                onTap: _busyRoom == null ? () => _run(room) : null,
              ),
            ),
          if (_log.isNotEmpty) ...[
            const SizedBox(height: 8),
            Row(
              children: [
                const Text('结果', style: TextStyle(fontWeight: FontWeight.w600)),
                const Spacer(),
                TextButton(
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: _log.toString()));
                    if (context.mounted) showToast(context, '已复制');
                  },
                  child: const Text('复制'),
                ),
              ],
            ),
            SelectableText(
              _log.toString(),
              style: const TextStyle(fontFamily: 'monospace', fontSize: 12, height: 1.4),
            ),
          ],
        ],
      ),
    );
  }
}
