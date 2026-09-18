/// 课堂考勤围栏实测四至。东西窄、南北长。签到提交用中心点。
class CampusFence {
  const CampusFence({
    required this.name,
    required this.southLat,
    required this.southLng,
    required this.northLat,
    required this.northLng,
    required this.westLat,
    required this.westLng,
    required this.eastLat,
    required this.eastLng,
    required this.centerLat,
    required this.centerLng,
  });

  final String name;
  final double southLat, southLng;
  final double northLat, northLng;
  final double westLat, westLng;
  final double eastLat, eastLng;
  final double centerLat, centerLng;
}

const kFenceBs = CampusFence(
  name: 'BS',
  southLat: 30.56059,
  southLng: 103.97158,
  northLat: 30.57513,
  northLng: 103.97098,
  westLat: 30.56781,
  westLng: 103.96942,
  eastLat: 30.56800,
  eastLng: 103.97278,
  centerLat: 30.56817,
  centerLng: 103.97111,
);

const kFenceBw = CampusFence(
  name: 'BW',
  southLat: 30.56172,
  southLng: 103.96863,
  northLat: 30.57547,
  northLng: 103.96975,
  westLat: 30.56766,
  westLng: 103.96774,
  eastLat: 30.56796,
  eastLng: 103.97118,
  centerLat: 30.56829,
  centerLng: 103.96972,
);

const kFenceBx = CampusFence(
  name: 'BX',
  southLat: 30.56152,
  southLng: 103.96960,
  northLat: 30.57485,
  northLng: 103.96960,
  westLat: 30.56819,
  westLng: 103.96785,
  eastLat: 30.56819,
  eastLng: 103.97135,
  centerLat: 30.56819,
  centerLng: 103.96960,
);

const kFenceH = CampusFence(
  name: 'H',
  southLat: 30.55718,
  southLng: 103.96894,
  northLat: 30.57137,
  northLng: 103.96815,
  westLat: 30.56444,
  westLng: 103.96702,
  eastLat: 30.56001,
  eastLng: 103.97020,
  centerLat: 30.56428,
  centerLng: 103.96861,
);

CampusFence? campusFenceForRoom(String room) {
  final u = room.trim().toUpperCase().replaceAll(' ', '');
  if (u.isEmpty) return null;
  if (RegExp(r'^BS-?\d').hasMatch(u)) return kFenceBs;
  if (RegExp(r'^BW-?\d').hasMatch(u)) return kFenceBw;
  if (RegExp(r'^BX-?\d').hasMatch(u)) return kFenceBx;
  if (RegExp(r'^H-?\d').hasMatch(u)) return kFenceH;
  return null;
}
