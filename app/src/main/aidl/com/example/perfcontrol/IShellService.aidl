package com.example.perfcontrol;

interface IShellService {
    // الرمز 16777114 مطلوب من Shizuku لإغلاق الخدمة
    void destroy() = 16777114;

    // ينفّذ الأمر ويعيد الناتج، وآخر سطر "__EXIT__:<code>"
    String exec(String command) = 1;
}
