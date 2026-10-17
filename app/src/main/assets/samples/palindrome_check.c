#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <stdbool.h>

/* Ignores case, spaces and punctuation */
bool is_palindrome(const char *s) {
    int i = 0;
    int j = (int)strlen(s) - 1;
    while (i < j) {
        while (i < j && !isalnum((unsigned char)s[i])) i++;
        while (i < j && !isalnum((unsigned char)s[j])) j--;
        if (tolower((unsigned char)s[i]) != tolower((unsigned char)s[j])) return false;
        i++;
        j--;
    }
    return true;
}

int main() {
    const char *tests[] = {
        "racecar",
        "A man, a plan, a canal: Panama",
        "Hello, World",
        "Was it a car or a cat I saw?",
        "C programming"
    };
    int n = sizeof(tests) / sizeof(tests[0]);
    int i;

    for (i = 0; i < n; i++) {
        printf("\"%s\" -> %s\n", tests[i], is_palindrome(tests[i]) ? "palindrome" : "not a palindrome");
    }
    return 0;
}
