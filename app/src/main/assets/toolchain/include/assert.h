#ifndef _CCP_ASSERT_H
#define _CCP_ASSERT_H
void __assert(const char *, int, const char *);
#undef assert
#ifdef NDEBUG
#define assert(e) ((void)0)
#else
#define assert(e) ((e) ? (void)0 : __assert(__FILE__, __LINE__, #e))
#endif
#endif
