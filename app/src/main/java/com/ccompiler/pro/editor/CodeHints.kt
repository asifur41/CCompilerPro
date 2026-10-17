package com.ccompiler.pro.editor

data class Hint(val name: String, val signature: String, val insert: String, val back: Int)

object CodeHints {
    private fun fn(name: String, sig: String, ins: String = "$name()", back: Int = 1) = Hint(name, sig, ins, back)

    private val all: List<Hint> = listOf(
        fn("printf", "int printf(const char *fmt, ...)", "printf(\"\")", 2),
        fn("scanf", "int scanf(const char *fmt, ...)", "scanf(\"\")", 2),
        fn("sprintf", "int sprintf(char *buf, const char *fmt, ...)"),
        fn("snprintf", "int snprintf(char *buf, size_t n, const char *fmt, ...)"),
        fn("fprintf", "int fprintf(FILE *f, const char *fmt, ...)"),
        fn("puts", "int puts(const char *s)"),
        fn("putchar", "int putchar(int c)"),
        fn("getchar", "int getchar(void)"),
        fn("fgets", "char *fgets(char *buf, int n, FILE *f)"),
        fn("fputs", "int fputs(const char *s, FILE *f)"),
        fn("fopen", "FILE *fopen(const char *path, const char *mode)", "fopen(\"\", \"r\")", 7),
        fn("fclose", "int fclose(FILE *f)"),
        fn("fread", "size_t fread(void *p, size_t size, size_t n, FILE *f)"),
        fn("fwrite", "size_t fwrite(const void *p, size_t size, size_t n, FILE *f)"),
        fn("malloc", "void *malloc(size_t size)"),
        fn("calloc", "void *calloc(size_t n, size_t size)"),
        fn("realloc", "void *realloc(void *p, size_t size)"),
        fn("free", "void free(void *p)"),
        fn("exit", "void exit(int status)"),
        fn("atoi", "int atoi(const char *s)"),
        fn("atof", "double atof(const char *s)"),
        fn("rand", "int rand(void)", "rand()", 0),
        fn("srand", "void srand(unsigned seed)"),
        fn("abs", "int abs(int x)"),
        fn("qsort", "void qsort(void *base, size_t n, size_t size, cmp)"),
        fn("strlen", "size_t strlen(const char *s)"),
        fn("strcpy", "char *strcpy(char *dst, const char *src)"),
        fn("strncpy", "char *strncpy(char *dst, const char *src, size_t n)"),
        fn("strcat", "char *strcat(char *dst, const char *src)"),
        fn("strcmp", "int strcmp(const char *a, const char *b)"),
        fn("strncmp", "int strncmp(const char *a, const char *b, size_t n)"),
        fn("strchr", "char *strchr(const char *s, int c)"),
        fn("strstr", "char *strstr(const char *hay, const char *needle)"),
        fn("memcpy", "void *memcpy(void *dst, const void *src, size_t n)"),
        fn("memset", "void *memset(void *p, int c, size_t n)"),
        fn("toupper", "int toupper(int c)"),
        fn("tolower", "int tolower(int c)"),
        fn("isdigit", "int isdigit(int c)"),
        fn("isalpha", "int isalpha(int c)"),
        fn("isalnum", "int isalnum(int c)"),
        fn("isspace", "int isspace(int c)"),
        fn("sqrt", "double sqrt(double x)"),
        fn("pow", "double pow(double x, double y)"),
        fn("sin", "double sin(double x)"),
        fn("cos", "double cos(double x)"),
        fn("fabs", "double fabs(double x)"),
        fn("floor", "double floor(double x)"),
        fn("ceil", "double ceil(double x)"),
        fn("time", "time_t time(time_t *t)", "time(NULL)", 0),
        Hint("include", "#include <header.h>", "#include <>", 1),
        Hint("for", "for loop", "for (int i = 0; i < n; i++) {\n    \n}", 2),
        Hint("while", "while loop", "while () {\n    \n}", 10),
        Hint("if", "if statement", "if () {\n    \n}", 10),
        Hint("switch", "switch statement", "switch () {\n    case 0:\n        break;\n    default:\n        break;\n}", 60),
        Hint("return", "return value", "return 0;", 0)
    )

    fun suggest(prefix: String, max: Int = 8): List<Hint> {
        if (prefix.length < 2) return emptyList()
        val p = prefix.lowercase()
        return all.filter { it.name.startsWith(p) && it.name != prefix }.take(max)
    }
}
