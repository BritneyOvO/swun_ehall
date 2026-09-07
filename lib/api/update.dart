import 'package:dio/dio.dart';

import 'httpx.dart';

const kAppVersion = '1.0.2';
const kGithubRepo = 'BritneyOvO/swun_ehall';
const kGithubReleasesApi =
    'https://api.github.com/repos/$kGithubRepo/releases';

class AppRelease {
  const AppRelease({
    required this.version,
    required this.htmlUrl,
    this.apkUrl,
    this.notes = '',
    this.prerelease = false,
  });

  final String version;
  final String htmlUrl;
  final String? apkUrl;
  final String notes;
  final bool prerelease;

  String get openUrl =>
      (apkUrl != null && apkUrl!.isNotEmpty) ? apkUrl! : htmlUrl;

  bool get isNewer => isNewerVersion(version, kAppVersion);
}

int compareVersions(String a, String b) {
  final pa = _parts(a);
  final pb = _parts(b);
  final n = pa.length > pb.length ? pa.length : pb.length;
  for (var i = 0; i < n; i++) {
    final x = i < pa.length ? pa[i] : 0;
    final y = i < pb.length ? pb[i] : 0;
    if (x != y) return x.compareTo(y);
  }
  return 0;
}

bool isNewerVersion(String remote, String current) =>
    compareVersions(remote, current) > 0;

List<int> _parts(String raw) {
  var s = raw.trim();
  if (s.startsWith('v') || s.startsWith('V')) s = s.substring(1);
  s = s.split('+').first.split('-').first;
  if (s.isEmpty) return const [0];
  return s.split('.').map((e) => int.tryParse(e) ?? 0).toList();
}

AppRelease? parseGithubRelease(Object? raw) {
  if (raw is! Map) return null;
  if (raw['draft'] == true) return null;
  final tag = '${raw['tag_name'] ?? ''}'.trim();
  if (tag.isEmpty) return null;
  var version = tag;
  if (version.startsWith('v') || version.startsWith('V')) {
    version = version.substring(1);
  }
  String? apk;
  final assets = raw['assets'];
  if (assets is List) {
    for (final a in assets) {
      if (a is! Map) continue;
      final name = '${a['name'] ?? ''}';
      final url = '${a['browser_download_url'] ?? ''}';
      if (!name.toLowerCase().endsWith('.apk') || url.isEmpty) continue;
      if (name.contains('release')) {
        apk = url;
        break;
      }
      apk ??= url;
    }
  }
  var html = '${raw['html_url'] ?? ''}'.trim();
  if (html.isEmpty) {
    html = 'https://github.com/$kGithubRepo/releases/tag/$tag';
  }
  return AppRelease(
    version: version,
    htmlUrl: html,
    apkUrl: apk,
    notes: '${raw['body'] ?? ''}'.trim(),
    prerelease: raw['prerelease'] == true,
  );
}

AppRelease? pickLatestRelease(Object? raw) {
  if (raw is Map) return parseGithubRelease(raw);
  if (raw is! List) return null;
  AppRelease? pre;
  for (final item in raw) {
    final rel = parseGithubRelease(item);
    if (rel == null) continue;
    if (!rel.prerelease) return rel;
    pre ??= rel;
  }
  return pre;
}

Future<AppRelease?> Function() loadLatestRelease = fetchLatestRelease;

Future<AppRelease?> fetchLatestRelease() async {
  final dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 8),
      receiveTimeout: const Duration(seconds: 10),
      headers: {
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'swun-ehall',
        'X-GitHub-Api-Version': '2022-11-28',
      },
      validateStatus: (s) => s != null && s < 500,
    ),
  );
  attachHttpClient(dio);
  try {
    final latest = await dio.get('$kGithubReleasesApi/latest');
    if (latest.statusCode == 200) {
      return pickLatestRelease(latest.data);
    }
    if (latest.statusCode != 404) {
      throw Exception('HTTP ${latest.statusCode}');
    }
    final list = await dio.get(
      kGithubReleasesApi,
      queryParameters: {'per_page': 8},
    );
    if (list.statusCode != 200) {
      throw Exception('HTTP ${list.statusCode}');
    }
    return pickLatestRelease(list.data);
  } finally {
    dio.close(force: true);
  }
}
