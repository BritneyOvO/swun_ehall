class StudentProfile {
  const StudentProfile({
    this.studentId = '',
    this.name = '',
    this.gender = '',
    this.college = '',
    this.major = '',
    this.klass = '',
    this.grade = '',
    this.phone = '',
    this.campus = '',
    this.role = '',
    this.avatar = '',
  });

  final String studentId;
  final String name;
  final String gender;
  final String college;
  final String major;
  final String klass;
  final String grade;
  final String phone;
  final String campus;
  final String role;
  final String avatar;

  bool get hasDetails =>
      college.isNotEmpty || major.isNotEmpty || klass.isNotEmpty || grade.isNotEmpty || phone.isNotEmpty;

  bool get hasName => name.isNotEmpty && !_looksLikeId(name);

  StudentProfile merge(StudentProfile other) {
    return StudentProfile(
      studentId: _preferId(studentId, other.studentId),
      name: _preferName(name, other.name),
      gender: _prefer(gender, other.gender),
      college: _prefer(college, other.college),
      major: _prefer(major, other.major),
      klass: _prefer(klass, other.klass),
      grade: _prefer(grade, other.grade),
      phone: _prefer(phone, other.phone),
      campus: _prefer(campus, other.campus),
      role: _prefer(role, other.role),
      avatar: _prefer(avatar, other.avatar),
    );
  }

  static bool _looksLikeId(String s) => RegExp(r'^\d{8,}$').hasMatch(s.trim());

  static String _prefer(String a, String b) {
    if (a.isNotEmpty) return a;
    return b;
  }

  static String _preferId(String a, String b) {
    if (_looksLikeId(a)) return a;
    if (_looksLikeId(b)) return b;
    return _prefer(a, b);
  }

  static String _preferName(String a, String b) {
    if (b.isNotEmpty && !_looksLikeId(b)) {
      if (a.isEmpty || _looksLikeId(a)) return b;
    }
    if (a.isNotEmpty && !_looksLikeId(a)) return a;
    return _prefer(a, b);
  }
}
