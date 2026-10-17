#ifndef _CCP_TIME_H
#define _CCP_TIME_H
#include <stddef.h>
typedef long time_t;
typedef long clock_t;
#define CLOCKS_PER_SEC 1000000
struct tm {
    int tm_sec; int tm_min; int tm_hour; int tm_mday; int tm_mon; int tm_year;
    int tm_wday; int tm_yday; int tm_isdst; long tm_gmtoff; const char *tm_zone;
};
time_t time(time_t *);
clock_t clock(void);
double difftime(time_t, time_t);
time_t mktime(struct tm *);
struct tm *localtime(const time_t *);
struct tm *gmtime(const time_t *);
size_t strftime(char *, size_t, const char *, const struct tm *);
char *asctime(const struct tm *);
char *ctime(const time_t *);
#endif
