import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/update.dart';
import 'package:swun_ehall/main.dart';

void main() {
  testWidgets('app boots', (tester) async {
    loadLatestRelease = () async => null;
    addTearDown(() => loadLatestRelease = fetchLatestRelease);
    await tester.pumpWidget(const SwunApp());
    await tester.pump(const Duration(milliseconds: 1400));
    await tester.pump();
    expect(find.byType(SwunApp), findsOneWidget);
  });
}
