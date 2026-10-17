#ifndef _CCP_ERRNO_H
#define _CCP_ERRNO_H
int *__errno(void);
#define errno (*__errno())
#define ENOENT 2
#define EIO 5
#define ENOMEM 12
#define EACCES 13
#define EEXIST 17
#define EINVAL 22
#define EDOM 33
#define ERANGE 34
#endif
