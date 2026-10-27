package com.ccompiler.pro.editor

/** [snippet] hints are multi-line templates; their insert text contains [CodeHints.CUR] where the cursor goes. */
data class Hint(val name: String, val signature: String, val insert: String, val back: Int, val snippet: Boolean = false)

object CodeHints {
    /** Marks where the cursor goes after a snippet is inserted. */
    const val CUR = "@@"

    private fun fn(name: String, sig: String, ins: String = "$name()", back: Int = 1) = Hint(name, sig, ins, back)
    private fun snip(name: String, label: String, template: String) = Hint(name, label, template, 0, true)

    private val library: List<Hint> = listOf(
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
        Hint("return", "return value", "return 0;", 0)
    )

    private val snippets: List<Hint> = listOf(
        snip("main", "main function", "int main() {\n    @@\n    return 0;\n}"),
        snip("for", "for loop", "for (int i = 0; i < @@; i++) {\n    \n}"),
        snip("forr", "for loop (backwards)", "for (int i = @@ - 1; i >= 0; i--) {\n    \n}"),
        snip("while", "while loop", "while (@@) {\n    \n}"),
        snip("do", "do-while loop", "do {\n    @@\n} while ();"),
        snip("if", "if", "if (@@) {\n    \n}"),
        snip("ife", "if / else", "if (@@) {\n    \n} else {\n    \n}"),
        snip("switch", "switch", "switch (@@) {\n    case 0:\n        break;\n    default:\n        break;\n}"),
        snip("func", "function", "void name(@@) {\n    \n}"),
        snip("struct", "struct", "struct Name {\n    @@\n};"),
        snip("typedef", "typedef struct", "typedef struct {\n    @@\n} Name;"),
        snip("enum", "enum", "enum Name {\n    @@\n};"),
        snip("pf", "printf line", "printf(\"@@\\n\");"),
        snip("sf", "scanf a number", "scanf(\"%d\", &@@);"),
        snip("mal", "malloc array", "int *p = (int *)malloc(@@ * sizeof(int));\nif (p == NULL) {\n    return 1;\n}"),
        snip("fo", "open a file", "FILE *fp = fopen(\"@@\", \"r\");\nif (fp == NULL) {\n    return 1;\n}\nfclose(fp);"),
        snip("include", "#include", "#include <@@>"),
        snip("node", "linked-list node", "struct Node {\n    int data;\n    struct Node *next;\n};")
    )

    /** Names of the library functions, so local-name suggestions do not repeat them. */
    val libraryNames: Set<String> get() = library.map { it.name }.toSet()

    /** Order: snippets (if on), then the user's own names, then library functions. */
    fun suggest(prefix: String, snippetsOn: Boolean = true, locals: List<LocalSymbol> = emptyList(), max: Int = 8): List<Hint> {
        if (prefix.length < 2) return emptyList()
        val p = prefix.lowercase()
        val out = ArrayList<Hint>()
        if (snippetsOn) snippets.filterTo(out) { it.name.startsWith(p) && it.name != prefix }
        for (l in locals) {
            if (l.name == prefix || !l.name.startsWith(prefix, ignoreCase = true)) continue
            out.add(
                Hint(
                    l.name,
                    if (l.isFunction) l.name + "()  - yours" else l.name + "  - yours",
                    if (l.isFunction) l.name + "()" else l.name,
                    if (l.isFunction) 1 else 0
                )
            )
        }
        library.filterTo(out) { it.name.startsWith(p) && it.name != prefix }
        return out.distinctBy { it.name + it.snippet }.take(max)
    }
}
