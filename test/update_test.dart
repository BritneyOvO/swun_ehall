import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/update.dart';

void main() {
  test('compares dotted versions', () {
    expect(compareVersions('1.0.1', '1.0.0'), greaterThan(0));
    expect(compareVersions('v1.2.0', '1.1.9'), greaterThan(0));
    expect(compareVersions('1.0.0', 'v1.0.0'), 0);
    expect(compareVersions('1.0.0+2', '1.0.0'), 0);
    expect(compareVersions('2.0.0', '1.9.9'), greaterThan(0));
    expect(compareVersions('1.0', '1.0.1'), lessThan(0));
    expect(isNewerVersion('1.0.1', '1.0.0'), isTrue);
    expect(isNewerVersion('1.0.0', '1.0.0'), isFalse);
  });

  test('parses GitHub release JSON and skips drafts', () {
    final rel = parseGithubRelease({
      'tag_name': 'v1.1.0',
      'html_url': 'https://github.com/BritneyOvO/swun_ehall/releases/tag/v1.1.0',
      'draft': false,
      'prerelease': false,
      'body': 'fix login',
      'assets': [
        {
          'name': 'swun_ehall-1.1.0-arm64-debug.apk',
          'browser_download_url': 'https://example.com/debug.apk',
        },
        {
          'name': 'swun_ehall-1.1.0-arm64-release.apk',
          'browser_download_url': 'https://example.com/release.apk',
        },
      ],
    });
    expect(rel?.version, '1.1.0');
    expect(rel?.apkUrl, 'https://example.com/release.apk');
    expect(isNewerVersion(rel!.version, '1.0.0'), isTrue);

    expect(parseGithubRelease({'tag_name': 'v9.0.0', 'draft': true}), isNull);

    final picked = pickLatestRelease([
      {
        'tag_name': 'v1.2.0',
        'draft': false,
        'prerelease': true,
        'html_url': 'https://example.com/pre',
      },
      {
        'tag_name': 'v1.1.0',
        'draft': false,
        'prerelease': false,
        'html_url': 'https://example.com/stable',
      },
    ]);
    expect(picked?.version, '1.1.0');
  });
}
