import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/models/profile.dart';

void main() {
  test('does not keep Lantu classId as 班级', () {
    final lantu = const StudentProfile(
      college: '计算机与人工智能学院',
      major: '网络工程',
      klass: '0809032401',
    );
    final jwxt = const StudentProfile(klass: '网络工程2024-1班');
    final merged = lantu.merge(jwxt);
    expect(StudentProfile.looksLikeCode('0809032401'), isTrue);
    expect(merged.klass, '网络工程2024-1班');
    expect(merged.major, '网络工程');
    expect(merged.college, '计算机与人工智能学院');
  });
}
