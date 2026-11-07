/*
 * Title: Command-Line Word Counter
 * Counts the words given as arguments and the characters in them, for example: hello "big world" C. Turn on Settings > Editor > Program arguments first.
 */
#include <stdio.h>
#include <string.h>

int main(int argc, char *argv[]) {
    int i;
    int total_chars = 0;

    if (argc < 2) {
        printf("Usage: %s word [word ...]\n", argv[0]);
        printf("Example: hello \"big world\" C\n");
        return 1;
    }

    printf("Total arguments: %d\n", argc - 1);
    for (i = 1; i < argc; i++) {
        int length = (int)strlen(argv[i]);
        printf("%d: %s (%d characters)\n", i, argv[i], length);
        total_chars += length;
    }
    printf("Total characters: %d\n", total_chars);
    return 0;
}
