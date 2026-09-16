import 'dart:convert';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../models/credit.dart';
import '../models/profile.dart';
import 'cas.dart';
import 'httpx.dart';
import 'jwxt/xk.dart';

export 'jwxt/xk.dart';

part 'jwxt/academic.dart';
part 'jwxt/html.dart';
part 'jwxt/profile.dart';
part 'jwxt/selection.dart';
part 'jwxt/session.dart';

const kJwxt = 'https://jwxt.swun.edu.cn';
const kJwxtService = 'http://jwxt.swun.edu.cn/sso/jziotlogin';

/// 自主选课功能码（jwglxt 所有 xsxk 请求都要带 ?gnmkdm=N253512）。
const kXkGnmkdm = 'N253512';
const kXkReferer = '$kJwxt/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=$kXkGnmkdm';
const _jwxtMenuReferer = '$kJwxt/jwglxt/xtgl/index_initMenu.html?jsdm=xs';

(int, String) currentTerm() {
  final now = DateTime.now();
  if (now.month >= 8 || now.month <= 1) return (now.year, '3');
  return (now.year - 1, '12');
}

/// 正方教务客户端：会话 + 成绩/学业/考试 + 选课 + 档案。
class JwxtClient
    with JwxtSessionApi, JwxtAcademicApi, JwxtSelectionApi, JwxtProfileApi {
  JwxtClient(this.jar) : dio = buildDio(jar);

  @override
  final CookieJar jar;
  @override
  final Dio dio;
}
